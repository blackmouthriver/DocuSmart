package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.font.PdfFontFactory
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * RF-PDF-14/HU-PDF-09. El PDF de prueba tiene "SECRETO" cerca del borde
 * superior de la página (y=700 en un LETTER de 792 de alto) y "PUBLICO"
 * cerca del borde inferior (y=50) — al censurar solo la franja superior
 * (yFrac=0, hFrac=0.3) se verifica que "SECRETO" deja de poder extraerse
 * del PDF resultante (censura real, no un rectángulo negro visual encima
 * de texto que sigue ahí) mientras "PUBLICO" se conserva intacto.
 */
class RedactPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: RedactPdfUseCase

    private val messages = RedactPdfMessages(
        emptyRectsError = "emptyRectsError", readError = "readError", noPages = "noPages",
        generateError = "generateError", success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_redact_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_redact_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = RedactPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `censurar la franja superior elimina el texto secreto y conserva el texto publico`() = runTest {
        stubResolver(createTestPdf())
        val rect = RedactionRect(pageNumber = 1, xFrac = 0f, yFrac = 0f, wFrac = 1f, hFrac = 0.3f)

        val result = useCase(mockk<Uri>(), rects = listOf(rect), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val text = pageTextOf((result as PdfToolResult.Success).outputFile)
        assertFalse(text.contains("SECRETO"))
        assertTrue(text.contains("PUBLICO"))
    }

    @Test
    fun `censurar sin marcar ninguna zona devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), rects = emptyList(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptyRectsError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `una zona en una pagina fuera de rango se ignora sin fallar`() = runTest {
        stubResolver(createTestPdf())
        val rect = RedactionRect(pageNumber = 5, xFrac = 0f, yFrac = 0f, wFrac = 1f, hFrac = 0.3f)

        val result = useCase(mockk<Uri>(), rects = listOf(rect), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val text = pageTextOf((result as PdfToolResult.Success).outputFile)
        assertTrue(text.contains("SECRETO"))
        assertTrue(text.contains("PUBLICO"))
    }

    @Test
    fun `el mensaje de exito informa el numero de zonas censuradas`() = runTest {
        stubResolver(createTestPdf())
        val rects = listOf(
            RedactionRect(1, 0f, 0f, 1f, 0.3f),
            RedactionRect(1, 0f, 0.8f, 1f, 0.2f)
        )

        val result = useCase(mockk<Uri>(), rects = rects, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 2", (result as PdfToolResult.Success).message)
    }

    @Test
    fun `censurar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())
        val rect = RedactionRect(1, 0f, 0f, 1f, 0.3f)

        val result = useCase(mockk<Uri>(), rects = listOf(rect), messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        val page = pdfDoc.addNewPage()
        val canvas = PdfCanvas(page)
        canvas.beginText().setFontAndSize(font, 24f).moveText(50.0, 700.0).showText("SECRETO").endText()
        canvas.beginText().setFontAndSize(font, 24f).moveText(50.0, 50.0).showText("PUBLICO").endText()
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageTextOf(file: File): String {
        val pdf = PdfDocument(PdfReader(file))
        val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
        pdf.close()
        return text
    }
}
