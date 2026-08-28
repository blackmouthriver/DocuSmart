package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.properties.TextAlignment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class PageNumberFormat { NUMBER_ONLY, NUMBER_OF_TOTAL, PAGE_OF_TOTAL }

data class NumberPagesMessages(
    val readError           : String,
    val noPages             : String,
    val generateError       : String,
    val success             : String, // formato: %1$d páginas
    val genericError        : String, // formato: %1$s mensaje de excepción
    val pageOfTotalTemplate : String  // formato: %1$d página actual, %2$d total -- texto que se escribe en el PDF
)

class NumberPagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NumberPagesUseCase"
        private const val FONT_SIZE = 9f
        private const val BOTTOM_MARGIN = 20f
    }

    /**
     * RF-PDF-06/HU-PDF-05: numera cada página en el pie con el formato
     * elegido, escribiendo directamente sobre la página vía iText7
     * (`PdfCanvas`/`Canvas`) -- no rasteriza, conserva el contenido
     * original de cada página tal cual (mismo principio que Rotar/Unir,
     * RNF-PDF-01).
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        format        : PageNumberFormat = PageNumberFormat.PAGE_OF_TOTAL,
        outputFileName: String? = null,
        messages      : NumberPagesMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Numbered")
            val font       = PdfFontFactory.createFont(StandardFonts.HELVETICA)

            val pdf        = PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile))
            val totalPages = pdf.numberOfPages

            if (totalPages == 0) {
                pdf.close()
                outputFile.delete()
                return@withContext PdfToolResult.Error(messages.noPages)
            }

            for (pageNumber in 1..totalPages) {
                val page     = pdf.getPage(pageNumber)
                val pageSize = page.pageSize
                val text     = labelFor(format, pageNumber, totalPages, messages.pageOfTotalTemplate)

                val pdfCanvas = PdfCanvas(page)
                val canvas    = Canvas(pdfCanvas, pageSize)
                canvas.setFont(font).setFontSize(FONT_SIZE)
                canvas.showTextAligned(
                    text, pageSize.width / 2, BOTTOM_MARGIN, TextAlignment.CENTER
                )
                canvas.close()
            }
            pdf.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: numeración exitosa — $totalPages páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = String.format(messages.success, totalPages)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al numerar páginas")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun labelFor(
        format: PageNumberFormat, pageNumber: Int, totalPages: Int, pageOfTotalTemplate: String
    ): String = when (format) {
        PageNumberFormat.NUMBER_ONLY     -> pageNumber.toString()
        PageNumberFormat.NUMBER_OF_TOTAL -> "$pageNumber / $totalPages"
        PageNumberFormat.PAGE_OF_TOTAL   -> String.format(pageOfTotalTemplate, pageNumber, totalPages)
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "numberpages_${System.currentTimeMillis()}.pdf")
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
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
