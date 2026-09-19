package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
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
 * Ronda 14 de la auditoría del Convertidor: este use case tenía 0% de
 * cobertura pese a que su código ya incorpora los mismos fixes que
 * PdfToTextUseCase/PdfToWordUseCase (CancellationException relanzada, catch
 * de OutOfMemoryError, `.use{}` sobre el PdfDocument, cacheFile borrado en
 * `finally`). Los tests de abajo verifican ese comportamiento y, en
 * particular, el escapado HTML del texto extraído (si una futura regresión
 * quita el `.replace("<", "&lt;")`, un PDF con texto como "<script>" o
 * "A & B" rompería el HTML generado en vez de mostrarse como texto plano).
 */
class PdfToHtmlUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: PdfToHtmlUseCase

    private lateinit var cacheDir: File

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_pdftohtml_files_").toFile()
        cacheDir = Files.createTempDirectory("docsmart_pdftohtml_cache_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        every { context.getString(any(), any()) } returns "Página N"
        useCase = PdfToHtmlUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    @Test
    fun `extrae el texto real de un PDF de varias paginas y arma un HTML por pagina`() = runTest {
        stubResolver(createPdf(listOf("Primera página", "Segunda página")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val success = result as ConversionResult.Success
        assertEquals("html", success.outputFile.extension)
        assertEquals(2, success.pageCount)
        val html = success.outputFile.readText()
        assertTrue(html.contains("<p>Primera página</p>"))
        assertTrue(html.contains("<p>Segunda página</p>"))
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("</html>"))
    }

    // Si esta escapada se rompe, texto de usuario como "<script>" quedaría
    // interpretado como una etiqueta real por cualquier visor de HTML en vez
    // de mostrarse como texto plano.
    @Test
    fun `escapa caracteres especiales HTML del texto extraido del PDF`() = runTest {
        stubResolver(createPdf(listOf("<script>A & B</script>")))

        val result = useCase(mockk<Uri>(), "salida")

        val html = (result as ConversionResult.Success).outputFile.readText()
        assertTrue(html.contains("&lt;script&gt;A &amp; B&lt;/script&gt;"))
        assertFalse(html.contains("<script>A & B</script>"))
    }

    @Test
    fun `paginas en blanco se omiten del HTML pero las paginas con texto real se conservan`() = runTest {
        stubResolver(createPdf(listOf("Con contenido", "   ", "")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val html = (result as ConversionResult.Success).outputFile.readText()
        assertTrue(html.contains("Con contenido"))
        // Solo una página real -- las 2 páginas en blanco no generan <div class="page">.
        assertEquals(1, Regex("class=\"page\"").findAll(html).count())
    }

    @Test
    fun `PDF sin texto extraible devuelve Error`() = runTest {
        stubResolver(createPdf(emptyList()))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Error)
    }

    @Test
    fun `archivo no legible devuelve Error`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase(uri, "salida")

        assertTrue(result is ConversionResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdf(pageLines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        pageLines.forEach { line ->
            val page = pdfDoc.addNewPage()
            if (line.isNotBlank()) {
                val canvas = PdfCanvas(page)
                canvas.beginText().setFontAndSize(font, 12f).moveText(50.0, 700.0)
                canvas.showText(line)
                canvas.endText()
            }
        }
        if (pageLines.isEmpty()) pdfDoc.addNewPage()
        pdfDoc.close()
        return out.toByteArray()
    }
}
