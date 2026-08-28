package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
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
 * RF-PDF-09. El recorte reduce `MediaBox`/`CropBox` (mismo principio que
 * RF-PDF-04/Rotar: no se toca el content stream), así que se verifica el
 * nuevo tamaño de página del PDF de salida contra el tamaño original de
 * LETTER (612x792), y que el texto de la página sigue siendo extraíble
 * pese a haberse reducido el área visible.
 */
class CropPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: CropPdfUseCase

    private val messages = CropPdfMessages(
        readError = "readError", noPages = "noPages", generateError = "generateError",
        success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_crop_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_crop_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = CropPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `recortar con margen del 10 por ciento reduce el tamano de pagina proporcionalmente`() = runTest {
        stubResolver(createLabeledPdf())

        val result = useCase(mockk<Uri>(), marginPercent = 10, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val size = pageSizeOf((result as PdfToolResult.Success).outputFile)
        // LETTER = 612x792 -- 10% de margen a cada lado quita 20% del ancho/alto total
        assertEquals(612f * 0.8f, size.width, 0.5f)
        assertEquals(792f * 0.8f, size.height, 0.5f)
    }

    @Test
    fun `el texto de la pagina sigue siendo extraible tras recortar`() = runTest {
        stubResolver(createLabeledPdf())

        val result = useCase(mockk<Uri>(), marginPercent = 10, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val text = pageTextOf((result as PdfToolResult.Success).outputFile)
        assertTrue(text.contains("CONTENIDO_PAGINA"))
    }

    @Test
    fun `margen de 0 por ciento no cambia el tamano de la pagina`() = runTest {
        stubResolver(createLabeledPdf())

        val result = useCase(mockk<Uri>(), marginPercent = 0, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val size = pageSizeOf((result as PdfToolResult.Success).outputFile)
        assertEquals(612f, size.width, 0.5f)
        assertEquals(792f, size.height, 0.5f)
    }

    @Test
    fun `un margen fuera de rango se ajusta al maximo permitido sin generar un rectangulo invalido`() = runTest {
        stubResolver(createLabeledPdf())

        val result = useCase(mockk<Uri>(), marginPercent = 90, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 40", (result as PdfToolResult.Success).message)
        val size = pageSizeOf(result.outputFile)
        assertTrue(size.width > 0f)
        assertTrue(size.height > 0f)
    }

    @Test
    fun `recortar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), marginPercent = 10, messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createLabeledPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        val page = pdfDoc.addNewPage(PageSize.LETTER)
        val canvas = PdfCanvas(page)
        canvas.beginText().setFontAndSize(font, 24f).moveText(50.0, 400.0).showText("CONTENIDO_PAGINA").endText()
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageSizeOf(file: File): com.itextpdf.kernel.geom.Rectangle {
        val pdf = PdfDocument(PdfReader(file))
        val size = pdf.getPage(1).pageSize
        pdf.close()
        return size
    }

    private fun pageTextOf(file: File): String {
        val pdf = PdfDocument(PdfReader(file))
        val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
        pdf.close()
        return text
    }
}
