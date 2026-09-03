package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.geom.Vector
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * RF-CONV-09: a diferencia de la versión anterior (extraía texto plano con
 * `PdfTextExtractor` y lo volcaba en un .docx mínimo escrito a mano por ZIP),
 * esta reconstruye negrita/cursiva/tamaño de fuente por fragmento de texto y
 * separa párrafos reales -- no solo un salto de línea por línea de texto.
 *
 * Alcance honesto (ver RNF-CONV-08): esto NO es una reconstrucción visual del
 * PDF -- no reproduce imágenes embebidas, tablas, columnas ni la posición
 * exacta del texto. Es una mejora de fidelidad sobre texto plano: negrita,
 * cursiva, tamaño de fuente y párrafos reales (detectados por el espaciado
 * vertical real entre líneas), igual de "basado en texto" que el resto de
 * conversiones "a PDF"/"a Word" del módulo (RNF-CONV-02).
 */
class PdfToWordUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Un salto de línea con espaciado mayor a este múltiplo del tamaño
        // de fuente se interpreta como fin de párrafo (interlineado extra /
        // línea en blanco), no como ajuste de línea dentro del mismo
        // párrafo. Valor empírico: el interlineado normal de un PDF
        // generado por Word/LibreOffice ronda 1.15-1.2x el tamaño de
        // fuente; el espaciado entre párrafos suele ser notablemente mayor.
        private const val PARAGRAPH_GAP_MULTIPLIER = 1.6f

        // Tolerancia para considerar dos fragmentos de texto como parte de
        // la misma línea (misma coordenada Y de línea base, con margen por
        // redondeos de punto flotante).
        private const val SAME_LINE_TOLERANCE = 1f

        // Bug real reportado por el usuario 2026-09-03: un .docx generado
        // por esta conversión mostraba palabras pegadas ("Funza,Cundinamarca,
        // 03deseptiembrede2026"). Causa: muchos generadores de PDF NO
        // codifican el espacio entre palabras como un carácter " " real --
        // en vez de eso, dibujan "Funza," y "Cundinamarca," como dos
        // operaciones de texto separadas, simplemente moviendo el cursor
        // horizontalmente entre una y otra (el espacio es un hueco visual,
        // no un carácter). `TextRenderInfo.getText()` solo devuelve los
        // glifos de CADA operación por separado, así que un salto de línea
        // dentro del mismo párrafo ya insertaba un espacio (`isWrappedLine`
        // más abajo), pero dos fragmentos en la MISMA línea con ese mismo
        // hueco se pegaban sin nada entre ellos. Umbral empírico: el ancho
        // de un espacio real ronda 0.2-0.3x el tamaño de fuente; un valor
        // menor evita separar letras con kerning normal dentro de una
        // palabra.
        private const val WORD_GAP_MULTIPLIER = 0.2f
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = File(context.cacheDir, "pdftodocx_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            val pdfDoc = PdfDocument(PdfReader(cacheFile))
            val totalPages = pdfDoc.numberOfPages
            val pages = (1..totalPages).map { i ->
                val listener = FormattedTextListener()
                PdfCanvasProcessor(listener).processPageContent(pdfDoc.getPage(i))
                listener.chunks
            }
            pdfDoc.close()

            if (pages.all { page -> page.all { it.text.isBlank() } }) {
                return@withContext ConversionResult.Error(
                    "El PDF no contiene texto extraíble. Puede ser un PDF escaneado."
                )
            }

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.docx")

            buildDocx(pages, outputFile)

            if (outputFile.length() == 0L)
                return@withContext ConversionResult.Error("Error al generar el archivo Word")

            Timber.d("PdfToWordUseCase: docx creado — ${outputFile.length() / 1024} KB")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = totalPages,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "PdfToWordUseCase: error — ${e.message}")
            ConversionResult.Error("Error al convertir: ${e.message}")
        } finally {
            cacheFile?.delete()
        }
    }

    private fun buildDocx(pages: List<List<TextChunk>>, outputFile: File) {
        XWPFDocument().use { doc ->
            var paragraph = doc.createParagraph()
            var previousY: Float? = null
            var previousXEnd: Float? = null

            pages.forEachIndexed { pageIndex, chunks ->
                if (pageIndex > 0) {
                    paragraph.createRun().addBreak(BreakType.PAGE)
                    paragraph = doc.createParagraph()
                    previousY = null
                    previousXEnd = null
                }
                chunks.forEach { chunk ->
                    if (chunk.text.isBlank()) return@forEach

                    val placement = classifyChunkPlacement(chunk, previousY, previousXEnd)
                    if (placement.isNewParagraph) paragraph = doc.createParagraph()

                    val run = paragraph.createRun()
                    val prefix = if (placement.needsLeadingSpace && !chunk.text.startsWith(" ")) " " else ""
                    run.setText(prefix + chunk.text)
                    run.isBold = chunk.bold
                    run.isItalic = chunk.italic
                    run.fontSize = chunk.fontSize.toInt().coerceAtLeast(1)

                    previousY = chunk.y
                    previousXEnd = chunk.xEnd
                }
            }
            outputFile.outputStream().use { doc.write(it) }
        }
    }

    private data class ChunkPlacement(val isNewParagraph: Boolean, val needsLeadingSpace: Boolean)

    /**
     * Decide si `chunk` empieza un párrafo nuevo (salto vertical grande),
     * continúa una línea envuelta dentro del mismo párrafo (salto vertical
     * chico), o comparte línea con el fragmento anterior (`previousY`) --
     * en cuyo caso un hueco horizontal real frente a `previousXEnd` (ver
     * WORD_GAP_MULTIPLIER) también amerita un espacio, aunque no haya salto
     * de línea de por medio.
     */
    private fun classifyChunkPlacement(chunk: TextChunk, previousY: Float?, previousXEnd: Float?): ChunkPlacement {
        if (previousY == null) return ChunkPlacement(isNewParagraph = false, needsLeadingSpace = false)

        val sameLine = abs(previousY - chunk.y) <= SAME_LINE_TOLERANCE
        val wordGapThreshold = WORD_GAP_MULTIPLIER * chunk.fontSize
        val hasWordGap = sameLine && previousXEnd != null && (chunk.xStart - previousXEnd) > wordGapThreshold
        val isNewParagraph = !sameLine && (previousY - chunk.y) > PARAGRAPH_GAP_MULTIPLIER * chunk.fontSize
        val isWrappedLine = !sameLine && !isNewParagraph

        return ChunkPlacement(isNewParagraph = isNewParagraph, needsLeadingSpace = isWrappedLine || hasWordGap)
    }

    private data class TextChunk(
        val text: String,
        val fontSize: Float,
        val bold: Boolean,
        val italic: Boolean,
        val y: Float,
        val xStart: Float,
        val xEnd: Float
    )

    /**
     * Escucha los eventos de renderizado de texto de iText7 durante el
     * recorrido del stream de contenido de una página -- cada evento trae
     * el fragmento de texto tal como lo dibujó el PDF (respeta los cortes
     * que el propio documento ya hace por cambio de fuente/estilo), su
     * fuente real y la posición de línea base (para detectar párrafos).
     */
    private class FormattedTextListener : IEventListener {
        val chunks = mutableListOf<TextChunk>()

        override fun eventOccurred(data: IEventData?, type: EventType) {
            val info = data as? TextRenderInfo ?: return
            val text = info.text
            if (text.isEmpty()) return

            val fontNames = info.font?.fontProgram?.fontNames
            val fontName = fontNames?.fontName.orEmpty()

            chunks.add(
                TextChunk(
                    text = text,
                    fontSize = info.fontSize,
                    bold = fontNames?.isBold == true || fontName.contains("bold", ignoreCase = true),
                    italic = fontNames?.isItalic == true ||
                        fontName.contains("italic", ignoreCase = true) ||
                        fontName.contains("oblique", ignoreCase = true),
                    y = info.baseline.startPoint.get(Vector.I2),
                    xStart = info.baseline.startPoint.get(Vector.I1),
                    xEnd = info.baseline.endPoint.get(Vector.I1)
                )
            )
        }

        override fun getSupportedEvents(): MutableSet<EventType> = mutableSetOf(EventType.RENDER_TEXT)
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
