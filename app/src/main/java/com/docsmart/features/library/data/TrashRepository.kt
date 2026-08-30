package com.docsmart.features.library.data

import android.content.IntentSender
import android.os.Build
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import com.docsmart.core.ui.components.DocumentUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** RF-VIS-07: documento en la papelera junto a cuándo se eliminó -- separado
 *  de `DocumentUiModel` para no agregarle un campo que sería `null` en el
 *  99% de los usos (Biblioteca/Home normales). */
data class TrashedDocumentUiModel(
    val document : DocumentUiModel,
    val deletedAt: Long
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
class TrashRepository @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val trashDao: TrashDao,
    private val documentHistoryDao: DocumentHistoryDao,
    private val favoritesRepository: FavoritesRepository,
    private val mediaDeletePermission: MediaDeletePermission
) {
    companion object {
        const val TRASH_RETENTION_DAYS = 30
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * DAY_MILLIS

        // Función pura, sin I/O -- separada para poder testearla directo con
        // timestamps, sin mockear Room.
        internal fun isTrashEntryExpired(deletedAt: Long, now: Long): Boolean =
            now - deletedAt >= TRASH_RETENTION_MILLIS
    }

    /**
     * Documentos en la papelera, ordenados del más reciente al más antiguo.
     * El archivo/fila real no se toca al eliminar (ver `moveToTrash`) --
     * por eso `loadAllDocumentsRaw()` (que lee el almacenamiento real)
     * todavía los encuentra; acá se cruza con `trash_entries` para
     * quedarse solo con esos y adjuntar `deletedAt`.
     */
    suspend fun loadTrashedDocuments(): List<TrashedDocumentUiModel> = withContext(Dispatchers.IO) {
        purgeExpiredTrash()
        val trashById = trashDao.getAll().associateBy { it.documentId }
        documentRepository.loadAllDocumentsRaw()
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
    suspend fun moveToTrash(documentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            trashDao.insert(TrashEntry(documentId, System.currentTimeMillis()))
            documentHistoryDao.remove(documentId)
            true
        } catch (e: Exception) {
            Timber.e(e, "Error moviendo a la papelera: $documentId")
            false
        }
    }

    /** Saca un documento de la papelera sin tocar el archivo real. */
    suspend fun restoreFromTrash(documentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            trashDao.remove(documentId)
            true
        } catch (e: Exception) {
            Timber.e(e, "Error restaurando de la papelera: $documentId")
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
    suspend fun deleteForever(documentId: String): DocumentRepository.DeleteOutcome = withContext(Dispatchers.IO) {
        val outcome = documentRepository.deleteDocument(documentId)
        if (outcome is DocumentRepository.DeleteOutcome.Deleted) {
            trashDao.remove(documentId)
            favoritesRepository.removeAlias(documentId)
        }
        outcome
    }

    /** Limpia las tablas propias tras confirmar un borrado que requirió el
     *  diálogo de sistema (Android ya borró la fila en ese punto). */
    suspend fun finalizeDeleteForever(documentId: String) = withContext(Dispatchers.IO) {
        trashDao.remove(documentId)
        favoritesRepository.removeAlias(documentId)
    }

    suspend fun finalizeDeleteForever(documentIds: List<String>) = withContext(Dispatchers.IO) {
        documentIds.forEach {
            trashDao.remove(it)
            favoritesRepository.removeAlias(it)
        }
    }

    sealed interface BulkDeleteOutcome {
        data object Done : BulkDeleteOutcome
        data class NeedsPermission(val intentSender: IntentSender, val documentIds: List<String>) : BulkDeleteOutcome
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
    suspend fun deleteAllForever(documentIds: List<String>): BulkDeleteOutcome = withContext(Dispatchers.IO) {
        val plainFiles = documentIds.filterNot { it.startsWith("content://") }
        val mediaFiles = documentIds.filter { it.startsWith("content://") }

        plainFiles.forEach { id ->
            if (documentRepository.deleteDocument(id) is DocumentRepository.DeleteOutcome.Deleted) {
                trashDao.remove(id)
                favoritesRepository.removeAlias(id)
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
                    favoritesRepository.removeAlias(id)
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
    internal suspend fun purgeExpiredTrash(now: Long = System.currentTimeMillis()) {
        trashDao.getAll()
            .filter { isTrashEntryExpired(it.deletedAt, now) }
            .forEach { entry ->
                // Solo se quita la entrada si el borrado real se confirmó --
                // si Android pidió permiso (NeedsPermission) no hay Activity
                // disponible acá para mostrar el diálogo, así que el archivo
                // se queda en la papelera (vencido, pero visible) hasta que
                // el usuario lo borre manualmente desde la UI.
                if (documentRepository.deleteDocument(entry.documentId)
                    is DocumentRepository.DeleteOutcome.Deleted
                ) {
                    trashDao.remove(entry.documentId)
                }
            }
    }
}
