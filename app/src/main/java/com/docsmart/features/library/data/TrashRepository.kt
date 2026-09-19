package com.docsmart.features.library.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import com.docsmart.core.data.DocumentIdentityMaintenance
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.util.elapsedRealtimeMillisSafe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** RF-VIS-07: documento en la papelera junto a cuándo se eliminó -- separado
 *  de `DocumentUiModel` para no agregarle un campo que sería `null` en el
 *  99% de los usos (Biblioteca/Home normales). */
data class TrashedDocumentUiModel(
    val document: DocumentUiModel,
    val deletedAt: Long,
)

/**
 * RF-VIS-07: papelera de reciclaje -- extraída de `DocumentRepository` (que
 * superó el umbral de `TooManyFunctions` de detekt al agregar estos 5
 * métodos) a su propia clase, siguiendo el mismo criterio ya usado para
 * `FavoritesRepository` (una responsabilidad propia, aunque relacionada).
 * "Eliminar" un documento desde Biblioteca/Home/Visor solo registra su id
 * acá con la fecha -- el archivo/fila real (app o MediaStore) permanece
 * intacto hasta que se restaura, se elimina definitivamente, o vence
 * `TRASH_RETENTION_DAYS` (purga automática, ver `purgeExpiredTrash`).
 */
@Singleton
class TrashRepository
    @Inject
    constructor(
        private val documentRepository: DocumentRepository,
        private val trashDao: TrashDao,
        private val documentHistoryDao: DocumentHistoryDao,
        private val mediaDeletePermission: MediaDeletePermission,
        private val documentIdentityMaintenance: DocumentIdentityMaintenance,
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val TRASH_RETENTION_DAYS = 30
            private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
            private const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * DAY_MILLIS
            private const val PURGE_CANDIDATES_PREFS = "docusmart_trash_purge_candidates"

            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Alta -- P1): a diferencia del PIN y el límite diario (ya
            // blindados con elapsedRealtime), la purga automática confiaba
            // solo en el reloj de pared, SIN ningún diálogo de confirmación --
            // adelantar la fecha del sistema 31+ días y abrir Papelera borraba
            // todo de forma irreversible en un solo paso. Mismo criterio ya
            // usado para DailyLimitManager: exigir al menos
            // MIN_REAL_MS_BEFORE_PURGE de tiempo real (elapsedRealtime, inmune
            // al reloj de pared) entre la PRIMERA vez que una entrada se ve
            // vencida y el borrado real -- un salto instantáneo del reloj ya
            // no alcanza por sí solo, hace falta además que pase tiempo real.
            internal const val MIN_REAL_MS_BEFORE_PURGE = 20L * 60 * 60 * 1000

            // Función pura, sin I/O -- separada para poder testearla directo con
            // timestamps, sin mockear Room.
            internal fun isTrashEntryExpired(
                deletedAt: Long,
                now: Long,
            ): Boolean = now - deletedAt >= TRASH_RETENTION_MILLIS
        }

        private val purgeCandidatePrefs by lazy {
            context.getSharedPreferences(PURGE_CANDIDATES_PREFS, Context.MODE_PRIVATE)
        }

        /**
         * Documentos en la papelera, ordenados del más reciente al más antiguo.
         * El archivo/fila real no se toca al eliminar (ver `moveToTrash`) --
         * por eso `loadAllDocumentsRaw()` (que lee el almacenamiento real)
         * todavía los encuentra; acá se cruza con `trash_entries` para
         * quedarse solo con esos y adjuntar `deletedAt`.
         */
        suspend fun loadTrashedDocuments(): List<TrashedDocumentUiModel> =
            withContext(Dispatchers.IO) {
                purgeExpiredTrash()
                val trashById = trashDao.getAll().associateBy { it.documentId }
                documentRepository
                    .loadAllDocumentsRaw()
                    .filter { it.id in trashById }
                    .map { doc -> TrashedDocumentUiModel(doc, trashById.getValue(doc.id).deletedAt) }
                    .sortedByDescending { it.deletedAt }
            }

        /**
         * Mueve un documento a la papelera -- NO borra el archivo ni la fila de
         * MediaStore, solo registra la fecha de eliminación en `trash_entries`.
         * `DocumentRepository.loadAllDocuments()` lo excluye a partir de acá, y
         * queda disponible para restaurar o para el borrado definitivo
         * automático tras `TRASH_RETENTION_DAYS`.
         */
        suspend fun moveToTrash(documentId: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    trashDao.insert(TrashEntry(documentId, System.currentTimeMillis()))
                    documentHistoryDao.remove(documentId)
                    // Arranca un ciclo de vencimiento nuevo y limpio (ver P1 en
                    // purgeExpiredTrash()) -- si este mismo id ya había pasado por
                    // la papelera antes, no debe heredar un firstSeenElapsed viejo.
                    clearPurgeCandidate(documentId)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Revisión adversarial de seguridad (ronda 13): mismo criterio
                    // de redacción ya aplicado a la línea de abajo y en
                    // DownloadsAccessManager -- documentId es la ruta/URI real,
                    // CrashlyticsTree la reenviaría a Crashlytics.
                    Timber.e(e, "Error moviendo a la papelera")
                    false
                }
            }

        /**
         * Saca un documento de la papelera sin tocar el archivo real.
         *
         * Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda,
         * Media -- P3): antes solo borraba la fila de `trash_entries` sin
         * verificar que el archivo siguiera existiendo -- si se había
         * borrado por fuera de la app (otro gestor de archivos, carpeta SAF
         * desvinculada), el documento simplemente desaparecía sin ningún
         * aviso (ya no estaba en Papelera ni en Biblioteca). Ahora se
         * confirma que el archivo sigue ahí antes de "restaurarlo" -- si no,
         * se devuelve `false` para que el llamador pueda avisar, igual que ya
         * hace `deleteForever` con sus propios fallos.
         */
        suspend fun restoreFromTrash(documentId: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    if (!documentStillExists(documentId)) {
                        // Hallazgo real de la revisión adversarial de esta misma
                        // ronda (Baja-Media): `documentId` es la ruta/URI real del
                        // archivo -- CrashlyticsTree reenvía todo Timber.w/e como
                        // breadcrumb, así que se omite del mensaje (mismo criterio
                        // ya aplicado a DownloadsAccessManager en la octava ronda).
                        Timber.w("TrashRepository: no se pudo restaurar un documento -- el archivo ya no existe")
                        trashDao.remove(documentId)
                        clearPurgeCandidate(documentId)
                        return@withContext false
                    }
                    trashDao.remove(documentId)
                    clearPurgeCandidate(documentId)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Revisión adversarial de seguridad (ronda 13): mismo criterio
                    // de redacción que la línea 138 de esta misma función.
                    Timber.e(e, "Error restaurando de la papelera")
                    false
                }
            }

        /**
         * Borrado definitivo manual desde la papelera -- reutiliza
         * `DocumentRepository.deleteDocument()` (mismo mecanismo real ya usado
         * antes de RF-VIS-07).
         *
         * Bug real corregido (2026-08-30): antes se llamaba a
         * `trashDao.remove(documentId)` sin importar el resultado del borrado
         * real. Para fotos de MediaStore que la app no creó, `deleteDocument()`
         * fallaba por falta de permiso (ver `DocumentRepository.DeleteOutcome`) y
         * aun así se quitaba la entrada de la papelera -- el archivo "resucitaba"
         * en Biblioteca/Recientes aunque el toast dijera que no se pudo eliminar.
         * Ahora la entrada de la papelera solo se limpia si el borrado fue
         * confirmado (`Deleted`); si Android pide permiso (`NeedsPermission`), el
         * llamador debe lanzar el `IntentSender` y, si el usuario confirma,
         * llamar a [finalizeDeleteForever].
         */
        suspend fun deleteForever(documentId: String): DocumentRepository.DeleteOutcome =
            withContext(Dispatchers.IO) {
                val outcome = documentRepository.deleteDocument(documentId)
                if (outcome is DocumentRepository.DeleteOutcome.Deleted) {
                    trashDao.remove(documentId)
                    // Hallazgo real de la revisión adversarial de esta misma
                    // ronda (Media): ninguno de los 3 caminos de borrado
                    // DEFINITIVO limpiaba el candidato de purga (P1, más abajo)
                    // -- `documentId` (ruta/URI real, potencialmente sensible)
                    // quedaba huérfano en SharedPreferences para siempre después
                    // de un borrado que se supone completo.
                    clearPurgeCandidate(documentId)
                    documentIdentityMaintenance.onPermanentlyDeleted(documentId)
                }
                outcome
            }

        /** Limpia las tablas propias tras confirmar un borrado que requirió el
         *  diálogo de sistema (Android ya borró la fila en ese punto). */
        suspend fun finalizeDeleteForever(documentId: String) =
            withContext(Dispatchers.IO) {
                trashDao.remove(documentId)
                clearPurgeCandidate(documentId)
                documentIdentityMaintenance.onPermanentlyDeleted(documentId)
            }

        suspend fun finalizeDeleteForever(documentIds: List<String>) =
            withContext(Dispatchers.IO) {
                documentIds.forEach {
                    trashDao.remove(it)
                    clearPurgeCandidate(it)
                    documentIdentityMaintenance.onPermanentlyDeleted(it)
                }
            }

        sealed interface BulkDeleteOutcome {
            data object Done : BulkDeleteOutcome

            data class NeedsPermission(
                val intentSender: IntentSender,
                val documentIds: List<String>,
            ) : BulkDeleteOutcome

            data object PartialNeedsPermission : BulkDeleteOutcome
        }

        /**
         * "Borrar todo" -- los archivos propios de la app (rutas de archivo) se
         * borran directo; las fotos de MediaStore (content://) se agrupan en un
         * único `MediaStore.createDeleteRequest()` (API 30+, un solo diálogo de
         * sistema para todas). En API < 30 no existe el borrado en lote: se
         * reintenta una por una y las que pidan permiso individual quedan en la
         * papelera (se informa con [PartialNeedsPermission] en vez de encadenar
         * varios diálogos de sistema seguidos).
         */
        suspend fun deleteAllForever(documentIds: List<String>): BulkDeleteOutcome =
            withContext(Dispatchers.IO) {
                val plainFiles = documentIds.filterNot { it.startsWith("content://") }
                val mediaFiles = documentIds.filter { it.startsWith("content://") }

                plainFiles.forEach { id ->
                    if (documentRepository.deleteDocument(id) is DocumentRepository.DeleteOutcome.Deleted) {
                        trashDao.remove(id)
                        clearPurgeCandidate(id)
                        documentIdentityMaintenance.onPermanentlyDeleted(id)
                    }
                }

                if (mediaFiles.isEmpty()) return@withContext BulkDeleteOutcome.Done

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val uris = mediaFiles.map { android.net.Uri.parse(it) }
                    val intentSender = mediaDeletePermission.createBulkDeleteRequest(uris)
                    if (intentSender != null) return@withContext BulkDeleteOutcome.NeedsPermission(intentSender, mediaFiles)
                }

                var pendingPermission = false
                mediaFiles.forEach { id ->
                    when (documentRepository.deleteDocument(id)) {
                        is DocumentRepository.DeleteOutcome.Deleted -> {
                            trashDao.remove(id)
                            clearPurgeCandidate(id)
                            documentIdentityMaintenance.onPermanentlyDeleted(id)
                        }
                        is DocumentRepository.DeleteOutcome.NeedsPermission -> pendingPermission = true
                        DocumentRepository.DeleteOutcome.Failed -> Unit
                    }
                }
                if (pendingPermission) BulkDeleteOutcome.PartialNeedsPermission else BulkDeleteOutcome.Done
            }

        /**
         * Purga automática -- se ejecuta al abrir la Papelera
         * (`loadTrashedDocuments()`) en vez de depender de WorkManager/un job
         * en segundo plano: el proyecto no tenía ningún mecanismo de tarea
         * programada, y una purga "perezosa" al leer es suficiente para este
         * caso (no hay una garantía de "debe borrarse exactamente al día 30
         * aunque la app esté cerrada" en los requisitos).
         */
        internal suspend fun purgeExpiredTrash(
            now: Long = System.currentTimeMillis(),
            nowElapsed: Long = elapsedRealtimeMillisSafe(),
        ) {
            trashDao
                .getAll()
                .filter { isTrashEntryExpired(it.deletedAt, now) }
                .forEach { entry ->
                    val key = "candidate_${entry.documentId}"
                    val firstSeenElapsed = purgeCandidatePrefs.getLong(key, 0L)
                    // Ver el comentario de MIN_REAL_MS_BEFORE_PURGE arriba (P1).
                    // Primera vez que esta entrada se ve vencida: se anota el
                    // momento (reloj real, inmune al de pared) pero TODAVÍA no
                    // se borra -- recién se confirma en una purga posterior,
                    // una vez que pasó tiempo real de verdad desde esa primera
                    // detección.
                    if (firstSeenElapsed == 0L) {
                        purgeCandidatePrefs.edit().putLong(key, nowElapsed).apply()
                        return@forEach
                    }
                    val realElapsedSinceFirstSeen = nowElapsed - firstSeenElapsed
                    if (realElapsedSinceFirstSeen < MIN_REAL_MS_BEFORE_PURGE) {
                        // Se omite entry.documentId (ruta/URI real) del mensaje
                        // -- ver la nota de arriba en restoreFromTrash().
                        Timber.w(
                            "TrashRepository: un documento figura vencido pero no pasó " +
                                "suficiente tiempo real desde que se detectó -- se ignora (posible " +
                                "manipulación del reloj)",
                        )
                        return@forEach
                    }
                    // Solo se quita la entrada si el borrado real se confirmó --
                    // si Android pidió permiso (NeedsPermission) no hay Activity
                    // disponible acá para mostrar el diálogo, así que el archivo
                    // se queda en la papelera (vencido, pero visible) hasta que
                    // el usuario lo borre manualmente desde la UI.
                    if (documentRepository.deleteDocument(entry.documentId)
                            is DocumentRepository.DeleteOutcome.Deleted
                    ) {
                        trashDao.remove(entry.documentId)
                        purgeCandidatePrefs.edit().remove(key).apply()
                        // Hallazgo real #50: esta rama (purga automática a los
                        // 30 días) no limpiaba ni el alias ni el favorito --
                        // a diferencia de las otras 4 vías de borrado
                        // definitivo de este archivo, que ya limpiaban el
                        // alias (aunque tampoco el favorito, hasta este mismo
                        // hallazgo).
                        documentIdentityMaintenance.onPermanentlyDeleted(entry.documentId)
                    }
                }
        }

        private fun clearPurgeCandidate(documentId: String) {
            purgeCandidatePrefs.edit().remove("candidate_$documentId").apply()
        }

        // Mismo criterio de distinción file:// vs content:// que el resto del
        // repositorio (ver DocumentRepository.deleteDocument()).
        private fun documentStillExists(documentId: String): Boolean {
            if (documentId.startsWith("content://")) {
                return try {
                    context.contentResolver
                        .query(Uri.parse(documentId), null, null, null, null)
                        ?.use { it.moveToFirst() } ?: false
                } catch (e: Exception) {
                    Timber.w(e, "TrashRepository: no se pudo confirmar si un documento sigue existiendo")
                    false
                }
            }
            return File(documentId).exists()
        }
    }
