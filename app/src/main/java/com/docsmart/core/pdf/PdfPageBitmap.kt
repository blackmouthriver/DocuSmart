package com.docsmart.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import timber.log.Timber
import java.io.File

/**
 * Extraído de `ViewerScreen.kt` (2026-09-08) al necesitarse una segunda vez,
 * en Modo Estudio (RF-STU: mostrar el PDF mientras la voz lee, en vez de los
 * párrafos extraídos) -- antes vivía privado en el Visor, sin motivo para
 * duplicar la lógica de render a bitmap en dos features.
 *
 * Página renderizada + su tamaño real en puntos PDF (necesario para
 * convertir coordenadas del PDF a píxeles de pantalla, ej. resaltado de
 * búsqueda en el Visor).
 */
data class PdfPageBitmap(val bitmap: Bitmap, val pageWidthPts: Float, val pageHeightPts: Float)

internal fun copyPdfUriToCache(
    uri: Uri,
    context: Context,
    cacheFile: File,
): Boolean =
    if (uri.scheme == "file") {
        copyFileSchemeToCache(uri, cacheFile)
    } else {
        copyContentUriToCache(uri, context, cacheFile)
    }

private fun copyFileSchemeToCache(
    uri: Uri,
    cacheFile: File,
): Boolean {
    val srcFile = uri.path?.let(::File)
    val valid = srcFile != null && srcFile.exists() && srcFile.length() > 0
    if (valid) {
        java.io.FileInputStream(srcFile).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return valid
}

// Hallazgo real de la auditoría general 2026-09-18 (ronda 14): este catch
// interpolaba `e.message` en el texto del log -- `Timber.e` sube ese texto
// tal cual como breadcrumb a Crashlytics (`CrashlyticsTree.log()`, sin
// filtrar por si trae o no un Throwable), y `openInputStream()` sobre un
// `content://` suele fallar con una excepción cuyo mensaje incluye la Uri
// real del documento del usuario (SecurityException/FileNotFoundException
// de content resolvers). Mismo criterio de redacción que
// DownloadsAccessManager/SecurityManager/PermissionHandler: solo el tipo de
// excepción, nunca su mensaje original.
@Suppress("TooGenericExceptionCaught")
private fun copyContentUriToCache(
    uri: Uri,
    context: Context,
    cacheFile: File,
): Boolean =
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
    } catch (e: Exception) {
        Timber.e("PdfPageRenderer: error abriendo el content:// de origen (${e.javaClass.simpleName})")
        false
    }

// Crash real en producción (Crashlytics, v1.1.0, 2026-10-05): la escala fija 2x
// sobre una página de gran formato (planos/pósters) daba un bitmap de ~156 MB y
// Android aborta el dibujado de cualquier bitmap > 100 MB ("Canvas: trying to
// draw too large bitmap") -- cierre de la app al abrir un PDF válido. Tope de
// 8 MP (32 MB) por página; ver `lazyPageBitmapSize` en PdfPageSource.kt, que lo aplica.
internal const val VIEWER_PAGE_MAX_PIXELS = 8_000_000L

internal data class PageBitmapSize(val width: Int, val height: Int)
