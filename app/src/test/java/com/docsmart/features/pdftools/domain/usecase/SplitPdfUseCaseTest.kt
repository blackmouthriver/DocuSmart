package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
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
 * Verifica RF-SEC/RF-PDF-02 (docs/requirements/pdf-tools.md): la QA de mayo
 * reportaba "dividir genera el mismo PDF sin dividir" — estos tests
 * confirman con un PDF real de 5 páginas que el archivo de salida
 * efectivamente contiene solo el rango pedido.
 */
class SplitPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: SplitPdfUseCase

    private val messages = SplitPdfMessages(
        readError = "readError", noPages = "noPages",
        rangeTooSmall = "rangeTooSmall %1\$d", generateError = "generateError",
        success = "success %1\$d %2\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_split_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_split_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = SplitPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `split extrae solo el rango pedido, no el documento completo`() = runTest {
        stubResolver(createTestPdf(pages = 5))

        val result = useCase(mockk<Uri>(), messages = messages, fromPage = 2, toPage = 4)

        assertTrue(result is PdfToolResult.Success)
        val outputFile = (result as PdfToolResult.Success).outputFile
        assertEquals(3, pageCountOf(outputFile))
    }

    @Test
    fun `split con rango completo produce el mismo numero de paginas que el original`() = runTest {
        stubResolver(createTestPdf(pages = 5))

        val result = useCase(mockk<Uri>(), messages = messages, fromPage = 1, toPage = 5)

        assertTrue(result is PdfToolResult.Success)
        assertEquals(5, pageCountOf((result as PdfToolResult.Success).outputFile))
    }

    @Test
    fun `split con rango fuera de limites se ajusta al total de paginas`() = runTest {
        stubResolver(createTestPdf(pages = 3))

        val result = useCase(mockk<Uri>(), messages = messages, fromPage = 2, toPage = 999)

        assertTrue(result is PdfToolResult.Success)
        assertEquals(2, pageCountOf((result as PdfToolResult.Success).outputFile))
    }

    @Test
    fun `split de un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), messages = messages, fromPage = 1, toPage = 2)

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
}
