package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class CropPdfMessages(
    val readError: String,
    val noPages: String,
    val generateError: String,
    // formato: %1$d porcentaje de margen recortado
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
)

class CropPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            pdfUri: Uri,
            marginPercent: Int = 10,
            outputFileName: String? = null,
            messages: CropPdfMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
                // outputFile era un `val` dentro del try -- el catch de abajo ni
                // siquiera podía referenciarlo para borrarlo si el for de páginas
                // lanzaba a mitad de camino, mismo patrón ya corregido en
                // Compare/Compress/OCR (hallazgos #25-27).
                var outputFile: File? = null
                try {
                    cacheFile = copyUriToCache(pdfUri)
                        ?: return@withContext PdfToolResult.Error(messages.readError)

                    outputFile = createOutputFile(outputFileName ?: "Recortado")
                    val percent = marginPercent.coerceIn(0, 40)

                    PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                        if (pdf.numberOfPages == 0) {
                            // Bug real encontrado 2026-09-14 (repaso general):
                            // PdfWriter(outputFile) ya crea el archivo en disco al
                            // abrirse -- sin este delete() quedaba huérfano en
                            // filesDir/pdftools para siempre.
                            outputFile!!.delete()
                            return@withContext PdfToolResult.Error(messages.noPages)
                        }
                        for (pageNumber in 1..pdf.numberOfPages) {
                            // Hallazgo real de la auditoría r13 (Alta): sin este
                            // ensureActive() la cancelación cooperativa no se
                            // notaba hasta terminar de recortar todas las páginas
                            // en segundo plano -- mismo patrón que CompressPdfUseCase.
                            coroutineContext.ensureActive()
                            val page = pdf.getPage(pageNumber)
                            val size = page.pageSize
                            val marginX = size.width * percent / 100f
                            val marginY = size.height * percent / 100f
                            val cropped =
                                Rectangle(
                                    size.x + marginX,
                                    size.y + marginY,
                                    size.width - 2 * marginX,
                                    size.height - 2 * marginY,
                                )
                            page.setMediaBox(cropped)
                            page.setCropBox(cropped)
                        }
                    }

                    if (outputFile!!.length() == 0L) {
                        return@withContext PdfToolResult.Error(messages.generateError)
                    }

                    Timber.d("$TAG: recorte exitoso — $percent% de margen")

                    PdfToolResult.Success(
                        outputFile = outputFile,
                        message = String.format(messages.success, percent),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo real de la auditoría r13 (Media): CancellationException
                    // hereda de Exception, así que sin este catch específico antes
                    // del genérico de abajo cada cancelación real se registraba
                    // como error. Se relanza tal cual, mismo patrón que
                    // CompressPdfUseCase.
                    outputFile?.delete()
                    throw e
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría r13 (Media): OutOfMemoryError no
                    // hereda de Exception en Kotlin/Java, así que el catch genérico
                    // de abajo nunca lo atrapaba y outputFile quedaba huérfano.
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: error al recortar PDF")
                    outputFile?.delete()
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
