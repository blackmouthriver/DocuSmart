package com.docsmart.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
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
        // Hallazgo real de esta ronda (auditoría 2026-09-18, Favoritos/
        // Descargas): displayName es parte de la firma pública de esta
        // función (no siempre viene de file.name, ver saveUri() abajo) --
        // sin sanear acá, un llamador futuro que pase un nombre con
        // segmentos de ruta ("../../algo") reproduciría exactamente el
        // path traversal ya corregido en sanitizeOutputFileName() (revisión
        // general 2026-09-16), ya que el branch pre-Q usa File(dir, name)
        // directo.
        val safeName = sanitizeOutputFileName(displayName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
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
                // Revisado (hallazgo SonarCloud kotlin:S5324): solo se alcanza en
                // Android 9 y anteriores (Build.VERSION_CODES.Q ya usa MediaStore,
                // con almacenamiento por ámbitos, arriba) -- es el reemplazo
                // retrocompatible oficial recomendado por Android para ese rango
                // de API, y el destino es la carpeta pública de Descargas por
                // pedido explícito del usuario ("Guardar en Descargas"), no una
                // ruta interna sensible.
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) // NOSONAR
                // Hallazgo real de esta ronda: a diferencia del branch MediaStore
                // (que renombra solo en colisión de forma nativa), este camino
                // legacy usaba overwrite=true -- si dos documentos distintos
                // comparten nombre (mismo escenario ya corregido en
                // SecurityManager.uniqueDestination(), hallazgo #61), el segundo
                // "Guardar en Descargas" pisaba en silencio el contenido del
                // primero en la carpeta pública, sin avisar al usuario.
                file.copyTo(uniqueDestination(dir, safeName), overwrite = false)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hallazgo real de esta ronda: se pasaba `e` (la excepción original)
            // directo a Timber.e -- CrashlyticsTree reenvía TODO log ≥WARN con
            // Throwable a FirebaseCrashlytics.recordException(), y una
            // IOException/FileNotFoundException de esta operación trae la ruta
            // absoluta real del archivo/carpeta de Descargas en su .message.
            // Mismo criterio ya usado en SecurityManager/PdfPasswordUseCase/
            // DownloadsAccessManager: se loguea solo el tipo de excepción.
            Timber.e(redactedForLog(e), "DownloadsSaver: error guardando archivo en Descargas")
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
        val safeName = sanitizeOutputFileName(displayName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
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
                // Revisado (hallazgo SonarCloud kotlin:S5324): mismo caso que en
                // saveFile() arriba -- solo Android 9 y anteriores, reemplazo
                // retrocompatible oficial de MediaStore, destino público pedido
                // explícitamente por el usuario.
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) // NOSONAR
                val destFile = uniqueDestination(dir, safeName)
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ver el comentario equivalente en saveFile(): una IOException acá
            // puede traer la ruta real del destino o la Uri de origen (content://
            // con el id del proveedor) en su .message.
            Timber.e(redactedForLog(e), "DownloadsSaver: error guardando URI en Descargas")
            false
        }
    }

    // Mismo criterio ya usado en SecurityManager/PdfPasswordUseCase/
    // DownloadsAccessManager para no reenviar a Crashlytics una ruta real de
    // archivo a través del .message de la excepción original.
    private fun redactedForLog(e: Exception) = RuntimeException("DownloadsSaver: ${e.javaClass.simpleName}")

    // Ver el comentario de uniqueDestination() en SecurityManager.kt (hallazgo
    // #61) -- mismo criterio: si el nombre ya existe en destino, agrega un
    // sufijo numérico antes de la extensión hasta encontrar uno libre, en vez
    // de sobrescribir en silencio.
    private fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext  = if (dotIndex > 0) name.substring(dotIndex) else ""
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($suffix)$ext")
            suffix++
        }
        return candidate
    }
}
