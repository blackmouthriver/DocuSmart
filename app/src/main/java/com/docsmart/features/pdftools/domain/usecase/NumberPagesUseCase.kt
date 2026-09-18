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
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // outputFile era un `val` dentro del try -- el catch de abajo ni
        // siquiera podía referenciarlo para borrarlo si algo lanzaba a
        // mitad de camino, mismo patrón ya corregido en Compare/Compress/
        // OCR (hallazgos #25-27).
        var outputFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            outputFile = createOutputFile(outputFileName ?: "Numbered")
            val font       = PdfFontFactory.createFont(StandardFonts.HELVETICA)

            // .use{} en vez de pdf.close() manual (mismo criterio ya
            // documentado como bug real en SplitPdfUseCase/CompressPdfUseCase):
            // una excepción a mitad del for no debe dejar el PdfDocument sin
            // cerrar ni el archivo de salida a medio escribir.
            var totalPages = 0
            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                totalPages = pdf.numberOfPages
                for (pageNumber in 1..totalPages) {
                    // Hallazgo real de la auditoría r13 (Alta): sin este
                    // ensureActive() la cancelación cooperativa (usuario
                    // navega hacia atrás mientras se numera un PDF largo)
                    // no se notaba hasta terminar todas las páginas en
                    // segundo plano -- mismo patrón que CompressPdfUseCase.
                    coroutineContext.ensureActive()
                    val page = pdf.getPage(pageNumber)
                    val text = labelFor(format, pageNumber, totalPages, messages.pageOfTotalTemplate)
                    drawPageNumber(page, text, font)
                }
            }

            if (totalPages == 0) {
                outputFile!!.delete()
                return@withContext PdfToolResult.Error(messages.noPages)
            }

            if (outputFile!!.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: numeración exitosa — $totalPages páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = String.format(messages.success, totalPages)
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
            Timber.e(e, "$TAG: error al numerar páginas")
            outputFile?.delete()
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    // Hallazgo real de la auditoría r13 (Alta): antes se anclaba el texto en
    // pageSize.width/2 y un margen Y absoluto, tratando las DIMENSIONES del
    // MediaBox como si fueran sus COORDENADAS -- en un PDF cuyo MediaBox no
    // arranca en (0,0) (común en escaneos o en un PDF ya recortado/rotado
    // por esta misma app) el número caía fuera del centro real de la
    // página. Se usa pageSize.left/pageSize.bottom para el centro real,
    // mismo criterio ya corregido en WatermarkPdfUseCase.drawWatermark().
    // Además, page.getRotation() no se consideraba: en una página rotada
    // 90/180/270 el "pie de página" visual no coincide con el borde inferior
    // del MediaBox sin rotar -- se elige el punto de anclaje según la
    // rotación y se dibuja el texto con esa misma orientación (matriz de
    // texto vía PdfCanvas, igual mecanismo que ya usa drawWatermark()).
    private fun drawPageNumber(
        page: com.itextpdf.kernel.pdf.PdfPage,
        text: String,
        font: com.itextpdf.kernel.font.PdfFont
    ) {
        val pageSize = page.pageSize
        val rotation = ((page.getRotation() % 360) + 360) % 360
        val textWidth = font.getWidth(text, FONT_SIZE)

        // Revisión adversarial de correctitud (ronda 13): el signo de
        // angleRad estaba invertido (-rotation en vez de +rotation) y los
        // anchors de 90/270 estaban CRUZADOS entre sí -- ambos errores se
        // cancelaban parcialmente en apariencia para 0°/180° (sin(±0)=0,
        // sin(±180)=0), por eso solo se notaba en 90°/270°. Verificado con
        // la matriz de transformación de display estándar de PDF para cada
        // ángulo de /Rotate: para 90° CW, (x,y)_raw -> (y, W-x)_disp: el
        // punto raw que cae en el centro-inferior visual es
        // (right-margin, bottom+height/2), no (left+margin, ...). Para
        // 270° CW, (x,y)_raw -> (H-y, x)_disp: el punto raw correcto es
        // (left+margin, bottom+height/2). La dirección del texto (para que
        // se lea derecha tras la rotación de display) es rotation, no
        // -rotation.
        val angleRad = Math.toRadians(rotation.toDouble()).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        val (anchorX, anchorY) = when (rotation) {
            90  -> (pageSize.right - BOTTOM_MARGIN) to (pageSize.bottom + pageSize.height / 2)
            180 -> (pageSize.left + pageSize.width / 2) to (pageSize.top - BOTTOM_MARGIN)
            270 -> (pageSize.left + BOTTOM_MARGIN) to (pageSize.bottom + pageSize.height / 2)
            else -> (pageSize.left + pageSize.width / 2) to (pageSize.bottom + BOTTOM_MARGIN)
        }
        val startX = anchorX - (textWidth / 2) * cosA
        val startY = anchorY - (textWidth / 2) * sinA

        val pdfCanvas = PdfCanvas(page)
        pdfCanvas.beginText()
        pdfCanvas.setFontAndSize(font, FONT_SIZE)
        pdfCanvas.setTextMatrix(cosA, sinA, -sinA, cosA, startX, startY)
        pdfCanvas.showText(text)
        pdfCanvas.endText()
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
