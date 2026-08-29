package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfFormField
import com.itextpdf.kernel.geom.Rectangle
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
 * RF-PDF-12/HU-PDF-13. Tras `flattenFields()` el valor rellenado se
 * convierte en contenido de página normal, así que se verifica con
 * `PdfTextExtractor` que el valor realmente quedó escrito en el PDF de
 * salida, no solo que el mensaje de éxito lo dice.
 */
class FillFormUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: FillFormUseCase

    private val messages = FillFormMessages(
        emptyValuesError = "emptyValuesError", readError = "readError", noFieldsError = "noFieldsError",
        generateError = "generateError", success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_fillform_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_fillform_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = FillFormUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `rellenar campos existentes escribe los valores de verdad en el PDF de salida`() = runTest {
        stubResolver(createPdfWithForm())
        val values = mapOf("nombre" to "Ana Torres", "email" to "ana@ejemplo.com")

        val result = useCase(mockk<Uri>(), values = values, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 2", (result as PdfToolResult.Success).message)
        val text = pageTextOf(result.outputFile)
        assertTrue(text.contains("Ana Torres"))
        assertTrue(text.contains("ana@ejemplo.com"))
    }

    @Test
    fun `valores vacios devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), values = emptyMap(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptyValuesError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `un PDF sin AcroForm devuelve Error especifico`() = runTest {
        stubResolver(createPdfWithoutForm())

        val result = useCase(mockk<Uri>(), values = mapOf("nombre" to "Ana"), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("noFieldsError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `nombres de campo que no existen en el formulario no rellenan nada y devuelven Error`() = runTest {
        stubResolver(createPdfWithForm())

        val result = useCase(mockk<Uri>(), values = mapOf("campoInexistente" to "x"), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("noFieldsError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `rellenar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), values = mapOf("nombre" to "Ana"), messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdfWithForm(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val page = pdfDoc.addNewPage()
        val form = PdfAcroForm.getAcroForm(pdfDoc, true)
        val nameField = PdfFormField.createText(pdfDoc, Rectangle(50f, 700f, 200f, 30f), "nombre", "")
        val emailField = PdfFormField.createText(pdfDoc, Rectangle(50f, 650f, 200f, 30f), "email", "")
        form.addField(nameField, page)
        form.addField(emailField, page)
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun createPdfWithoutForm(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        pdfDoc.addNewPage()
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
