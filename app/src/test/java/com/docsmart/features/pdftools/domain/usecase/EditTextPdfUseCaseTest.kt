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
 * RF-PDF-10/HU-PDF-11. No es solo cosmético: localiza el texto por
 * posición real (`RegexBasedLocationExtractionStrategy`) y lo elimina de
 * verdad (`PdfCleaner`) antes de escribir el reemplazo, así que se
 * verifica con `PdfTextExtractor` que el texto original ya no aparece en
 * el PDF de salida, no solo que "algo se dibujó encima".
 */
class EditTextPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: EditTextPdfUseCase

    private val messages = EditTextPdfMessages(
        emptySearchError = "emptySearchError", readError = "readError", noPages = "noPages",
        noMatchesError = "noMatchesError", generateError = "generateError",
        success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_edittext_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_edittext_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = EditTextPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `reemplaza el texto encontrado y elimina el original de verdad`() = runTest {
        stubResolver(createPdf(listOf("Hola Mundo Antiguo")))

        val result = useCase(mockk<Uri>(), searchText = "Antiguo", replaceText = "Nuevo", messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val text = pageTextOf((result as PdfToolResult.Success).outputFile)
        assertFalse(text.contains("Antiguo"))
        assertTrue(text.contains("Nuevo"))
    }

    @Test
    fun `busqueda vacia devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), searchText = "", replaceText = "x", messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptySearchError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `texto no encontrado devuelve Error especifico`() = runTest {
        stubResolver(createPdf(listOf("Contenido de la pagina")))

        val result = useCase(mockk<Uri>(), searchText = "NoExiste", replaceText = "x", messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("noMatchesError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `todas las ocurrencias en la pagina se reemplazan y el mensaje informa el total`() = runTest {
        stubResolver(createPdf(listOf("Gato", "Perro Gato", "Gato Pajaro")))

        val result = useCase(mockk<Uri>(), searchText = "Gato", replaceText = "Leon", messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 3", (result as PdfToolResult.Success).message)
        val text = pageTextOf(result.outputFile)
        assertFalse(text.contains("Gato"))
    }

    @Test
    fun `editar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), searchText = "algo", replaceText = "x", messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdf(lines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        val page = pdfDoc.addNewPage()
        val canvas = PdfCanvas(page)
        lines.forEachIndexed { index, line ->
            canvas.beginText()
                .setFontAndSize(font, 18f)
                .moveText(50.0, (700 - index * 40).toDouble())
                .showText(line)
                .endText()
        }
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
