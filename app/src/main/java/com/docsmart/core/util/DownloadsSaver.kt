package com.docsmart.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

// Código duplicado corregido 2026-09-08: esta misma lógica (MediaStore en
// Android 10+, copia directa al directorio público antes) estaba repetida
// de forma casi idéntica en 7 archivos distintos (ConverterViewModel,
// PdfToolsViewModel, QrScreen, ScanResultScreen x2, SecurityViewModel,
// StudyScreen x2). Se unifica aquí para que cualquier corrección futura
// (ej. un cambio de API) se haga en un solo lugar.
object DownloadsSaver {

    fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "pdf"         -> "application/pdf"
        "txt"         -> "text/plain"
        "csv"         -> "text/csv"
        "jpg", "jpeg" -> "image/jpeg"
        "png"         -> "image/png"
        else          -> "application/octet-stream"
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun saveFile(
        context: Context,
        file: File,
        mimeType: String,
        displayName: String = file.name
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                file.copyTo(File(dir, displayName), overwrite = true)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "DownloadsSaver: error guardando archivo en Descargas")
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun saveUri(
        context: Context,
        sourceUri: Uri,
        mimeType: String,
        displayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
                } ?: return@withContext false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(destUri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(dir, displayName)
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "DownloadsSaver: error guardando URI en Descargas")
            false
        }
    }
}
