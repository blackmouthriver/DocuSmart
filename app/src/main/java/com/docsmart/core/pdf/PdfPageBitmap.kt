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
        cacheFile.delete()
        return emptyList()
    }
    // Hallazgo real de la auditoría general 2026-09-17: cacheFile solo
    // hace falta durante el render (PdfRenderer exige un FileDescriptor
    // real, no puede leer un Uri/stream directo) -- antes nunca se
    // borraba, ni en éxito ni en error, acumulando una copia completa de
    // cada PDF abierto (Visor y Modo Estudio) en cacheDir para siempre,
    // fuera del alcance de "Limpiar caché" (que solo barre filesDir).
    return try {
        renderCachedPdfPages(cacheFile)
    } finally {
        cacheFile.delete()
    }
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
private fun copyContentUriToCache(uri: Uri, context: Context, cacheFile: File): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false
} catch (e: Exception) {
    Timber.e("PdfPageRenderer: error abriendo el content:// de origen (${e.javaClass.simpleName})")
    false
}

// Hallazgo real de la auditoría general 2026-09-17: si render()/openPage()
// lanzaba a mitad del bucle (PDF corrupto, página de tamaño extremo →
// OutOfMemoryError), pdfRenderer.close()/fileDescriptor.close() nunca se
// alcanzaban -- fuga del objeto nativo PdfRenderer y del descriptor de
// archivo en cada intento fallido. Cada recurso se cierra ahora en su
// propio finally, de adentro hacia afuera.
private fun renderCachedPdfPages(cacheFile: File): List<PdfPageBitmap> {
    val fileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    return try {
        val pdfRenderer = PdfRenderer(fileDescriptor)
        try {
            renderAllPages(pdfRenderer)
        } finally {
            pdfRenderer.close()
        }
    } finally {
        fileDescriptor.close()
    }
}

private fun renderAllPages(pdfRenderer: PdfRenderer): List<PdfPageBitmap> {
    val pages = mutableListOf<PdfPageBitmap>()
    for (i in 0 until pdfRenderer.pageCount) {
        val page = pdfRenderer.openPage(i)
        try {
            val bitmap = Bitmap.createBitmap(
                page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(PdfPageBitmap(bitmap, page.width.toFloat(), page.height.toFloat()))
        } finally {
            page.close()
        }
    }
    return pages
}
