package com.docsmart.features.library.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.docsmart.core.data.FavoritesRepository
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
    private val favoritesRepository: FavoritesRepository  // ← NUEVO
) {
    suspend fun loadAllDocuments(): List<DocumentUiModel> =
        withContext(Dispatchers.IO) {
            try {
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

    /**
     * Elimina el documento subyacente (archivo de la app o fila de MediaStore),
     * no solo la entrada en memoria. Devuelve false si no se pudo borrar
     * (por ejemplo, sin permiso sobre un archivo de MediaStore que la app no
     * creó) para que quien llama pueda informarlo en vez de dar por hecho
     * que se eliminó.
     */
    suspend fun deleteDocument(documentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (documentId.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(documentId), null, null) > 0
            } else {
                val file = File(documentId)
                file.exists() && file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error eliminando documento: $documentId")
            false
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
                            isFavorite = false // se aplica luego en loadAllDocuments
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
                            isFavorite = false
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
                            isFavorite = false
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
        else                 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
}
