package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.geom.Rectangle
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

data class CropPdfMessages(
    val readError    : String,
    val noPages      : String,
    val generateError: String,
    val success       : String, // formato: %1$d porcentaje de margen recortado
    val genericError  : String  // formato: %1$s mensaje de excepción
)

class CropPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CropPdfUseCase"
    }

    /**
     * RF-PDF-09: recorta un margen uniforme (mismo porcentaje en los 4
     * lados) de cada página, ajustando `MediaBox` y `CropBox` al nuevo
     * tamaño (`PdfPage.setMediaBox`/`setCropBox`) — el contenido de la
     * página no se toca ni se rasteriza, solo se reduce el área visible,
     * igual que RF-PDF-04 (Rotar) no reescribe el contenido.
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        marginPercent : Int = 10,
        outputFileName: String? = null,
        messages      : CropPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Recortado")
            val percent = marginPercent.coerceIn(0, 40)

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                if (pdf.numberOfPages == 0) {
                    return@withContext PdfToolResult.Error(messages.noPages)
                }
                for (pageNumber in 1..pdf.numberOfPages) {
                    val page = pdf.getPage(pageNumber)
                    val size = page.pageSize
                    val marginX = size.width * percent / 100f
                    val marginY = size.height * percent / 100f
                    val cropped = Rectangle(
                        size.x + marginX,
                        size.y + marginY,
                        size.width - 2 * marginX,
                        size.height - 2 * marginY
                    )
                    page.setMediaBox(cropped)
                    page.setCropBox(cropped)
                }
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: recorte exitoso — $percent% de margen")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, percent)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al recortar PDF")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "crop_${System.currentTimeMillis()}.pdf")
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
