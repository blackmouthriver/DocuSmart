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

class RotatePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RotatePdfUseCase"
        private const val FULL_TURN_DEGREES = 360
    }

    /**
     * Rota vía iText7 (`PdfPage.setRotation`) en lugar de rasterizar cada
     * página a bitmap: conserva texto/vectores seleccionables y evita que
     * el ángulo aplicado dependa de un cálculo manual de matriz.
     */
    suspend operator fun invoke(
        pdfUri: Uri,
        degrees: Int = 90,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(
                    "No se pudo leer el PDF. Verifica que sea un archivo válido."
                )

            val outputFile = createOutputFile(outputFileName ?: "Rotated_${degrees}deg")

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                for (pageNumber in 1..pdf.numberOfPages) {
                    val page = pdf.getPage(pageNumber)
                    val newRotation = ((page.getRotation() + degrees) % FULL_TURN_DEGREES + FULL_TURN_DEGREES) %
                        FULL_TURN_DEGREES
                    page.setRotation(newRotation)
                }
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error("Error al generar el PDF rotado.")
            }

            Timber.d("$TAG: rotación exitosa ${degrees}° — ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = "PDF rotado ${degrees}° correctamente"
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al rotar PDF")
            PdfToolResult.Error("Error al rotar PDF: ${e.message ?: "Error desconocido"}", e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "rotate_${System.currentTimeMillis()}.pdf")
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
