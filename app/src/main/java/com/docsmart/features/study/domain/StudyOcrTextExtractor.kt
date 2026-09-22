package com.docsmart.features.study.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import timber.log.Timber
import java.io.File
import kotlin.coroutines.coroutineContext

// Pedido explícito del usuario 2026-09-22: "si un PDF es escaneado la idea es
// que se extraiga el texto y también se pueda leer" -- un PDF escaneado no
// tiene capa de texto real, así que `extractPdfText()` (StudyScreen.kt) no
// encontraba ningún párrafo y Lectura mostraba "el PDF no tiene texto", sin
// nada que la voz pudiera leer. Reutiliza ML Kit Text Recognition on-device
// (mismo motor que ya usa OcrPdfUseCase para "Hacer buscable" en Herramientas
// PDF), pero acá NO se escribe ningún PDF nuevo -- solo se junta el texto
// reconocido en párrafos, igual que hace `groupPdfChunksIntoParagraphs()` con
// el texto real.
//
// Resolución de render más baja que el 3.0x/~216dpi de OcrPdfUseCase: acá el
// texto reconocido nunca se ve, solo se lee en voz alta, así que no hace
// falta la precisión de posición palabra por palabra que sí necesita esa
// capa invisible -- prioriza terminar más rápido en un documento largo.
private const val OCR_RENDER_SCALE = 2.0f

// Un bloque de texto de ML Kit (agrupación por párrafo visual que ya hace el
// propio reconocedor) más corto que esto es casi siempre ruido (un número de
// página suelto, una marca de agua) -- mismo umbral que ya usa
// `groupPdfChunksIntoParagraphs()` para el texto real.
private const val MIN_PARAGRAPH_LENGTH = 5

/**
 * OCR de página en página sobre un PDF ya copiado a `cacheFile` (el llamador
 * es dueño de crearlo/borrarlo, igual que con la extracción de texto real).
 * `onPageExtracted` se invoca después de cada página reconocida -- mismo
 * "procesamiento incremental" que ya tiene la extracción de texto real, para
 * que Lectura pueda empezar a leer desde la primera página lista sin esperar
 * el documento completo.
 */
internal suspend fun extractTextByOcr(
    cacheFile: File,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit,
): Pair<List<String>, List<Int>> {
    val paragraphs = mutableListOf<String>()
    val pageBoundaries = mutableListOf<Int>()
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                ocrAllPages(renderer, recognizer, paragraphs, pageBoundaries, onPageExtracted)
            }
        }
    } finally {
        // H3 de OcrPdfUseCase, mismo motor: el reconocedor nativo queda
        // huérfano si no se cierra explícitamente.
        recognizer.close()
    }
    return paragraphs.toList() to pageBoundaries.toList()
}

// Extraído de extractTextByOcr() -- mismo motivo que ocrAllPages() en
// OcrPdfUseCase (detekt: NestedBlockDepth, dos `use{}` anidados + el loop).
private suspend fun ocrAllPages(
    renderer: PdfRenderer,
    recognizer: TextRecognizer,
    paragraphs: MutableList<String>,
    pageBoundaries: MutableList<Int>,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit,
) {
    for (index in 0 until renderer.pageCount) {
        // Mismo patrón que OcrPdfUseCase (hallazgo #27 de esa herramienta): un
        // punto de suspensión por página para que cancelar (navegar hacia
        // atrás a mitad del OCR) se note de verdad, en vez de seguir
        // corriendo en segundo plano.
        coroutineContext.ensureActive()
        paragraphs.addAll(ocrOnePage(renderer, index, recognizer))
        pageBoundaries.add(paragraphs.size)
        if (paragraphs.isNotEmpty()) onPageExtracted(paragraphs.toList(), pageBoundaries.toList())
    }
}

private fun ocrOnePage(
    renderer: PdfRenderer,
    index: Int,
    recognizer: TextRecognizer,
): List<String> {
    val bitmap =
        renderer.openPage(index).use { page ->
            val width = (page.width * OCR_RENDER_SCALE).toInt().coerceAtLeast(1)
            val height = (page.height * OCR_RENDER_SCALE).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    return try {
        val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        // Un bloque de ML Kit ya agrupa líneas que van juntas -- se trata como
        // un párrafo, igual que un párrafo real de `groupPdfChunksIntoParagraphs`.
        recognized.textBlocks
            .map { it.text.replace('\n', ' ').trim() }
            .filter { it.length > MIN_PARAGRAPH_LENGTH }
            .flatMap { splitForSpeech(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e("Estudio: error de OCR en la página ${index + 1} (${e.javaClass.simpleName})")
        emptyList()
    } finally {
        bitmap.recycle()
    }
}
