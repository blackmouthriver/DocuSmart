package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
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
import java.util.Base64

/**
 * RF-PDF-11/HU-PDF-12. No hay infraestructura de certificados/PKI en el
 * proyecto, así que "firma digital" se implementa como firma manuscrita
 * (imagen capturada del trazo del usuario) estampada sobre la página --
 * se verifica que la operación produce un archivo real no vacío y que el
 * número de página firmada queda correctamente acotado al total de
 * páginas del documento.
 */
class SignPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: SignPdfUseCase

    private val messages = SignPdfMessages(
        emptySignatureError = "emptySignatureError", readError = "readError", noPages = "noPages",
        generateError = "generateError", success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_sign_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_sign_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = SignPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `firmar un PDF de una pagina produce un archivo no vacio en la pagina 1`() = runTest {
        stubResolver(createPdf(pages = 1))

        val result = useCase(
            mockk<Uri>(), signatureImageBytes = createSignaturePng(), pageNumber = 1, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 1", (result as PdfToolResult.Success).message)
        assertTrue(result.outputFile.length() > 0L)
    }

    @Test
    fun `firma sin imagen devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), signatureImageBytes = ByteArray(0), pageNumber = 1, messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptySignatureError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `un numero de pagina fuera de rango se ajusta a la ultima pagina valida`() = runTest {
        stubResolver(createPdf(pages = 2))

        val result = useCase(
            mockk<Uri>(), signatureImageBytes = createSignaturePng(), pageNumber = 99, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 2", (result as PdfToolResult.Success).message)
    }

    @Test
    fun `un numero de pagina menor a 1 se ajusta a la primera pagina`() = runTest {
        stubResolver(createPdf(pages = 3))

        val result = useCase(
            mockk<Uri>(), signatureImageBytes = createSignaturePng(), pageNumber = 0, messages = messages
        )

        assertTrue(result is PdfToolResult.Success)
        assertEquals("success 1", (result as PdfToolResult.Success).message)
    }

    @Test
    fun `firmar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(
            mockk<Uri>(), signatureImageBytes = createSignaturePng(), pageNumber = 1, messages = messages
        )

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdf(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        repeat(pages) { index ->
            val page = pdfDoc.addNewPage()
            val canvas = PdfCanvas(page)
            canvas.beginText().setFontAndSize(font, 18f).moveText(50.0, 700.0).showText("PAGINA_${index + 1}").endText()
        }
        pdfDoc.close()
        return out.toByteArray()
    }

    // PNG 100x40 con un trazo diagonal simple, generado fuera de línea (con
    // Python + zlib) y embebido en base64 -- java.awt/ImageIO no está
    // disponible en el classpath de tests unitarios de Android, así que no
    // se puede generar en tiempo de ejecución con las APIs estándar de la
    // JVM.
    @Suppress("MaxLineLength")
    private fun createSignaturePng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAGQAAAAoCAIAAACHGsgUAAAAmklEQVR4nO3WIRLEQBDDQP//03c8mwVCjqs0sIcYKkl+x4mv2F8whM/fR2Z9E3N7iCfm9hBPzO0hvmB/wQ6mvmAI+wuG0HQAaDoANB0Amg6mg+lQx/6CITQdAJoOAE0HgKaD6WA61LG/YAhNB4CmA0DTAaDpYDqYDnXsLxhC0wGg6QDQdABoOpgOpkMd+wuG0HQAaDoANB0A/gH4oAkqruU2LwAAAABJRU5ErkJggg=="
    )
}
