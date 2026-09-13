package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * RF-PDF-07/HU-PDF-06 (docs/requirements/pdf-tools.md §11). Verifica que el
 * texto de marca de agua queda escrito como texto real (extraíble con
 * PdfTextExtractor pese a estar rotado/semitransparente) en cada página,
 * mismo patrón ya usado en NumberPagesUseCaseTest.
 */
class WatermarkPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: WatermarkPdfUseCase

    private val messages = WatermarkMessages(
        emptyTextError = "emptyTextError", readError = "readError", noPages = "noPages",
        generateError = "generateError", success = "success %1\$d",
        genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_watermark_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_watermark_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = WatermarkPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `la marca de agua queda escrita como texto real en cada pagina`() = runTest {
        stubResolver(createTestPdf(pages = 3))

        val result = useCase(mockk<Uri>(), watermarkText = "CONFIDENCIAL", messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(3, texts.size)
        texts.forEach { assertTrue(it.contains("CONFIDENCIAL"), "página sin marca de agua: '$it'") }
    }

    @Test
    fun `conserva el total de paginas del original`() = runTest {
        stubResolver(createTestPdf(pages = 5))

        val result = useCase(mockk<Uri>(), watermarkText = "Borrador", messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals(5, pageCountOf((result as PdfToolResult.Success).outputFile))
    }

    @Test
    fun `texto de marca de agua vacio devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), watermarkText = "   ", messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptyTextError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `marca de agua sobre un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), watermarkText = "Borrador", messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // Bug real reportado por testers 2026-09-13: "no se ve la marca de agua"
    // -- el test anterior solo verificaba que el texto fuera extraíble, lo
    // cual pasaba incluso con 0.15 de opacidad (casi invisible). Este test
    // habría fallado con el valor viejo si alguna vez se hubiera bajado por
    // error, y sigue protegiendo contra que alguien lo vuelva a bajar sin
    // darse cuenta de que queda casi blanco sobre blanco.
    @Test
    fun `la opacidad de la marca de agua es suficiente para verse`() {
        assertTrue(
            WatermarkPdfUseCase.WATERMARK_OPACITY >= 0.25f,
            "opacidad demasiado baja para verse con claridad: ${WatermarkPdfUseCase.WATERMARK_OPACITY}"
        )
    }

    // Bug real reportado por testers 2026-09-13: la marca de agua se
    // centraba respecto al origen (0,0) en vez del centro real de la
    // página -- invisible en cualquier PDF cuyo MediaBox no arranque en
    // (0,0) (común en escaneos o en un PDF ya procesado por Recortar/Rotar).
    @Test
    fun `la marca de agua queda dentro del area visible aunque el MediaBox no arranque en cero`() = runTest {
        stubResolver(createTestPdfWithOffsetMediaBox())

        val result = useCase(mockk<Uri>(), watermarkText = "CONFIDENCIAL", messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val file = (result as PdfToolResult.Success).outputFile
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val page = pdf.getPage(1)
        val pageSize = page.pageSize

        val strategy = RegexBasedLocationExtractionStrategy("CONFIDENCIAL")
        PdfCanvasProcessor(strategy).processPageContent(page)
        val locations = strategy.resultantLocations
        pdf.close()

        assertTrue(locations.isNotEmpty(), "no se encontró la marca de agua en la página")
        val rect = locations.first().rectangle
        assertTrue(
            rect.left >= pageSize.left && rect.right <= pageSize.right,
            "marca de agua fuera del ancho visible: texto=$rect página=$pageSize"
        )
        assertTrue(
            rect.bottom >= pageSize.bottom && rect.top <= pageSize.top,
            "marca de agua fuera del alto visible: texto=$rect página=$pageSize"
        )
    }

    @Test
    fun `texto largo no lanza excepcion, se ajusta el tamano de fuente`() = runTest {
        stubResolver(createTestPdf(pages = 1))
        val longText = "Este es un texto de marca de agua bastante largo para forzar el ajuste"

        val result = useCase(mockk<Uri>(), watermarkText = longText, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertTrue(texts.first().contains(longText))
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestPdf(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        repeat(pages) { pdfDoc.addNewPage() }
        pdfDoc.close()
        return out.toByteArray()
    }

    // MediaBox desplazado lejos de (0,0) -- reproduce el caso real que
    // exponía el bug de posicionamiento (escaneos, o un PDF que ya pasó por
    // Recortar/Rotar). El offset es grande a propósito: con el cálculo viejo
    // (sin sumar pageSize.left/bottom) la marca de agua habría quedado
    // centrada en (297,421), muy lejos del área visible real
    // ([1000,1595]x[1500,2342]) -- cualquier ambigüedad de redondeo queda
    // descartada.
    private fun createTestPdfWithOffsetMediaBox(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val page = pdfDoc.addNewPage()
        page.setMediaBox(Rectangle(1000f, 1500f, 595f, 842f))
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageCountOf(file: File): Int {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val count = pdf.numberOfPages
        pdf.close()
        return count
    }

    private fun pageTextsOf(file: File): List<String> {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val texts = (1..pdf.numberOfPages).map {
            PdfTextExtractor.getTextFromPage(pdf.getPage(it)).trim()
        }
        pdf.close()
        return texts
    }
}
