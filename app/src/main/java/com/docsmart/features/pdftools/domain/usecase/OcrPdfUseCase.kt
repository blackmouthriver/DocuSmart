package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.coroutineContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class OcrPdfMessages(
    val readError    : String,
    val noPages      : String,
    val alreadyHasText: String,
    val noTextFound   : String,
    val generateError : String,
    val success        : String, // formato: %1$d páginas, %2$d palabras reconocidas
    val genericError    : String  // formato: %1$s mensaje de excepción
)

/**
 * Posición en el PDF (puntos, origen inferior-izquierda) de una palabra
 * detectada por OCR. Resultado de [mapOcrBoxToPdf].
 */
internal data class OcrWordPlacement(
    val text: String,
    val x: Float,
    val yBaseline: Float,
    val widthPts: Float,
    val heightPts: Float
)

/** Bounding box de una palabra detectada, en píxeles del bitmap renderizado
 *  (origen superior-izquierda, como Android `Rect`). */
internal data class OcrBoxPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Geometría necesaria para convertir píxeles del bitmap a puntos del PDF:
 *  origen de la página (`x`,`y`), su alto en puntos, y la escala a la que
 *  se renderizó el bitmap respecto al tamaño real de la página. */
internal data class PdfPageGeometry(val x: Float, val y: Float, val height: Float, val renderScale: Float)

/**
 * Convierte el bounding box de una palabra a coordenadas reales del PDF
 * (puntos, origen inferior-izquierda, como espera `PdfCanvas`). Función
 * pura sin dependencias de Android/iText -- se puede testear en JVM puro
 * pese a que el resto del use case no se puede (requiere `PdfRenderer` +
 * ML Kit, solo disponibles en runtime Android real, mismo límite ya
 * documentado para `CompressPdfUseCase`).
 */
internal fun mapOcrBoxToPdf(
    text: String,
    box: OcrBoxPx,
    geometry: PdfPageGeometry
): OcrWordPlacement = OcrWordPlacement(
    text = text,
    x = geometry.x + box.left / geometry.renderScale,
    yBaseline = geometry.y + geometry.height - box.bottom / geometry.renderScale,
    widthPts = (box.right - box.left) / geometry.renderScale,
    heightPts = (box.bottom - box.top) / geometry.renderScale
)

/**
 * Escalado horizontal (operador `Tz` de PDF, en %) necesario para que el
 * ancho natural del texto a `fontSize` coincida con `targetWidthPts` --
 * así la palabra invisible ocupa exactamente el ancho de la palabra real
 * detectada por OCR, sin importar que la fuente estándar usada para la
 * capa invisible no sea la misma que la del documento escaneado. Función
 * pura, testeable.
 */
internal fun horizontalScalingPercent(naturalWidthPts: Float, targetWidthPts: Float): Float {
    if (naturalWidthPts <= 0f || targetWidthPts <= 0f) return 100f
    return (targetWidthPts / naturalWidthPts * 100f).coerceIn(1f, 500f)
}

class OcrPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OcrPdfUseCase"

        // 3.0x sobre el tamaño de página en puntos (72 dpi base) == ~216 dpi
        // de resolución real para el OCR -- suficiente para buena precisión
        // sin generar bitmaps desproporcionadamente pesados en memoria.
        private const val RENDER_SCALE = 3.0f
        private const val MIN_FONT_SIZE = 2f
        private const val MAX_FONT_SIZE = 200f

        // Rendering mode 3 del operador Tr de PDF == texto invisible
        // (se pinta pero no se muestra), el mecanismo estándar para capas
        // de texto buscable sobre un escaneo -- mismo principio que usan
        // Adobe Acrobat "Reconocer texto" u ocrmypdf.
        private const val TEXT_RENDERING_MODE_INVISIBLE = 3
    }

    /**
     * RF-PDF-15: agrega una capa de texto invisible sobre cada página de
     * un PDF escaneado, posicionada según el resultado real de OCR (ML
     * Kit Text Recognition, on-device), para que el texto quede
     * seleccionable/buscable sin alterar la apariencia visual del
     * escaneo original. No rasteriza ni reemplaza el contenido existente
     * de la página (RNF-PDF-01 en sentido inverso, mismo criterio que
     * Firmar/RF-PDF-11): solo **añade** contenido nuevo vía
     * `page.newContentStreamAfter()`. Páginas que ya tienen texto real
     * extraíble se omiten (evita una segunda capa de texto duplicada
     * sobre un PDF que no era en realidad un escaneo).
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        outputFileName: String? = null,
        messages      : OcrPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        // Declarado afuera del try (igual que cacheFile) para poder
        // borrarlo también si PdfRenderer(fd) lanza (ej. PDF con
        // contraseña de propietario, que PdfReader arriba sí puede abrir
        // pero PdfRenderer no) -- mismo escenario que motiva el hallazgo
        // #27, y sin esto quedaba huérfano por esa misma vía.
        var outputFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val output = createOutputFile(outputFileName ?: "OCR")
            outputFile = output
            val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            var processedPages = 0
            var totalWords = 0

            PdfDocument(PdfReader(cacheFile), PdfWriter(output)).use { pdf ->
                if (pdf.numberOfPages == 0) {
                    // Hallazgo real de la revisión general 2026-09-16 (#27):
                    // outputFile ya existía en disco (PdfWriter lo abre al
                    // construirse, arriba) -- este return salteaba el
                    // delete() que sí se hace más abajo para
                    // alreadyHasText/noTextFound, dejándolo huérfano.
                    output.delete()
                    return@withContext PdfToolResult.Error(messages.noPages)
                }
                val (pages, words) = ocrAllPages(pdf, cacheFile, recognizer, font)
                processedPages = pages
                totalWords = words
            }

            if (output.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }
            if (processedPages == 0) {
                output.delete()
                return@withContext PdfToolResult.Error(messages.alreadyHasText)
            }
            if (totalWords == 0) {
                output.delete()
                return@withContext PdfToolResult.Error(messages.noTextFound)
            }

            Timber.d("$TAG: OCR exitoso — $processedPages páginas, $totalWords palabras")

            PdfToolResult.Success(
                outputFile = output,
                message = String.format(messages.success, processedPages, totalWords)
            )
        } catch (e: CancellationException) {
            // Hallazgo real de la revisión adversarial de correctitud sobre
            // el fix de cancelación cooperativa (ensureActive() en
            // ocrAllPages()): CancellationException hereda de Exception, así
            // que sin este catch específico ANTES del genérico de abajo,
            // cada cancelación real (navegar hacia atrás) se registraba
            // como un error de OCR -- ruido falso en cualquier reporte de
            // fallos. Se relanza tal cual para no romper la propagación de
            // la cancelación (limpieza de outputFile/cacheFile igual vía
            // finally).
            outputFile?.delete()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al aplicar OCR")
            outputFile?.delete()
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    // Extraído de invoke() -- además de mantener la complejidad ciclomática
    // bajo el umbral de detekt, aísla el recorrido de páginas del hallazgo
    // #27 (cierre de fd/renderer) del resto de la máquina de estados.
    private suspend fun ocrAllPages(
        pdf: PdfDocument,
        cacheFile: File,
        recognizer: TextRecognizer,
        font: PdfFont
    ): Pair<Int, Int> {
        var processedPages = 0
        var totalWords = 0
        // Hallazgo real #27: si PdfRenderer(fd) lanzaba (ej. un PDF con
        // contraseña de propietario, que PdfRenderer no puede abrir aunque
        // PdfReader arriba sí) el fd ya abierto nunca se cerraba -- el
        // try/finally empezaba recién en la línea siguiente. .use{}
        // anidado cierra el fd pase lo que pase, incluida una excepción
        // del propio constructor de adentro.
        ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (pageNumber in 1..pdf.numberOfPages) {
                    // Hallazgo real de la auditoría general 2026-09-17
                    // (quinta pasada): sin ningún punto de suspensión en
                    // este bucle, cancelar la corrutina (ej. el usuario
                    // navega hacia atrás mientras corre el OCR) nunca se
                    // notaba hasta que las ~40 páginas terminaban solas en
                    // segundo plano -- el .pdf resultante quedaba huérfano,
                    // sin ninguna referencia en la UI.
                    coroutineContext.ensureActive()
                    val words = ocrOnePage(pdf, renderer, pageNumber, recognizer, font) ?: continue
                    totalWords += words
                    processedPages++
                }
            }
        }
        return processedPages to totalWords
    }

    // Extraído de ocrAllPages() -- baja la profundidad de anidamiento bajo
    // el umbral de detekt. Devuelve null si la página se saltea (ya tiene
    // texto real extraíble), o la cantidad de palabras reconocidas.
    private fun ocrOnePage(
        pdf: PdfDocument,
        renderer: PdfRenderer,
        pageNumber: Int,
        recognizer: TextRecognizer,
        font: PdfFont
    ): Int? {
        val page = pdf.getPage(pageNumber)
        if (PdfTextExtractor.getTextFromPage(page).isNotBlank()) return null

        val bitmap = renderPageBitmap(renderer, pageNumber - 1, RENDER_SCALE)
        val recognized = recognizeText(recognizer, bitmap)
        val words = drawInvisibleTextLayer(page, pdf, recognized, font, RENDER_SCALE)
        bitmap.recycle()
        return words
    }

    private fun renderPageBitmap(renderer: PdfRenderer, index: Int, scale: Float): Bitmap {
        val page = renderer.openPage(index)
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    private fun recognizeText(recognizer: TextRecognizer, bitmap: Bitmap): Text {
        val image = InputImage.fromBitmap(bitmap, 0)
        return Tasks.await(recognizer.process(image))
    }

    private fun drawInvisibleTextLayer(
        page: PdfPage,
        pdf: PdfDocument,
        text: Text,
        font: PdfFont,
        scale: Float
    ): Int {
        val pageSize: Rectangle = page.pageSize
        val geometry = PdfPageGeometry(pageSize.x, pageSize.y, pageSize.height, scale)
        val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdf)

        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }
            .mapNotNull { element ->
                val box = element.boundingBox
                if (box != null && element.text.isNotBlank()) element.text to box else null
            }
        words.forEach { (word, box) ->
            drawWord(canvas, font, word, OcrBoxPx(box.left, box.top, box.right, box.bottom), geometry)
        }
        return words.size
    }

    private fun drawWord(
        canvas: PdfCanvas,
        font: PdfFont,
        word: String,
        box: OcrBoxPx,
        geometry: PdfPageGeometry
    ) {
        val placement = mapOcrBoxToPdf(word, box, geometry)
        val fontSize = placement.heightPts.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        val naturalWidth = font.getWidth(word, fontSize)
        val scaling = horizontalScalingPercent(naturalWidth, placement.widthPts)

        canvas.saveState()
        canvas.beginText()
        canvas.setFontAndSize(font, fontSize)
        canvas.setTextRenderingMode(TEXT_RENDERING_MODE_INVISIBLE)
        canvas.setHorizontalScaling(scaling)
        canvas.setTextMatrix(placement.x, placement.yBaseline)
        canvas.showText(word)
        canvas.endText()
        canvas.restoreState()
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.pdf")
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
