package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SplitPdfMessages(
    val readError    : String,
    val noPages      : String,
    val rangeTooSmall: String, // formato: %1$d total de páginas
    val generateError: String,
    val success       : String, // formato: %1$d páginas, %2$d KB
    val genericError  : String  // formato: %1$s mensaje de excepción
)

class SplitPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SplitPdfUseCase"
    }

    suspend operator fun invoke(
        pdfUri        : Uri,
        fromPage      : Int,
        toPage        : Int,
        outputFileName: String? = null,
        messages      : SplitPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // outputFile era un `lateinit var` dentro del try -- el catch de
        // abajo ni siquiera podía referenciarlo para borrarlo si
        // copyPagesTo() lanzaba a mitad de camino, mismo patrón ya
        // corregido en Compare/Compress/OCR (hallazgos #25-27). `File?` en
        // vez de `lateinit` porque `::localVar.isInitialized` no está
        // soportado para variables locales (solo propiedades).
        var outputFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            // Bug real corregido 2026-09-08: rechazaba extraer exactamente
            // UNA página de un PDF de más de una página ("rango muy
            // pequeño"), aunque la interfaz sí permite dejar "desde" y
            // "hasta" en el mismo número y no bloquea el botón -- el
            // usuario terminaba con un error en vez de su PDF de una sola
            // página, un caso de uso normal (ej. "extraer solo la página 3").
            // De paso, `sourcePdf`/`destPdf` antes se cerraban a mano solo
            // en el camino feliz -- `.use{}` los cierra pase lo que pase,
            // mismo patrón ya usado en `RotatePdfUseCase`.
            var startPage = 0
            var endPage = 0

            PdfDocument(PdfReader(cacheFile)).use { sourcePdf ->
                val totalPages = sourcePdf.numberOfPages
                Timber.d("$TAG: PDF abierto — $totalPages páginas totales")

                if (totalPages == 0) {
                    return@withContext PdfToolResult.Error(messages.noPages)
                }

                startPage = fromPage.coerceIn(1, totalPages)
                endPage   = toPage.coerceIn(startPage, totalPages)
                Timber.d("$TAG: extrayendo páginas $startPage a $endPage")

                val name = outputFileName ?: "Split_p${startPage}-p${endPage}"
                outputFile = createOutputFile(name)

                PdfDocument(PdfWriter(outputFile)).use { destPdf ->
                    sourcePdf.copyPagesTo(startPage, endPage, destPdf)
                }
            }

            if (outputFile!!.length() == 0L)
                return@withContext PdfToolResult.Error(messages.generateError)

            val pagesExtracted = endPage - startPage + 1
            val sizeKb = outputFile.length() / 1024
            Timber.d("$TAG: split exitoso — $pagesExtracted páginas, $sizeKb KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = String.format(messages.success, pagesExtracted, sizeKb)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al dividir PDF")
            outputFile?.delete()
            PdfToolResult.Error(
                message = String.format(messages.genericError, e.message ?: ""),
                cause   = e
            )
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "split_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir       = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}