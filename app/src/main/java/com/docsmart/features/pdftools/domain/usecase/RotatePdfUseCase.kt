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

data class RotatePdfMessages(
    val readError: String,
    // Hallazgo real de la revisión general 2026-09-16 (#28): era la única
    // de las 8 herramientas comparables sin validar 0 páginas -- un PDF
    // vacío "rotaba con éxito" (el archivo de 0 páginas ya pesa > 0 bytes,
    // el chequeo de abajo tampoco lo detectaba).
    val noPages: String,
    val generateError: String,
    // formato: %1$d grados
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
)

class RotatePdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            outputFileName: String? = null,
            messages: RotatePdfMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
                // outputFile era un `val` dentro del try -- el catch de abajo ni
                // siquiera podía referenciarlo para borrarlo si el for de páginas
                // lanzaba a mitad de camino (content stream corrupto, etc.), mismo
                // patrón ya corregido en Compare/Compress/OCR (hallazgos #25-27).
                var outputFile: File? = null
                try {
                    cacheFile = copyUriToCache(pdfUri)
                        ?: return@withContext PdfToolResult.Error(messages.readError)

                    outputFile = createOutputFile(outputFileName ?: "Rotated_${degrees}deg")

                    PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                        if (pdf.numberOfPages == 0) {
                            outputFile!!.delete()
                            return@withContext PdfToolResult.Error(messages.noPages)
                        }
                        for (pageNumber in 1..pdf.numberOfPages) {
                            val page = pdf.getPage(pageNumber)
                            val newRotation =
                                ((page.getRotation() + degrees) % FULL_TURN_DEGREES + FULL_TURN_DEGREES) %
                                    FULL_TURN_DEGREES
                            page.setRotation(newRotation)
                        }
                    }

                    if (outputFile!!.length() == 0L) {
                        return@withContext PdfToolResult.Error(messages.generateError)
                    }

                    Timber.d("$TAG: rotación exitosa $degrees° — ${outputFile.length() / 1024} KB")

                    PdfToolResult.Success(
                        outputFile = outputFile,
                        message = String.format(messages.success, degrees),
                    )
                } catch (e: Exception) {
                    Timber.e("$TAG: error al rotar PDF: ${e.javaClass.simpleName}")
                    outputFile?.delete()
                    PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
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
                        if (bytes == 0L) {
                            // Evita dejar el archivo vacío huérfano en cacheDir.
                            file.delete()
                            return null
                        }
                    }
                } ?: return null
                file
            } catch (e: Exception) {
                Timber.e("$TAG: error copiando URI al cache: ${e.javaClass.simpleName}")
                null
            }
        }

        private fun createOutputFile(name: String): File {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
            return File(dir, "DocuSmart_${name}_$timestamp.pdf")
        }
    }
