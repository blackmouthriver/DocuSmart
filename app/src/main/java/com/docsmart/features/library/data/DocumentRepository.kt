package com.docsmart.features.library.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesRepository: FavoritesRepository,  // ← NUEVO
    private val documentHistoryDao: DocumentHistoryDao,
    private val trashDao: TrashDao,
    private val mediaDeletePermission: MediaDeletePermission
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
    suspend fun loadRecentlyOpened(limit: Int): List<DocumentUiModel> = withContext(Dispatchers.IO) {
        val all = loadAllDocuments()
        val recentIds = documentHistoryDao.recentDocumentIds(limit + HISTORY_QUERY_BUFFER)
        mergeHistoryWithDocuments(all, recentIds, limit)
    }

    // Lógica pura, sin I/O — separada para poder testearla directo con
    // listas comunes, sin mockear MediaStore/ContentResolver/Room.
    internal fun mergeHistoryWithDocuments(
        all: List<DocumentUiModel>,
        recentIds: List<String>,
        limit: Int
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
    suspend fun loadAllDocuments(): List<DocumentUiModel> = withContext(Dispatchers.IO) {
        val trashedIds = trashDao.getAll().map { it.documentId }.toSet()
        loadAllDocumentsRaw().filterNot { it.id in trashedIds }
    }

    // internal (no private): TrashRepository también necesita el inventario
    // real de documentos para saber cuáles de ellos están en la papelera.
    internal suspend fun loadAllDocumentsRaw(): List<DocumentUiModel> {
        return try {
            val documents = mutableListOf<DocumentUiModel>()
            documents.addAll(loadPdfsFromDownloads())
            documents.addAll(loadImagesFromMediaStore())
            documents.addAll(loadAppGeneratedFiles())

            val seen = mutableSetOf<String>()
            val unique = documents.filter { seen.add(it.id) }

            // ── Aplica favoritos persistidos al cargar ─────────────────
            val favoriteIds = favoritesRepository.getAllFavoriteIds()
            val withFavorites = unique.map { doc ->
                val alias = favoritesRepository.getAlias(doc.id)
                doc.copy(
                    isFavorite = favoriteIds.contains(doc.id),
                    name       = alias ?: doc.name   // ← aplica alias si existe
                )
            }

            withFavorites.sortedByDescending { it.date }
        } catch (e: Exception) {
            Timber.e(e, "Error cargando documentos")
            emptyList()
        }
    }

    /** Resultado de intentar borrar un documento real (archivo o fila de
     *  MediaStore) -- separado de un simple Boolean porque el caso de "sin
     *  permiso" no es un fallo terminal: Android entrega un [IntentSender]
     *  que, al lanzarse y confirmarse, sí realiza el borrado. */
    sealed interface DeleteOutcome {
        data object Deleted : DeleteOutcome
        data class NeedsPermission(val intentSender: IntentSender) : DeleteOutcome
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
    suspend fun deleteDocument(documentId: String): DeleteOutcome = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && documentId.startsWith("content://")) {
            val intentSender = mediaDeletePermission.createBulkDeleteRequest(listOf(Uri.parse(documentId)))
            return@withContext if (intentSender != null) {
                DeleteOutcome.NeedsPermission(intentSender)
            } else {
                DeleteOutcome.Failed
            }
        }
        try {
            val deleted = if (documentId.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(documentId), null, null) > 0
            } else {
                val file = File(documentId)
                file.exists() && file.delete()
            }
            if (deleted) documentHistoryDao.remove(documentId)
            if (deleted) DeleteOutcome.Deleted else DeleteOutcome.Failed
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaDeletePermission.recoverableIntentSenderOrNull(e)?.let {
                    return@withContext DeleteOutcome.NeedsPermission(it)
                }
            }
            Timber.e(e, "Error eliminando documento: $documentId")
            DeleteOutcome.Failed
        }
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
    suspend fun renameDocument(documentId: String, newName: String): String = withContext(Dispatchers.IO) {
        try {
            if (!documentId.startsWith("content://")) {
                val file = File(documentId)
                val newFile = File(file.parent, newName)
                if (file.renameTo(newFile)) {
                    favoritesRepository.removeAlias(documentId)
                    return@withContext newFile.absolutePath
                }
            }
            favoritesRepository.saveAlias(documentId, newName)
            documentId
        } catch (e: Exception) {
            Timber.e(e, "Error renombrando documento: $documentId")
            favoritesRepository.saveAlias(documentId, newName)
            documentId
        }
    }

    private fun loadPdfsFromDownloads(): List<DocumentUiModel> {
        val documents = mutableListOf<DocumentUiModel>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return documents

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.MIME_TYPE
        )
        val mimeTypes = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        val selection = mimeTypes.joinToString(" OR ") {
            "${MediaStore.Downloads.MIME_TYPE} = ?"
        }
        try {
            context.contentResolver.query(
                collection, projection, selection,
                mimeTypes.toTypedArray(),
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)

                while (cursor.moveToNext()) {
                    try {
                        val id     = cursor.getLong(idCol)
                        val name   = cursor.getString(nameCol) ?: continue
                        val size   = cursor.getLong(sizeCol)
                        val dateMs = cursor.getLong(dateCol) * 1000
                        val mime   = cursor.getString(mimeCol) ?: continue
                        if (name.startsWith(".")) continue

                        val uri = Uri.withAppendedPath(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()
                        )
                        documents.add(DocumentUiModel(
                            id         = uri.toString(),
                            name       = name,
                            type       = mimeToDocumentType(mime, name),
                            size       = formatSize(size),
                            date       = formatDate(dateMs),
                            isFavorite = false, // se aplica luego en loadAllDocuments
                            sizeBytes  = size
                        ))
                    } catch (e: Exception) {
                        Timber.w("Error leyendo fila Downloads: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error consultando Downloads")
        }
        Timber.d("Downloads: ${documents.size} documentos")
        return documents
    }

    private fun loadImagesFromMediaStore(): List<DocumentUiModel> {
        val documents = mutableListOf<DocumentUiModel>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    try {
                        val id     = cursor.getLong(idCol)
                        val name   = cursor.getString(nameCol) ?: continue
                        val size   = cursor.getLong(sizeCol)
                        val dateMs = cursor.getLong(dateCol) * 1000
                        cursor.getString(mimeCol) ?: continue
                        if (name.startsWith(".")) continue

                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )
                        documents.add(DocumentUiModel(
                            id         = uri.toString(),
                            name       = name,
                            type       = DocumentType.IMAGE,
                            size       = formatSize(size),
                            date       = formatDate(dateMs),
                            isFavorite = false,
                            sizeBytes  = size
                        ))
                        count++
                    } catch (e: Exception) {
                        Timber.w("Error leyendo imagen: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error consultando imágenes")
        }
        Timber.d("Imágenes: ${documents.size}")
        return documents
    }

    private fun loadAppGeneratedFiles(): List<DocumentUiModel> {
        val documents = mutableListOf<DocumentUiModel>()
        val dirs = listOf(
            File(context.filesDir, "converted"),
            File(context.filesDir, "pdftools")
        )
        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()
                ?.filter { it.exists() && it.length() > 0 }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    try {
                        documents.add(DocumentUiModel(
                            id         = file.absolutePath,
                            name       = file.name,
                            type       = extensionToDocumentType(file.extension),
                            size       = formatSize(file.length()),
                            date       = formatDate(file.lastModified()),
                            isFavorite = false,
                            sizeBytes  = file.length()
                        ))
                    } catch (e: Exception) {
                        Timber.w("Error leyendo archivo app: ${e.message}")
                    }
                }
        }
        Timber.d("App files: ${documents.size}")
        return documents
    }

    private fun mimeToDocumentType(mime: String, name: String): DocumentType = when {
        mime.contains("pdf")                                          -> DocumentType.PDF
        mime.contains("word") || mime.contains("msword")             -> DocumentType.WORD
        mime.contains("excel") || mime.contains("sheet")             -> DocumentType.EXCEL
        mime.contains("powerpoint") || mime.contains("presentation") -> DocumentType.POWERPOINT
        mime.contains("image")                                        -> DocumentType.IMAGE
        mime.contains("text")                                         -> DocumentType.TEXT
        else -> extensionToDocumentType(name.substringAfterLast("."))
    }

    private fun extensionToDocumentType(ext: String): DocumentType = when (ext.lowercase()) {
        "pdf"                                -> DocumentType.PDF
        "doc", "docx"                        -> DocumentType.WORD
        "xls", "xlsx"                        -> DocumentType.EXCEL
        "ppt", "pptx"                        -> DocumentType.POWERPOINT
        "jpg", "jpeg", "png", "webp", "gif" -> DocumentType.IMAGE
        "txt", "md"                          -> DocumentType.TEXT
        "zip", "rar", "7z"                   -> DocumentType.ZIP
        else                                 -> DocumentType.PDF
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024         -> "$bytes B"
        bytes < 1024 * 1024  -> "${bytes / 1024} KB"
        else                 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
}
