package com.docsmart.features.viewer.domain.usecase

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Busca texto dentro de un PDF y devuelve los números de página (1-based)
 * donde aparece. El visor muestra los PDF como bitmaps renderizados
 * (`android.graphics.pdf.PdfRenderer`), así que no hay resaltado inline como
 * en Word/Excel/Texto — solo salto a la página con coincidencias.
 */
class SearchPdfTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SearchPdfTextUseCase"
    }

    // Contraseña incorrecta o datos corruptos deben verse igual para quien llama:
    // cualquier fallo al abrir/leer una página se trata como "sin coincidencia"
    // en esa página, no como error fatal de toda la búsqueda.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri, query: String): List<Int> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val cacheFile = copyToCache(uri) ?: return@withContext emptyList()
        try {
            val pdf = PdfDocument(PdfReader(cacheFile))
            val matches = (1..pdf.numberOfPages).filter { pageNumber ->
                textOfPage(pdf, pageNumber).contains(query, ignoreCase = true)
            }
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
    private fun textOfPage(pdf: PdfDocument, pageNumber: Int): String = try {
        PdfTextExtractor.getTextFromPage(pdf.getPage(pageNumber))
    } catch (e: Exception) {
        Timber.w("$TAG: no se pudo extraer texto de la página $pageNumber — ${e.message}")
        ""
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
