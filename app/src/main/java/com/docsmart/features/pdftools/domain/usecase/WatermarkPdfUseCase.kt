package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

data class WatermarkMessages(
    val emptyTextError: String,
    val readError      : String,
    val noPages        : String,
    val generateError  : String,
    val success         : String, // formato: %1$d páginas
    val genericError     : String  // formato: %1$s mensaje de excepción
)

class WatermarkPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WatermarkPdfUseCase"
        private const val WATERMARK_ANGLE_DEGREES = 45.0
        private const val WATERMARK_OPACITY = 0.15f
        private const val BASE_FONT_SIZE = 40f
        private const val MIN_FONT_SIZE = 8f
        private const val MAX_WIDTH_FACTOR = 1.3f
    }

    /**
     * RF-PDF-07/HU-PDF-06: superpone el texto de marca de agua en diagonal
     * y semitransparente sobre cada página, escrito directamente vía
     * iText7 (`PdfCanvas` + `PdfExtGState` para la opacidad) -- no
     * rasteriza, conserva el contenido original de cada página
     * (RNF-PDF-01, mismo principio que Numerar páginas).
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        watermarkText : String,
        outputFileName: String? = null,
        messages      : WatermarkMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (watermarkText.isBlank()) {
            return@withContext PdfToolResult.Error(messages.emptyTextError)
        }

        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Watermarked")
            val font       = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            val gState     = PdfExtGState().setFillOpacity(WATERMARK_OPACITY)

            val pdf        = PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile))
            val totalPages = pdf.numberOfPages

            if (totalPages == 0) {
                pdf.close()
                outputFile.delete()
                return@withContext PdfToolResult.Error(messages.noPages)
            }

            for (pageNumber in 1..totalPages) {
                val page = pdf.getPage(pageNumber)
                drawWatermark(page, watermarkText, font, gState)
            }
            pdf.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: marca de agua exitosa — $totalPages páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = String.format(messages.success, totalPages)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al aplicar marca de agua")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun drawWatermark(
        page: com.itextpdf.kernel.pdf.PdfPage,
        text: String,
        font: com.itextpdf.kernel.font.PdfFont,
        gState: PdfExtGState
    ) {
        val pageSize  = page.pageSize
        val fontSize  = fontSizeToFit(font, text, pageSize.width)
        val textWidth = font.getWidth(text, fontSize)

        val angleRad = Math.toRadians(WATERMARK_ANGLE_DEGREES)
        val cos = cos(angleRad).toFloat()
        val sin = sin(angleRad).toFloat()

        val centerX = pageSize.width / 2
        val centerY = pageSize.height / 2
        val startX  = centerX - (textWidth / 2) * cos
        val startY  = centerY - (textWidth / 2) * sin

        val canvas = PdfCanvas(page)
        canvas.saveState()
        canvas.setExtGState(gState)
        canvas.beginText()
        canvas.setFontAndSize(font, fontSize)
        canvas.setColor(ColorConstants.GRAY, true)
        canvas.setTextMatrix(cos, sin, -sin, cos, startX, startY)
        canvas.showText(text)
        canvas.endText()
        canvas.restoreState()
    }

    private fun fontSizeToFit(
        font: com.itextpdf.kernel.font.PdfFont, text: String, pageWidth: Float
    ): Float {
        val maxWidth      = pageWidth * MAX_WIDTH_FACTOR
        val widthAtBase   = font.getWidth(text, BASE_FONT_SIZE)
        if (widthAtBase <= maxWidth) return BASE_FONT_SIZE
        return (BASE_FONT_SIZE * (maxWidth / widthAtBase)).coerceAtLeast(MIN_FONT_SIZE)
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "watermark_${System.currentTimeMillis()}.pdf")
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
