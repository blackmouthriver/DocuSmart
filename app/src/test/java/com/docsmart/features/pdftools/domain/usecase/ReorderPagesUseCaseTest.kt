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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * RF-PDF-08/HU-PDF-07 (docs/requirements/pdf-tools.md §12). El PDF de
 * prueba tiene una etiqueta de texto distinta por página ("PAGINA_1",
 * "PAGINA_2", ...) para poder verificar no solo el conteo de páginas del
 * resultado sino que el **contenido correcto** terminó en cada posición
 * tras reordenar/eliminar -- mismo espíritu que NumberPagesUseCaseTest,
 * pero acá el orden de origen de cada página es lo que se está probando.
 */
class ReorderPagesUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ReorderPagesUseCase

    private val messages = ReorderPagesMessages(
        emptyOrderError = "emptyOrderError", readError = "readError",
        generateError = "generateError", success = "success %1\$d",
        genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_reorder_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_reorder_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = ReorderPagesUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `reordenar sin eliminar refleja el nuevo orden en el resultado`() = runTest {
        stubResolver(createLabeledPdf(pages = 3))

        val result = useCase(mockk<Uri>(), pageOrder = listOf(3, 1, 2), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("PAGINA_3", "PAGINA_1", "PAGINA_2"), texts)
    }

    @Test
    fun `omitir una pagina de la lista la elimina del resultado`() = runTest {
        stubResolver(createLabeledPdf(pages = 5))

        // Se omiten las páginas 2 y 4 -- quedan solo 1, 3, 5 en ese orden.
        val result = useCase(mockk<Uri>(), pageOrder = listOf(1, 3, 5), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("PAGINA_1", "PAGINA_3", "PAGINA_5"), texts)
    }

    @Test
    fun `reordenar y eliminar a la vez produce el resultado combinado correcto`() = runTest {
        stubResolver(createLabeledPdf(pages = 4))

        // Se elimina la página 2, y las 3 restantes quedan en orden 4,1,3.
        val result = useCase(mockk<Uri>(), pageOrder = listOf(4, 1, 3), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf("PAGINA_4", "PAGINA_1", "PAGINA_3"), texts)
    }

    @Test
    fun `lista de orden vacia devuelve Error sin tocar el archivo`() = runTest {
        val result = useCase(mockk<Uri>(), pageOrder = emptyList(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("emptyOrderError", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `reordenar un archivo que no es un PDF valido devuelve Error`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), pageOrder = listOf(1), messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createLabeledPdf(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        repeat(pages) { index ->
            val page = pdfDoc.addNewPage()
            val canvas = PdfCanvas(page)
            canvas.beginText()
                .setFontAndSize(font, 24f)
                .moveText(50.0, 700.0)
                .showText("PAGINA_${index + 1}")
                .endText()
        }
        pdfDoc.close()
        return out.toByteArray()
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
