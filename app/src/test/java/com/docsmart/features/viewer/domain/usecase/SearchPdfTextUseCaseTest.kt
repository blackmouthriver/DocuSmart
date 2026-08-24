package com.docsmart.features.viewer.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
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
 * Cubre el bug real encontrado en el Visor (docs/requirements/visor-biblioteca.md):
 * el botón de búsqueda aparecía habilitado para PDF pero `PdfViewerContent` no
 * recibía ningún `searchQuery` — no hacía nada. Este use case reemplaza ese
 * hueco extrayendo texto por página con iText7 y devolviendo las páginas con
 * coincidencias.
 */
class SearchPdfTextUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: SearchPdfTextUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_search_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        useCase = SearchPdfTextUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `busca solo en las paginas que contienen el texto`() = runTest {
        stubResolver(createTestPdf(listOf("Contrato de arrendamiento", "Cláusula de pago", "Firma y fecha")))

        val matches = useCase(contentUri(), "pago")

        assertEquals(listOf(2), matches)
    }

    @Test
    fun `la busqueda no distingue mayusculas de minusculas`() = runTest {
        stubResolver(createTestPdf(listOf("Documento Confidencial")))

        val matches = useCase(contentUri(), "confidencial")

        assertEquals(listOf(1), matches)
    }

    @Test
    fun `devuelve todas las paginas cuando el texto aparece en varias`() = runTest {
        stubResolver(createTestPdf(listOf("factura número 1", "resumen", "factura número 2")))

        val matches = useCase(contentUri(), "factura")

        assertEquals(listOf(1, 3), matches)
    }

    @Test
    fun `sin coincidencias devuelve lista vacia`() = runTest {
        stubResolver(createTestPdf(listOf("texto sin relación")))

        val matches = useCase(contentUri(), "inexistente")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `query en blanco devuelve lista vacia sin tocar el archivo`() = runTest {
        val matches = useCase(mockk<Uri>(), "   ")

        assertTrue(matches.isEmpty())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun contentUri(): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        return uri
    }

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestPdf(pagesText: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val document = Document(pdfDoc)
        pagesText.forEachIndexed { index, text ->
            document.add(Paragraph(text))
            if (index < pagesText.size - 1) document.add(AreaBreak())
        }
        document.close()
        return out.toByteArray()
    }
}
