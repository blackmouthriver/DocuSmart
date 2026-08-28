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
 * RF-PDF-13/HU-PDF-08. Verifica que la comparación es por contenido de
 * texto real por página (no un simple hash de igualdad): páginas idénticas
 * no se cuentan como distintas, líneas exclusivas de un documento aparecen
 * en el reporte generado, y una página que solo existe en uno de los dos
 * PDFs se marca como tal en vez de comparar texto contra "nada".
 */
class ComparePdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ComparePdfUseCase

    private val messages = ComparePdfMessages(
        readErrorA = "readErrorA", readErrorB = "readErrorB",
        generateError = "generateError", identical = "identical",
        differencesFound = "differencesFound %1\$d %2\$d",
        genericError = "genericError %1\$s",
        reportTitle = "reportTitle",
        reportPageHeader = "reportPageHeader %1\$d",
        reportPageOnlyInA = "reportPageOnlyInA",
        reportPageOnlyInB = "reportPageOnlyInB",
        reportOnlyInALine = "reportOnlyInALine %1\$s",
        reportOnlyInBLine = "reportOnlyInBLine %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_compare_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_compare_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = ComparePdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `documentos identicos producen el mensaje de identicos`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        val bytes = createPdf(listOf(listOf("Hola mundo"), listOf("Segunda pagina")))
        stubResolver(uriA, bytes, uriB, bytes)

        val result = useCase(uriA, uriB, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("identical", (result as PdfToolResult.Success).message)
    }

    @Test
    fun `una linea distinta en una pagina compartida aparece en el reporte y en el conteo`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        stubResolver(
            uriA, createPdf(listOf(listOf("TextoUnicoEnA"))),
            uriB, createPdf(listOf(listOf("TextoUnicoEnB")))
        )

        val result = useCase(uriA, uriB, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("differencesFound 1 1", (result as PdfToolResult.Success).message)
        val reportText = pageTextsOf(result.outputFile).joinToString(" ")
        assertTrue(reportText.contains("TextoUnicoEnA"))
        assertTrue(reportText.contains("TextoUnicoEnB"))
    }

    @Test
    fun `una pagina que solo existe en un documento se marca como tal, sin contarse como identica`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        stubResolver(
            uriA, createPdf(listOf(listOf("Igual"), listOf("SoloEnA"))),
            uriB, createPdf(listOf(listOf("Igual")))
        )

        val result = useCase(uriA, uriB, messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals("differencesFound 1 2", (result as PdfToolResult.Success).message)
        val reportText = pageTextsOf(result.outputFile).joinToString(" ")
        assertTrue(reportText.contains("reportPageOnlyInA"))
    }

    @Test
    fun `stream nulo al leer el documento A devuelve Error de lectura A`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uriA) } returns null
        every { resolver.openInputStream(uriB) } answers { ByteArrayInputStream(createPdf(listOf(listOf("x")))) }
        every { context.contentResolver } returns resolver

        val result = useCase(uriA, uriB, messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("readErrorA", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `stream nulo al leer el documento B devuelve Error de lectura B`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uriA) } answers { ByteArrayInputStream(createPdf(listOf(listOf("x")))) }
        every { resolver.openInputStream(uriB) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase(uriA, uriB, messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("readErrorB", (result as PdfToolResult.Error).message)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(uriA: Uri, bytesA: ByteArray, uriB: Uri, bytesB: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uriA) } answers { ByteArrayInputStream(bytesA) }
        every { resolver.openInputStream(uriB) } answers { ByteArrayInputStream(bytesB) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdf(pagesContent: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        pagesContent.forEach { lines ->
            val page = pdfDoc.addNewPage()
            val canvas = PdfCanvas(page)
            canvas.beginText().setFontAndSize(font, 12f).moveText(50.0, 700.0)
            lines.forEachIndexed { index, line ->
                if (index > 0) canvas.moveText(0.0, -20.0)
                canvas.showText(line)
            }
            canvas.endText()
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
