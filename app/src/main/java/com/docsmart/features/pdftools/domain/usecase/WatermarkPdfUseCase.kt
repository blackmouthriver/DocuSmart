package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
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
import kotlin.math.cos
import kotlin.math.sin

data class WatermarkMessages(
    val emptyTextError: String,
    val readError: String,
    val noPages: String,
    val generateError: String,
    // formato: %1$d páginas
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
)

class WatermarkPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "WatermarkPdfUseCase"
            private const val WATERMARK_ANGLE_DEGREES = 45.0

            // Bug real reportado por testers 2026-09-13: 0.15 (15% de opacidad)
            // combinado con ColorConstants.GRAY (gris medio) se renderiza casi
            // blanco sobre blanco (~93% blanco) -- técnicamente se dibuja, pero
            // resulta invisible a simple vista en la mayoría de documentos. Nunca
            // se había ajustado desde el commit inicial de esta función. Subido a
            // 0.3 (30%), suficiente para verse con claridad sin tapar el
            // contenido original de la página.
            // internal (no private): WatermarkPdfUseCaseTest verifica que se
            // mantenga por encima de un umbral visible -- 0.15 pasó desapercibido
            // como "casi invisible" precisamente porque nada lo cubría.
            internal const val WATERMARK_OPACITY = 0.3f
            private const val BASE_FONT_SIZE = 40f
            private const val MIN_FONT_SIZE = 8f
            private const val MAX_WIDTH_FACTOR = 1.3f

            // Límite del juego de caracteres Latin-1/WinAnsi que cubre la fuente
            // estándar Helvetica usada acá -- ver Hallazgo 5 de la auditoría r13.
            private const val MAX_WINANSI_CODE_POINT = 0xFF
        }

        /**
         * RF-PDF-07/HU-PDF-06: superpone el texto de marca de agua en diagonal
         * y semitransparente sobre cada página, escrito directamente vía
         * iText7 (`PdfCanvas` + `PdfExtGState` para la opacidad) -- no
         * rasteriza, conserva el contenido original de cada página
         * (RNF-PDF-01, mismo principio que Numerar páginas).
         */
        suspend operator fun invoke(
            pdfUri: Uri,
            watermarkText: String,
            outputFileName: String? = null,
            messages: WatermarkMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                if (watermarkText.isBlank()) {
                    return@withContext PdfToolResult.Error(messages.emptyTextError)
                }

                // Hallazgo real de la auditoría r13 (Media): PdfFontFactory.createFont
                // (StandardFonts.HELVETICA) solo cubre Latin-1/WinAnsi -- un texto en
                // ruso/chino/con emoji no fallaba con un error claro sino con la
                // excepción críptica de iText al codificar el glifo. Agregar una
                // fuente TTF Unicode embebida implicaría tocar assets/build.gradle.kts,
                // fuera del alcance de este lote -- se valida ANTES de generar el PDF
                // y se devuelve un mensaje claro en su lugar.
                if (watermarkText.any { it.code > MAX_WINANSI_CODE_POINT }) {
                    return@withContext PdfToolResult.Error(
                        context.getString(R.string.pdf_watermark_unsupported_chars_error),
                    )
                }

                var cacheFile: File? = null
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
                // outputFile era un `val` dentro del try -- el catch de abajo ni
                // siquiera podía referenciarlo para borrarlo si algo lanzaba a
                // mitad de camino, mismo patrón ya corregido en Compare/Compress/
                // OCR (hallazgos #25-27).
                var outputFile: File? = null
                try {
                    cacheFile = copyUriToCache(pdfUri)
                        ?: return@withContext PdfToolResult.Error(messages.readError)

                    outputFile = createOutputFile(outputFileName ?: "Watermarked")
                    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
                    val gState = PdfExtGState().setFillOpacity(WATERMARK_OPACITY)

                    // .use{} en vez de pdf.close() manual (mismo criterio ya
                    // documentado como bug real en SplitPdfUseCase/CompressPdfUseCase):
                    // una excepción a mitad del for no debe dejar el PdfDocument sin
                    // cerrar ni el archivo de salida a medio escribir.
                    var totalPages = 0
                    PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                        totalPages = pdf.numberOfPages
                        for (pageNumber in 1..totalPages) {
                            // Hallazgo real de la auditoría r13 (Alta): sin este
                            // ensureActive() la cancelación cooperativa no se
                            // notaba hasta terminar todas las páginas en segundo
                            // plano -- mismo patrón que CompressPdfUseCase.
                            coroutineContext.ensureActive()
                            val page = pdf.getPage(pageNumber)
                            drawWatermark(page, watermarkText, font, gState)
                        }
                    }

                    if (totalPages == 0) {
                        outputFile!!.delete()
                        return@withContext PdfToolResult.Error(messages.noPages)
                    }

                    if (outputFile!!.length() == 0L) {
                        return@withContext PdfToolResult.Error(messages.generateError)
                    }

                    Timber.d("$TAG: marca de agua exitosa — $totalPages páginas, ${outputFile.length() / 1024} KB")

                    PdfToolResult.Success(
                        outputFile = outputFile,
                        message = String.format(messages.success, totalPages),
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
                    Timber.e("$TAG: error al aplicar marca de agua: ${e.javaClass.simpleName}")
                    outputFile?.delete()
                    PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
                } finally {
                    cacheFile?.delete()
                }
            }

        private fun drawWatermark(
            page: com.itextpdf.kernel.pdf.PdfPage,
            text: String,
            font: com.itextpdf.kernel.font.PdfFont,
            gState: PdfExtGState,
        ) {
            val pageSize = page.pageSize
            val fontSize = fontSizeToFit(font, text, pageSize.width)
            val textWidth = font.getWidth(text, fontSize)

            val angleRad = Math.toRadians(WATERMARK_ANGLE_DEGREES)
            val cos = cos(angleRad).toFloat()
            val sin = sin(angleRad).toFloat()

            // Bug real reportado por testers 2026-09-13: `pageSize.width/height`
            // son solo las DIMENSIONES del MediaBox, no sus coordenadas -- un
            // PDF cuyo MediaBox no arranca en (0,0) (común en escaneos, o en un
            // PDF ya procesado antes por Recortar/Rotar de esta misma app) hacía
            // que la marca de agua se dibujara centrada respecto al origen
            // (0,0) en vez del centro real de la página, pudiendo caer fuera del
            // área visible. Se suma pageSize.left/pageSize.bottom para centrar
            // sobre las coordenadas reales de la página.
            val centerX = pageSize.left + pageSize.width / 2
            val centerY = pageSize.bottom + pageSize.height / 2
            val startX = centerX - (textWidth / 2) * cos
            val startY = centerY - (textWidth / 2) * sin

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
            font: com.itextpdf.kernel.font.PdfFont,
            text: String,
            pageWidth: Float,
        ): Float {
            val maxWidth = pageWidth * MAX_WIDTH_FACTOR
            val widthAtBase = font.getWidth(text, BASE_FONT_SIZE)
            if (widthAtBase <= maxWidth) return BASE_FONT_SIZE
            return (BASE_FONT_SIZE * (maxWidth / widthAtBase)).coerceAtLeast(MIN_FONT_SIZE)
        }

        private fun copyUriToCache(uri: Uri): File? {
            return try {
                val file = File(context.cacheDir, "watermark_${System.currentTimeMillis()}.pdf")
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
