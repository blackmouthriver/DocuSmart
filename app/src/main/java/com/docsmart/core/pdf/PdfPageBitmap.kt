package com.docsmart.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File

/**
 * Extraído de `ViewerScreen.kt` (2026-09-08) al necesitarse una segunda vez,
 * en Modo Estudio (RF-STU: mostrar el PDF mientras la voz lee, en vez de los
 * párrafos extraídos) -- antes vivía privado en el Visor, sin motivo para
 * duplicar la lógica de render a bitmap en dos features.
 */

/** Página renderizada + su tamaño real en puntos PDF (necesario para
 *  convertir coordenadas del PDF a píxeles de pantalla, ej. resaltado de
 *  búsqueda en el Visor). */
data class PdfPageBitmap(val bitmap: Bitmap, val pageWidthPts: Float, val pageHeightPts: Float)

/** Copia el PDF de [uri] al caché de la app y renderiza cada página a un [Bitmap]. */
fun renderPdfPagesToBitmaps(uri: Uri, context: Context, cachePrefix: String = "preview"): List<PdfPageBitmap> {
    val cacheFile = File(context.cacheDir, "${cachePrefix}_${System.currentTimeMillis()}.pdf")
    if (!copyPdfUriToCache(uri, context, cacheFile)) {
        Timber.e("PdfPageRenderer: no se pudo copiar el PDF al caché")
        return emptyList()
    }
    return renderCachedPdfPages(cacheFile)
}

private fun copyPdfUriToCache(uri: Uri, context: Context, cacheFile: File): Boolean =
    if (uri.scheme == "file") copyFileSchemeToCache(uri, cacheFile)
    else copyContentUriToCache(uri, context, cacheFile)

private fun copyFileSchemeToCache(uri: Uri, cacheFile: File): Boolean {
    val srcFile = uri.path?.let(::File)
    val valid = srcFile != null && srcFile.exists() && srcFile.length() > 0
    if (valid) {
        java.io.FileInputStream(srcFile).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return valid
}

@Suppress("TooGenericExceptionCaught")
private fun copyContentUriToCache(uri: Uri, context: Context, cacheFile: File): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false
} catch (e: Exception) {
    Timber.e("PdfPageRenderer: error openInputStream → ${e.message}")
    false
}

private fun renderCachedPdfPages(cacheFile: File): List<PdfPageBitmap> {
    val fileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val pdfRenderer = PdfRenderer(fileDescriptor)
    val pages = mutableListOf<PdfPageBitmap>()

    for (i in 0 until pdfRenderer.pageCount) {
        val page = pdfRenderer.openPage(i)
        val bitmap = Bitmap.createBitmap(
            page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pages.add(PdfPageBitmap(bitmap, page.width.toFloat(), page.height.toFloat()))
        page.close()
    }

    pdfRenderer.close()
    fileDescriptor.close()
    return pages
}
