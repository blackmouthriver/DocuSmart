package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
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
 * RF-PDF-06/HU-PDF-05 (docs/requirements/pdf-tools.md §9). Verifica que
 * cada página del resultado lleva su número en el pie escrito como texto
 * real (no una imagen) -- se comprueba extrayendo el texto de cada página
 * de salida con PdfTextExtractor, mismo patrón ya usado en
 * PptToPdfUseCaseTest.
 */
class NumberPagesUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: NumberPagesUseCase

    private val messages = NumberPagesMessages(
        readError = "readError", noPages = "noPages", generateError = "generateError",
        success = "success %1\$d", genericError = "genericError %1\$s",
        pageOfTotalTemplate = "Página %1\$d de %2\$d"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_numberpages_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_numberpages_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = NumberPagesUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `formato PAGE_OF_TOTAL escribe Pagina X de N en cada pagina`() = runTest {
        stubResolver(createTestPdf(pages = 3))

        val result = useCase(
            mockk<Uri>(), format = PageNumberFormat.PAGE_OF_TOTAL, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("Página 1 de 3", "Página 2 de 3", "Página 3 de 3"), texts)
    }

    @Test
    fun `formato NUMBER_ONLY escribe solo el numero de pagina`() = runTest {
        stubResolver(createTestPdf(pages = 2))

        val result = useCase(
            mockk<Uri>(), format = PageNumberFormat.NUMBER_ONLY, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("1", "2"), texts)
    }

    @Test
    fun `formato NUMBER_OF_TOTAL escribe numero y total`() = runTest {
        stubResolver(createTestPdf(pages = 2))

        val result = useCase(
            mockk<Uri>(), format = PageNumberFormat.NUMBER_OF_TOTAL, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("1 / 2", "2 / 2"), texts)
    }

    @Test
    fun `numerar conserva el total de paginas del original`() = runTest {
        stubResolver(createTestPdf(pages = 5))

        val result = useCase(
            mockk<Uri>(), format = PageNumberFormat.NUMBER_ONLY, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        assertEquals(5, pageCountOf((result as PdfToolResult.Success).outputFile))
    }

    @Test
    fun `numerar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
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
