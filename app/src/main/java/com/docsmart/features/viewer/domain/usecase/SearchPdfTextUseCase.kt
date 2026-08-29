package com.docsmart.features.viewer.domain.usecase

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/** Posición real (puntos PDF, origen inferior-izquierda) de una coincidencia. */
data class PdfMatchRect(
    val xPts     : Float,
    val yPts     : Float,
    val widthPts : Float,
    val heightPts: Float
)

/** Página (1-based) y todas las coincidencias reales encontradas en ella. */
data class PdfPageMatches(
    val pageNumber: Int,
    val rects     : List<PdfMatchRect>
)

/**
 * RF-VIS-08: busca texto dentro de un PDF y devuelve, por página, la
 * posición real (no solo el número de página) de cada coincidencia --
 * mismo mecanismo que `EditTextPdfUseCase.findMatches()` en Herramientas
 * PDF (`RegexBasedLocationExtractionStrategy`, parte de iText7-core). Esta
 * clase originalmente solo devolvía números de página (documentado como
 * "no hay resaltado inline posible sin reescribir el renderer" en
 * RNF-VIS-01) -- esa nota quedó desactualizada en cuanto el proyecto
 * construyó este mismo mecanismo de posición real para RF-PDF-10/RF-PDF-15.
 * `PdfViewerContent` usa estas coordenadas para dibujar el resaltado
 * directamente sobre el bitmap ya renderizado, sin tocar el renderer.
 */
class SearchPdfTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SearchPdfTextUseCase"
    }

    // Contraseña incorrecta o datos corruptos deben verse igual para quien llama:
    // cualquier fallo al abrir/leer el PDF se trata como "sin coincidencias",
    // no como error fatal de toda la búsqueda.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri, query: String): List<PdfPageMatches> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val cacheFile = copyToCache(uri) ?: return@withContext emptyList()
        try {
            val pdf = PdfDocument(PdfReader(cacheFile))
            val matches = findMatches(pdf, query)
            pdf.close()
            matches
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error buscando en el PDF")
            emptyList()
        } finally {
            cacheFile.delete()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun findMatches(pdf: PdfDocument, query: String): List<PdfPageMatches> {
        val regex = "(?i)" + Pattern.quote(query)
        val matches = mutableListOf<PdfPageMatches>()
        for (pageNumber in 1..pdf.numberOfPages) {
            try {
                val strategy = RegexBasedLocationExtractionStrategy(regex)
                PdfCanvasProcessor(strategy).processPageContent(pdf.getPage(pageNumber))
                val rects = strategy.resultantLocations.map { location ->
                    val r = location.rectangle
                    PdfMatchRect(r.x, r.y, r.width, r.height)
                }
                if (rects.isNotEmpty()) matches.add(PdfPageMatches(pageNumber, rects))
            } catch (e: Exception) {
                Timber.w("$TAG: no se pudo buscar en la página $pageNumber — ${e.message}")
            }
        }
        return matches
    }

    @Suppress("TooGenericExceptionCaught")
    private fun copyToCache(uri: Uri): File? {
        val file = File(context.cacheDir, "search_${System.currentTimeMillis()}.pdf")
        val copied = try {
            if (uri.scheme == "file") copyFileUri(uri, file) else copyContentUri(uri, file)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            false
        }
        return if (copied) file else null
    }

    private fun copyFileUri(uri: Uri, dest: File): Boolean {
        val src = uri.path?.let { File(it) }
        return if (src != null && src.exists()) {
            src.copyTo(dest, overwrite = true)
            true
        } else {
            false
        }
    }

    private fun copyContentUri(uri: Uri, dest: File): Boolean =
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } != null
}
