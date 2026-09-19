package com.docsmart.features.library.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.docsmart.R
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.canonicalMediaUri
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Crash real reportado por Crashlytics 2026-09-11 (IllegalArgumentException
// "All requested items must be referenced by specific ID" en
// MediaStore.createDeleteRequest): esta constante distinguía un Uri de
// carpeta vinculada (fila 22 del backlog UX) SOLO para la autoridad SAF de
// "Almacenamiento interno" -- pero cualquier documento abierto con el picker
// genérico de Android (botón "Abrir" de Inicio, ACTION_OPEN_DOCUMENT) puede
// venir de OTRAS autoridades SAF (el proveedor de "Documentos"/media
// agregada, "Descargas", Google Drive, etc.), todas con esquema "content://"
// pero NINGUNA es un Uri real de MediaStore -- MediaStore.createDeleteRequest()
// exige URIs con un ID numérico de fila (content://media/external/.../123),
// no URIs de documento SAF (content://.../document/document%3A123), y lanza
// esa excepción para cualquiera de estas otras autoridades. La comprobación
// correcta es la inversa: usar el borrado genérico de SAF
// (DocumentsContract.deleteDocument) para CUALQUIER Uri que no sea
// explícitamente de MediaStore, en vez de intentar listar cada autoridad SAF
// que no lo es.

// Compartida entre loadDocumentsFromDownloads() (consulta a MediaStore) y
// loadDocumentsFromLinkedFolder() (enumeración SAF) -- un único lugar para
// no repetir la lista y arriesgar que se desincronicen.
private val SUPPORTED_DOWNLOAD_MIME_TYPES =
    listOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/markdown",
        // HU-53 (extraer imágenes de un PDF): "Guardar en Descargas" inserta
        // filas image/jpeg y image/png en MediaStore.Downloads (mismo mecanismo
        // que el resto de las herramientas PDF, DownloadsSaver.saveFile) -- sin
        // esto quedaban filtradas en silencio de la vista "Descargas" de
        // Biblioteca, aunque el archivo sí se guardara.
        "image/jpeg",
        "image/png",
    )

// Hallazgo real de la ronda 15: loadAllDocumentsRaw() ordenaba por
// `DocumentUiModel.date`, que es el texto ya formateado "dd/MM/yyyy" -- un
// orden lexicografico compara primero el DIA, asi que "15/01/2026" quedaba
// ANTES que "14/09/2026" (Biblioteca "Todos" y el relleno de Recientes de
// Inicio salian desordenados entre meses/anios). Cada fuente ahora devuelve el
// timestamp real junto al modelo para ordenar por el.
internal data class DatedDocument(
    val document: DocumentUiModel,
    val sortKeyMillis: Long,
)

// Logica pura, sin I/O: conserva la PRIMERA aparicion de cada id (la fuente
// mas especifica va antes: MediaStore/carpeta vinculada/app, luego historial)
// y ordena del mas reciente al mas antiguo por timestamp real. El orden es
// estable: a igual timestamp se conserva el orden de entrada.
internal fun dedupeAndSortByRecency(documents: List<DatedDocument>): List<DocumentUiModel> {
    val seen = mutableSetOf<String>()
    return documents
        .filter { seen.add(it.document.id) }
        .sortedByDescending { it.sortKeyMillis }
        .map { it.document }
}

// Tope de profundidad al recorrer subcarpetas de una carpeta vinculada por
// SAF -- solo para evitar un recorrido descontrolado en árboles anormalmente
// profundos, no un límite real esperado en uso normal (Descargas/Documentos
// del usuario rara vez pasan de 2-3 niveles).
private const val LINKED_FOLDER_MAX_DEPTH = 8

@Singleton
class DocumentRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        // ← NUEVO
        private val favoritesRepository: FavoritesRepository,
        private val documentHistoryDao: DocumentHistoryDao,
        private val trashDao: TrashDao,
        private val mediaDeletePermission: MediaDeletePermission,
        private val downloadsAccessManager: DownloadsAccessManager,
        private val documentIdentityMaintenance: com.docsmart.core.data.DocumentIdentityMaintenance,
    ) {
        // Buffer sobre el límite pedido: algunos ids del historial pueden
        // apuntar a archivos que ya no existen (borrados/movidos fuera de la
        // app) y se descartan al cruzar con loadAllDocuments().
        private companion object {
            const val HISTORY_QUERY_BUFFER = 20
        }

        /**
         * RF-VIS/HOME: "recientes" según uso real (cuándo se abrió el documento
         * en el Visor), no la fecha de modificación del archivo — antes
         * Home mostraba simplemente loadAllDocuments().take(limit), así que un
         * PDF abierto hoy pero sin modificar no aparecía como reciente.
         * Si el historial no alcanza para llenar `limit` (instalación nueva,
         * pocos documentos abiertos), se completa con los más recientes por
         * fecha de archivo, igual que el comportamiento anterior.
         */
        suspend fun loadRecentlyOpened(limit: Int): List<DocumentUiModel> =
            withContext(Dispatchers.IO) {
                val all = loadAllDocuments()
                val recentIds = documentHistoryDao.recentDocumentIds(limit + HISTORY_QUERY_BUFFER)
                mergeHistoryWithDocuments(all, recentIds, limit)
            }

        // Lógica pura, sin I/O — separada para poder testearla directo con
        // listas comunes, sin mockear MediaStore/ContentResolver/Room.
        internal fun mergeHistoryWithDocuments(
            all: List<DocumentUiModel>,
            recentIds: List<String>,
            limit: Int,
        ): List<DocumentUiModel> {
            val byId = all.associateBy { it.id }
            val fromHistory = recentIds.mapNotNull { byId[it] }

            if (fromHistory.size >= limit) return fromHistory.take(limit)

            val alreadyIncluded = fromHistory.map { it.id }.toSet()
            val fallback = all.filter { it.id !in alreadyIncluded }
            return (fromHistory + fallback).take(limit)
        }

        /**
         * Excluye documentos en la papelera (RF-VIS-07) -- `TrashRepository`
         * mantiene la tabla `trash_entries` con lo que hay que ocultar acá; la
         * purga automática de lo vencido vive allá también (se ejecuta cuando
         * se abre la pantalla de Papelera, no en cada carga de esta lista).
         */
        suspend fun loadAllDocuments(): List<DocumentUiModel> =
            withContext(Dispatchers.IO) {
                val trashedIds = trashDao.getAll().map { it.documentId }.toSet()
                loadAllDocumentsRaw().filterNot { it.id in trashedIds }
            }

        // internal (no private): TrashRepository también necesita el inventario
        // real de documentos para saber cuáles de ellos están en la papelera.
        internal suspend fun loadAllDocumentsRaw(): List<DocumentUiModel> =
            try {
                val documents = mutableListOf<DatedDocument>()
                // Fila 22 del backlog UX: si el usuario vinculó la carpeta
                // Descargas por SAF, esa fuente reemplaza la consulta a
                // MediaStore.Downloads -- ve TODO lo que hay en la carpeta real
                // sin importar quién lo creó (a diferencia de MediaStore, que en
                // Android 13+ solo expone filas propias de la app). Sin vincular,
                // se mantiene el comportamiento anterior sin cambios.
                val linkedFolder = downloadsAccessManager.linkedFolderUri.value
                if (linkedFolder != null) {
                    documents.addAll(loadDocumentsFromLinkedFolder(linkedFolder))
                } else {
                    documents.addAll(loadDocumentsFromDownloads())
                }
                documents.addAll(loadImagesFromMediaStore())
                documents.addAll(loadAppGeneratedFiles())
                // Ampliación 2026-09-03 (fila 22 backlog UX): sin esto, la
                // Biblioteca "olvidaba" un documento externo en cuanto dejaba de
                // estar entre los más recientes de Inicio -- un PDF/Word abierto
                // una sola vez vía "Abrir con DocuSmart" o el selector de
                // archivos ahora queda visible en Biblioteca de forma
                // permanente, no solo mientras esté en "Recientes". Va al final:
                // el `seen.add()` de abajo ya conserva la versión de MediaStore/
                // carpeta vinculada/app si el mismo id vino de una fuente previa.
                documents.addAll(loadDocumentsFromHistory())

                val unique = dedupeAndSortByRecency(documents)

                // ── Aplica favoritos persistidos al cargar ─────────────────
                val favoriteIds = favoritesRepository.getAllFavoriteIds()
                val withFavorites =
                    unique.map { doc ->
                        val alias = favoritesRepository.getAlias(doc.id)
                        doc.copy(
                            isFavorite = favoriteIds.contains(doc.id),
                            // ← aplica alias si existe
                            name = alias ?: doc.name,
                        )
                    }

                withFavorites
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Solo el tipo: el mensaje puede traer rutas/URIs reales y
                // CrashlyticsTree reenvia todo Timber.w/e a Firebase.
                Timber.e("Error cargando documentos: ${e.javaClass.simpleName}")
                emptyList()
            }

        /** Resultado de intentar borrar un documento real (archivo o fila de
         *  MediaStore) -- separado de un simple Boolean porque el caso de "sin
         *  permiso" no es un fallo terminal: Android entrega un [IntentSender]
         *  que, al lanzarse y confirmarse, sí realiza el borrado. */
        sealed interface DeleteOutcome {
            data object Deleted : DeleteOutcome

            data class NeedsPermission(
                val intentSender: IntentSender,
            ) : DeleteOutcome

            data object Failed : DeleteOutcome
        }

        /**
         * Elimina el documento subyacente (archivo de la app o fila de MediaStore),
         * no solo la entrada en memoria.
         *
         * Bug real (RF-VIS-07, reportado 2026-08-29): para una foto de MediaStore
         * que la app no creó (p.ej. tomada con la cámara), `contentResolver.delete()`
         * lanza `RecoverableSecurityException` en API 29 -- Android exige
         * confirmación explícita del usuario para borrar filas que no son propias
         * (scoped storage). Antes esto se trataba como fallo genérico y
         * `TrashRepository` igual quitaba la entrada de la papelera, "resucitando"
         * el archivo en Biblioteca/Recientes aunque el toast dijera que no se
         * pudo eliminar. En API 30+ se usa `MediaStore.createDeleteRequest()`
         * para todos los content:// -- un solo diálogo de sistema por operación,
         * sin depender de si la fila es propia o no.
         */
        suspend fun deleteDocument(documentId: String): DeleteOutcome =
            withContext(Dispatchers.IO) {
                // Fila 22 del backlog UX: un documento de la carpeta vinculada por
                // SAF también empieza con "content://" pero NO es un Uri de
                // MediaStore -- MediaStore.createDeleteRequest() lanza
                // IllegalArgumentException si se le pasa uno (antes esta rama nunca
                // recibía nada que no fuera de MediaStore, así que no hacía falta
                // distinguir). Se borra directo vía DocumentsContract, la API real
                // para documentos SAF.
                val uri = if (documentId.startsWith("content://")) Uri.parse(documentId) else null
                if (uri != null && uri.authority != MediaStore.AUTHORITY) {
                    return@withContext deleteSafDocument(uri, documentId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && documentId.startsWith("content://")) {
                    val intentSender = mediaDeletePermission.createBulkDeleteRequest(listOf(Uri.parse(documentId)))
                    return@withContext if (intentSender != null) {
                        DeleteOutcome.NeedsPermission(intentSender)
                    } else {
                        DeleteOutcome.Failed
                    }
                }
                deleteLegacyDocument(documentId)
            }

        // Hallazgo real de la auditoría de la capa de persistencia (Media, catch
        // de CancellationException agregado): extraída de deleteDocument() para
        // no subir su complejidad ciclomática por encima del umbral de detekt --
        // mismo criterio ya usado para deleteSafDocument() (fila 22 del backlog
        // UX). Cubre el borrado real de un archivo de la app o una fila de
        // MediaStore/content:// que no requiere el diálogo de confirmación de
        // API 30+ (esa rama ya devolvió antes de llegar acá).
        private suspend fun deleteLegacyDocument(documentId: String): DeleteOutcome =
            try {
                val deleted =
                    if (documentId.startsWith("content://")) {
                        context.contentResolver.delete(Uri.parse(documentId), null, null) > 0
                    } else {
                        val file = File(documentId)
                        file.exists() && file.delete()
                    }
                if (deleted) documentHistoryDao.remove(documentId)
                if (deleted) DeleteOutcome.Deleted else DeleteOutcome.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val recoverable =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        mediaDeletePermission.recoverableIntentSenderOrNull(e)
                    } else {
                        null
                    }
                if (recoverable != null) {
                    DeleteOutcome.NeedsPermission(recoverable)
                } else {
                    Timber.e("Error eliminando documento: ${e.javaClass.simpleName}")
                    DeleteOutcome.Failed
                }
            }

        // Fila 22 del backlog UX: borra un documento de la carpeta vinculada por
        // SAF vía DocumentsContract -- extraída de deleteDocument() para no subir
        // su complejidad ciclomática por una rama que ya está resuelta.
        private suspend fun deleteSafDocument(
            uri: Uri,
            documentId: String,
        ): DeleteOutcome =
            try {
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                if (deleted) documentHistoryDao.remove(documentId)
                if (deleted) DeleteOutcome.Deleted else DeleteOutcome.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("Error eliminando documento de la carpeta vinculada: ${e.javaClass.simpleName}")
                DeleteOutcome.Failed
            }

        /**
         * RF-VIS-06: renombra un documento -- intenta un rename real del
         * archivo si es de la app (ruta absoluta); si es un documento de
         * MediaStore o el rename real falla, guarda un alias en
         * `FavoritesRepository` (no requiere permiso de escritura sobre el
         * archivo real, mismo mecanismo que ya usaban Biblioteca/Home antes de
         * esta extracción). Devuelve el id resultante: la ruta nueva si el
         * archivo se movió de verdad, o el mismo id si solo se guardó un alias
         * -- quien llama lo necesita para saber si su propia referencia al
         * documento quedó obsoleta (el Visor, que tiene un solo documento
         * abierto, debe seguir apuntando al archivo correcto tras renombrar).
         */
        suspend fun renameDocument(
            documentId: String,
            newName: String,
        ): String =
            withContext(Dispatchers.IO) {
                try {
                    if (!documentId.startsWith("content://")) {
                        val file = File(documentId)
                        // Hallazgo real de la revisión de seguridad 2026-09-16: esta
                        // función quedó fuera del alcance del saneo de path traversal
                        // de #19/#35 -- newName llega tal cual desde el diálogo
                        // "Renombrar" (Visor/Home/Biblioteca/Escáner), y File(parent,
                        // child) resuelve ".." como ruta relativa real. Mismo saneo
                        // ya usado en PdfToolsViewModel/ConverterViewModel.
                        val safeName =
                            com.docsmart.core.util
                                .sanitizeOutputFileName(newName)
                                .ifBlank { file.name }
                        val newFile = File(file.parent, safeName)
                        if (newFile.absolutePath == file.absolutePath) {
                            // Hallazgo real de la ronda 15: renombrar al mismo
                            // nombre que ya tiene el archivo hacia renameTo()
                            // == true y luego onIdChanged(id, id) sobre el mismo
                            // id. No hay nada que mover: solo se descarta un
                            // alias viejo (el nombre real ya es el elegido).
                            favoritesRepository.removeAlias(documentId)
                            return@withContext documentId
                        }
                        // Hallazgo real de la ronda 15: File.renameTo() en
                        // Android/Linux REEMPLAZA en silencio un destino que ya
                        // existe -- renombrar "a.pdf" a "b.pdf" con un "b.pdf"
                        // distinto en la misma carpeta destruia ese otro
                        // documento sin aviso. Si el destino existe se cae al
                        // alias (mismo camino que un rename fallido).
                        if (!newFile.exists() && file.renameTo(newFile)) {
                            // Hallazgo real de la ronda 15: si la migracion de ids
                            // fallaba DESPUES de mover el archivo (Room), el catch de
                            // abajo devolvia el id VIEJO y guardaba un alias sobre una
                            // ruta que ya no existe -- el llamador quedaba apuntando a
                            // un archivo inexistente aunque el rename fisico si
                            // ocurrio. El archivo ya se movio: se devuelve SIEMPRE la
                            // ruta nueva, y un fallo de migracion solo se registra.
                            try {
                                // El alias se descarta a propósito (no se migra): tras
                                // un rename físico exitoso, el nombre de archivo YA
                                // refleja el nombre elegido, así que un alias aparte ya
                                // no hace falta -- mismo criterio que antes.
                                favoritesRepository.removeAlias(documentId)
                                // Hallazgo real de la revisión general 2026-09-16 (#50):
                                // a diferencia del alias, el estado de favorito, las
                                // anotaciones del Visor (HU-46), y luego marcadores de
                                // página/última página vista SÍ deben migrar -- sin
                                // esto, renombrar un documento con cualquiera de estos
                                // le hacía perder la marca en silencio porque el id
                                // (ruta) cambia. onIdChanged() no toca el alias de nuevo
                                // (ya se limpió arriba).
                                documentIdentityMaintenance.onIdChanged(documentId, newFile.absolutePath)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e("Error migrando el id tras renombrar: ${e.javaClass.simpleName}")
                            }
                            return@withContext newFile.absolutePath
                        }
                    }
                    favoritesRepository.saveAlias(documentId, newName)
                    documentId
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e("Error renombrando documento: ${e.javaClass.simpleName}")
                    favoritesRepository.saveAlias(documentId, newName)
                    documentId
                }
            }

        // Renombrada 2026-09-03 (fila 22 del backlog UX): el nombre anterior
        // (loadPdfsFromDownloads) era engañoso -- siempre consultó PDF, Word,
        // Excel Y PowerPoint juntos en una sola consulta a MediaStore.Downloads,
        // no solo PDF. De paso se agregó "text/plain"/"text/markdown" al
        // filtro: nunca habían estado en la lista, así que un .txt/.md real de
        // Descargas no llegaba ni siquiera a evaluarse (bug aparte, no
        // relacionado con el permiso). mimeToDocumentType() ya sabía mapear
        // texto a DocumentType.TEXT -- ese código era inalcanzable para
        // archivos reales de Descargas por esta omisión.
        private fun loadDocumentsFromDownloads(): List<DatedDocument> {
            val documents = mutableListOf<DatedDocument>()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return documents

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection =
                arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED,
                    MediaStore.Downloads.MIME_TYPE,
                )
            val mimeTypes = SUPPORTED_DOWNLOAD_MIME_TYPES
            val selection =
                mimeTypes.joinToString(" OR ") {
                    "${MediaStore.Downloads.MIME_TYPE} = ?"
                }
            try {
                context.contentResolver
                    .query(
                        collection,
                        projection,
                        selection,
                        mimeTypes.toTypedArray(),
                        "${MediaStore.Downloads.DATE_MODIFIED} DESC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)

                        while (cursor.moveToNext()) {
                            try {
                                val id = cursor.getLong(idCol)
                                val name = cursor.getString(nameCol) ?: continue
                                val size = cursor.getLong(sizeCol)
                                val dateMs = cursor.getLong(dateCol) * 1000
                                val mime = cursor.getString(mimeCol) ?: continue
                                if (name.startsWith(".")) continue

                                val uri =
                                    canonicalMediaUri(
                                        Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()),
                                    )
                                documents.add(
                                    DatedDocument(
                                        DocumentUiModel(
                                            id = uri.toString(),
                                            name = name,
                                            type = mimeToDocumentType(mime, name),
                                            size = formatSize(size),
                                            date = formatDate(dateMs),
                                            // se aplica luego en loadAllDocuments
                                            isFavorite = false,
                                            sizeBytes = size,
                                        ),
                                        dateMs,
                                    ),
                                )
                            } catch (e: Exception) {
                                Timber.w("Error leyendo fila Downloads: ${e.javaClass.simpleName}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e("Error consultando Downloads: ${e.javaClass.simpleName}")
            }
            Timber.d("Downloads: ${documents.size} documentos")
            return documents
        }

        /**
         * Fila 22 del backlog UX (`backlog-mejoras-ux-2026-08-30.md` §16-17):
         * cuando el usuario vinculó una carpeta vía SAF (`DownloadsAccessManager`),
         * se enumera la carpeta real en vez de consultar MediaStore.Downloads --
         * ve TODOS los archivos ahí, sin la restricción de "solo filas propias
         * de la app" de scoped storage en Android 13+. `DocumentFile` solo lista
         * los mismos 8 mimeTypes de Office/PDF/Texto que ya reconoce
         * `loadDocumentsFromDownloads()`.
         *
         * Recorre subcarpetas (hasta [LINKED_FOLDER_MAX_DEPTH] niveles, tope de
         * seguridad para árboles anormalmente profundos) -- bug real reportado
         * por el usuario 2026-09-03: había vinculado una carpeta real pero sus
         * documentos estaban organizados en subcarpetas, así que la Biblioteca
         * seguía mostrando 0 archivos aunque la carpeta sí tenía contenido.
         */
        private fun loadDocumentsFromLinkedFolder(treeUri: Uri): List<DatedDocument> {
            val documents = mutableListOf<DatedDocument>()
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return documents
                collectLinkedFolderDocuments(root, depth = 0, into = documents)
            } catch (e: Exception) {
                Timber.e("Error consultando la carpeta vinculada: ${e.javaClass.simpleName}")
            }
            Timber.d("Carpeta vinculada: ${documents.size} documentos")
            return documents
        }

        private fun collectLinkedFolderDocuments(
            folder: DocumentFile,
            depth: Int,
            into: MutableList<DatedDocument>,
        ) {
            if (depth > LINKED_FOLDER_MAX_DEPTH) return
            folder.listFiles().forEach { child ->
                try {
                    if (child.isDirectory) {
                        collectLinkedFolderDocuments(child, depth + 1, into)
                    } else {
                        documentFromLinkedFile(child)?.let { into.add(it) }
                    }
                } catch (e: Exception) {
                    Timber.w("Error leyendo archivo de la carpeta vinculada: ${e.javaClass.simpleName}")
                }
            }
        }

        private fun documentFromLinkedFile(file: DocumentFile): DatedDocument? {
            val (name, mime) = eligibleNameAndMime(file) ?: return null
            val modified = file.lastModified()
            return DatedDocument(
                DocumentUiModel(
                    id = file.uri.toString(),
                    name = name,
                    type = mimeToDocumentType(mime, name),
                    size = formatSize(file.length()),
                    date = formatDate(modified),
                    isFavorite = false,
                    sizeBytes = file.length(),
                ),
                modified,
            )
        }

        private fun eligibleNameAndMime(file: DocumentFile): Pair<String, String>? {
            if (!file.isFile) return null
            val name = file.name ?: return null
            val mime = file.type ?: return null
            if (name.startsWith(".") || !isSupportedDownloadMime(mime)) return null
            return name to mime
        }

        private fun isSupportedDownloadMime(mime: String): Boolean = mime in SUPPORTED_DOWNLOAD_MIME_TYPES

        private fun loadImagesFromMediaStore(): List<DatedDocument> {
            val documents = mutableListOf<DatedDocument>()
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.MIME_TYPE,
                )
            try {
                context.contentResolver
                    .query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                        var count = 0
                        while (cursor.moveToNext() && count < 50) {
                            try {
                                val id = cursor.getLong(idCol)
                                val name = cursor.getString(nameCol) ?: continue
                                val size = cursor.getLong(sizeCol)
                                val dateMs = cursor.getLong(dateCol) * 1000
                                cursor.getString(mimeCol) ?: continue
                                if (name.startsWith(".")) continue

                                val uri =
                                    canonicalMediaUri(
                                        Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()),
                                    )
                                documents.add(
                                    DatedDocument(
                                        DocumentUiModel(
                                            id = uri.toString(),
                                            name = name,
                                            type = DocumentType.IMAGE,
                                            size = formatSize(size),
                                            date = formatDate(dateMs),
                                            isFavorite = false,
                                            sizeBytes = size,
                                        ),
                                        dateMs,
                                    ),
                                )
                                count++
                            } catch (e: Exception) {
                                Timber.w("Error leyendo imagen: ${e.javaClass.simpleName}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e("Error consultando imágenes: ${e.javaClass.simpleName}")
            }
            Timber.d("Imágenes: ${documents.size}")
            return documents
        }

        private fun loadAppGeneratedFiles(): List<DatedDocument> {
            val documents = mutableListOf<DatedDocument>()
            val dirs =
                listOf(
                    File(context.filesDir, "converted"),
                    File(context.filesDir, "pdftools"),
                    // HU-46: hallazgo real de la revisión de seguridad -- las copias
                    // aplanadas de "Compartir con anotaciones" (FlattenAnnotationsPdfUseCase)
                    // quedaban invisibles para el usuario, sin forma de verlas ni borrarlas
                    // desde la app, a diferencia del resto de archivos generados.
                    File(context.filesDir, "viewer_share"),
                    // Hallazgo real de la revisión general 2026-09-16: mismo bug que
                    // viewer_share en HU-46, pero nunca extendido a las notas/resúmenes
                    // exportados desde Modo Estudio (StudyNotesExporter/StudySummaryExporter).
                    File(context.filesDir, "study_exports"),
                )
            dirs.forEach { dir ->
                if (!dir.exists()) return@forEach
                dir
                    .listFiles()
                    ?.filter { it.exists() && it.length() > 0 }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        try {
                            documents.add(
                                DatedDocument(
                                    DocumentUiModel(
                                        id = file.absolutePath,
                                        name = file.name,
                                        type = extensionToDocumentType(file.extension),
                                        size = formatSize(file.length()),
                                        date = formatDate(file.lastModified()),
                                        isFavorite = false,
                                        sizeBytes = file.length(),
                                    ),
                                    file.lastModified(),
                                ),
                            )
                        } catch (e: Exception) {
                            Timber.w("Error leyendo archivo app: ${e.javaClass.simpleName}")
                        }
                    }
            }
            Timber.d("App files: ${documents.size}")
            return documents
        }

        /**
         * Fila 22 del backlog UX (ampliación 2026-09-03): documentos que el
         * usuario abrió alguna vez (vía "Abrir con DocuSmart" o el selector de
         * archivos) pero que ninguna otra fuente ya trae -- típicamente un PDF/
         * Word/Excel que vive fuera de cualquier carpeta vinculada. Se apoya en
         * `documentHistoryDao`, que ya registra cada apertura real
         * (`ViewerViewModel.recordHistoryOpen`) para "Recientes" en Inicio; acá
         * se usa el historial COMPLETO, no solo los últimos N.
         */
        private suspend fun loadDocumentsFromHistory(): List<DatedDocument> {
            val documents = mutableListOf<DatedDocument>()
            documentHistoryDao.allEntries().forEach { entry ->
                try {
                    documentFromHistoryId(entry.documentId, entry.lastOpenedAt)
                        ?.let { documents.add(DatedDocument(it, entry.lastOpenedAt)) }
                } catch (e: Exception) {
                    Timber.w("Error leyendo documento del historial: ${e.javaClass.simpleName}")
                }
            }
            Timber.d("Historial: ${documents.size} documentos")
            return documents
        }

        private fun documentFromHistoryId(
            id: String,
            lastOpenedAt: Long,
        ): DocumentUiModel? =
            if (id.startsWith("content://")) {
                documentFromHistoryUri(Uri.parse(id), lastOpenedAt)
            } else {
                documentFromHistoryFile(File(id), lastOpenedAt)
            }

        private fun documentFromHistoryUri(
            uri: Uri,
            lastOpenedAt: Long,
        ): DocumentUiModel? {
            val cursor =
                try {
                    context.contentResolver.query(uri, null, null, null, null)
                } catch (e: Exception) {
                    Timber.w("documentFromHistoryUri: no se pudo consultar un documento: ${e.javaClass.simpleName}")
                    null
                } ?: return null
            return cursor.use {
                if (!it.moveToFirst()) return@use null
                val nameCol = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeCol = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val name = if (nameCol >= 0) it.getString(nameCol) else null
                if (name == null) return@use null
                val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                val mime =
                    try {
                        context.contentResolver.getType(uri)
                    } catch (e: Exception) {
                        Timber.w("documentFromHistoryUri: no se pudo leer el mimeType: ${e.javaClass.simpleName}")
                        null
                    } ?: ""
                DocumentUiModel(
                    id = uri.toString(),
                    name = name,
                    type = mimeToDocumentType(mime, name),
                    size = formatSize(size),
                    date = formatDate(lastOpenedAt),
                    isFavorite = false,
                    sizeBytes = size,
                )
            }
        }

        private fun documentFromHistoryFile(
            file: File,
            lastOpenedAt: Long,
        ): DocumentUiModel? {
            if (!file.exists()) return null
            return DocumentUiModel(
                id = file.absolutePath,
                name = file.name,
                type = extensionToDocumentType(file.extension),
                size = formatSize(file.length()),
                date = formatDate(lastOpenedAt),
                isFavorite = false,
                sizeBytes = file.length(),
            )
        }

        private fun mimeToDocumentType(
            mime: String,
            name: String,
        ): DocumentType =
            when {
                mime.contains("pdf") -> DocumentType.PDF
                mime.contains("word") || mime.contains("msword") -> DocumentType.WORD
                mime.contains("excel") || mime.contains("sheet") -> DocumentType.EXCEL
                mime.contains("powerpoint") || mime.contains("presentation") -> DocumentType.POWERPOINT
                mime.contains("image") -> DocumentType.IMAGE
                mime.contains("text") -> DocumentType.TEXT
                else -> extensionToDocumentType(name.substringAfterLast("."))
            }

        private fun extensionToDocumentType(ext: String): DocumentType =
            when (ext.lowercase()) {
                "pdf" -> DocumentType.PDF
                "doc", "docx" -> DocumentType.WORD
                "xls", "xlsx" -> DocumentType.EXCEL
                "ppt", "pptx" -> DocumentType.POWERPOINT
                "jpg", "jpeg", "png", "webp", "gif" -> DocumentType.IMAGE
                "txt", "md" -> DocumentType.TEXT
                "zip", "rar", "7z" -> DocumentType.ZIP
                else -> DocumentType.PDF
            }

        // Hallazgo real de la auditoría general 2026-09-17 (M2): unidades "B"/
        // "KB"/"MB" hardcodeadas sin stringResource, fuera del sistema de 12
        // idiomas -- mismo patrón ya corregido antes para
        // PdfToolsScreen/ScanSessionManager, nunca extendido acá.
        private fun formatSize(bytes: Long): String =
            when {
                bytes < 1024 -> context.getString(R.string.file_size_bytes, bytes)
                bytes < 1024 * 1024 -> context.getString(R.string.file_size_kb, bytes / 1024)
                else ->
                    context.getString(
                        R.string.file_size_mb,
                        String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0)),
                    )
            }

        private fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
    }
