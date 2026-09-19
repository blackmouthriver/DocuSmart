package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * `OcrPdfUseCase` en sí depende de `android.graphics.pdf.PdfRenderer` y de
 * ML Kit (Text Recognition), ambos solo disponibles en runtime Android
 * real -- no se puede ejercitar en un test unitario JVM puro (mismo límite
 * ya documentado para `CompressPdfUseCase`, ver docs/requirements/pdf-tools.md
 * §7). Lo que sí es JVM puro y merece cobertura real es la conversión de
 * coordenadas de un bounding box de OCR a puntos de PDF, y el cálculo de
 * escalado horizontal -- ambas son funciones internas sin dependencias de
 * Android/iText, extraídas específicamente para poder testearlas.
 */
class OcrPdfUseCaseTest {
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: OcrPdfUseCase

    private val messages =
        OcrPdfMessages(
            readError = "readError",
            noPages = "noPages",
            alreadyHasText = "alreadyHasText",
            noTextFound = "noTextFound",
            generateError = "generateError",
            success = "%1\$d paginas, %2\$d palabras",
            genericError = "error %1\$s",
        )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_ocr_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        useCase = OcrPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun resolverReturning(stream: () -> InputStream?): Uri {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } answers { stream() }
        every { context.contentResolver } returns resolver
        return uri
    }

    @Test
    fun `mapOcrBoxToPdf convierte un box en la esquina superior izquierda del bitmap`() {
        // Página de 100x200 puntos, renderizada a escala 2x (bitmap de 200x400 px).
        // Un box en (0,0)-(20,10) px queda en la esquina superior-izquierda visual.
        val placement =
            mapOcrBoxToPdf(
                text = "Hola",
                box = OcrBoxPx(left = 0, top = 0, right = 20, bottom = 10),
                geometry = PdfPageGeometry(x = 0f, y = 0f, height = 200f, renderScale = 2f),
            )

        // x = 0/2 = 0
        assertEquals(0f, placement.x, 0.001f)
        // yBaseline = 0 + 200 - 10/2 = 195 (cerca del techo de la página, como se espera)
        assertEquals(195f, placement.yBaseline, 0.001f)
        assertEquals(10f, placement.widthPts, 0.001f)
        assertEquals(5f, placement.heightPts, 0.001f)
    }

    @Test
    fun `mapOcrBoxToPdf convierte un box cerca del pie de la pagina`() {
        // Bitmap de 200x400 px (escala 2x de una página de 100x200 pts).
        // Box en (10,390)-(50,400) px -- pegado al borde inferior del bitmap.
        val placement =
            mapOcrBoxToPdf(
                text = "Pie",
                box = OcrBoxPx(left = 10, top = 390, right = 50, bottom = 400),
                geometry = PdfPageGeometry(x = 0f, y = 0f, height = 200f, renderScale = 2f),
            )

        assertEquals(5f, placement.x, 0.001f)
        // yBaseline = 200 - 400/2 = 0 (base de la página, como se espera)
        assertEquals(0f, placement.yBaseline, 0.001f)
        assertEquals(20f, placement.widthPts, 0.001f)
        assertEquals(5f, placement.heightPts, 0.001f)
    }

    @Test
    fun `mapOcrBoxToPdf respeta el origen de pagina cuando x e y no son cero`() {
        val placement =
            mapOcrBoxToPdf(
                text = "Offset",
                box = OcrBoxPx(left = 0, top = 0, right = 10, bottom = 10),
                geometry = PdfPageGeometry(x = 50f, y = 30f, height = 100f, renderScale = 1f),
            )

        assertEquals(50f, placement.x, 0.001f)
        assertEquals(30f + 100f - 10f, placement.yBaseline, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent devuelve 100 si el ancho natural coincide con el objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 40f, targetWidthPts = 40f)
        assertEquals(100f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent comprime el texto si el ancho natural es mayor al objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 80f, targetWidthPts = 40f)
        assertEquals(50f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent estira el texto si el ancho natural es menor al objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 20f, targetWidthPts = 40f)
        assertEquals(200f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent devuelve 100 sin dividir por cero con anchos invalidos`() {
        assertEquals(100f, horizontalScalingPercent(0f, 40f), 0.001f)
        assertEquals(100f, horizontalScalingPercent(40f, 0f), 0.001f)
        assertEquals(100f, horizontalScalingPercent(-5f, 40f), 0.001f)
    }

    @Test
    fun `horizontalScalingPercent se limita al rango 1 a 500 por ciento`() {
        assertEquals(500f, horizontalScalingPercent(naturalWidthPts = 1f, targetWidthPts = 1000f), 0.001f)
        assertEquals(1f, horizontalScalingPercent(naturalWidthPts = 1000f, targetWidthPts = 1f), 0.001f)
    }

    // ── copyUriToCache / camino de error de lectura (antes de tocar ML Kit) ──

    // Bug real corregido en la ronda 15: copyUriToCache() devolvia null dejando
    // el archivo (vacio o parcial) huerfano en cacheDir -- el `finally` de
    // invoke() solo borra cacheFile cuando la copia ya devolvio un File.
    @Test
    fun `copyUriToCache con un stream vacio devuelve null y no deja archivo en cache`() {
        val uri = resolverReturning { ByteArrayInputStream(ByteArray(0)) }

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "cache huerfano: ${cacheDir.listFiles()?.toList()}")
    }

    @Test
    fun `copyUriToCache con stream nulo devuelve null y no deja archivo en cache`() {
        val uri = resolverReturning { null }

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `copyUriToCache borra la copia parcial si el stream falla a mitad de lectura`() {
        val uri = resolverReturning { FailingAfterBytesStream(1024) }

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "cache parcial huerfano: ${cacheDir.listFiles()?.toList()}")
    }

    @Test
    fun `copyUriToCache copia el contenido completo cuando el stream es valido`() {
        val content = ByteArray(4000) { (it % 199).toByte() }
        val uri = resolverReturning { ByteArrayInputStream(content) }

        val copied = useCase.copyUriToCache(uri)

        assertTrue(copied != null && content.contentEquals(copied.readBytes()))
    }

    @Test
    fun `invoke devuelve Error de lectura y no deja cache si el PDF no se puede abrir`() =
        runTest {
            val uri = resolverReturning { ByteArrayInputStream(ByteArray(0)) }

            val result = useCase(uri, messages = messages)

            assertEquals(PdfToolResult.Error("readError"), result)
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        }

    @Test
    fun `una cancelacion leyendo el origen se propaga en vez de devolver Error`() =
        runTest {
            val uri = resolverReturning { throw CancellationException("cancelado") }

            var cancelled = false
            try {
                useCase(uri, messages = messages)
            } catch (e: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled, "la CancellationException debia propagarse")
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        }

    /** Entrega `remaining` bytes y despues lanza IOException, como un stream de red/SAF que se corta. */
    private class FailingAfterBytesStream(private var remaining: Int) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) throw IOException("stream cortado")
            remaining--
            return 7
        }
    }
}
