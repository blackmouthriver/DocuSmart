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

    private val messages =
        NumberPagesMessages(
            readError = "readError",
            noPages = "noPages",
            generateError = "generateError",
            success = "success %1\$d",
            genericError = "genericError %1\$s",
            pageOfTotalTemplate = "Página %1\$d de %2\$d",
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
    fun `formato PAGE_OF_TOTAL escribe Pagina X de N en cada pagina`() =
        runTest {
            stubResolver(createTestPdf(pages = 3))

            val result =
                useCase(
                    mockk<Uri>(),
                    format = PageNumberFormat.PAGE_OF_TOTAL,
                    messages = messages,
                )

            assertTrue(result is PdfToolResult.Success)
            val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
            assertEquals(listOf("Página 1 de 3", "Página 2 de 3", "Página 3 de 3"), texts)
        }

    @Test
    fun `formato NUMBER_ONLY escribe solo el numero de pagina`() =
        runTest {
            stubResolver(createTestPdf(pages = 2))

            val result =
                useCase(
                    mockk<Uri>(),
                    format = PageNumberFormat.NUMBER_ONLY,
                    messages = messages,
                )

            assertTrue(result is PdfToolResult.Success)
            val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
            assertEquals(listOf("1", "2"), texts)
        }

    @Test
    fun `formato NUMBER_OF_TOTAL escribe numero y total`() =
        runTest {
            stubResolver(createTestPdf(pages = 2))

            val result =
                useCase(
                    mockk<Uri>(),
                    format = PageNumberFormat.NUMBER_OF_TOTAL,
                    messages = messages,
                )

            assertTrue(result is PdfToolResult.Success)
            val texts = pageTextsOf((result as PdfToolResult.Success).outputFile)
            assertEquals(listOf("1 / 2", "2 / 2"), texts)
        }

    @Test
    fun `numerar conserva el total de paginas del original`() =
        runTest {
            stubResolver(createTestPdf(pages = 5))

            val result =
                useCase(
                    mockk<Uri>(),
                    format = PageNumberFormat.NUMBER_ONLY,
                    messages = messages,
                )

            assertTrue(result is PdfToolResult.Success)
            assertEquals(5, pageCountOf((result as PdfToolResult.Success).outputFile))
        }

    // Revisión adversarial de correctitud (ronda 13): sin este test, un PDF
    // con /Rotate 90 o /Rotate 270 nunca se ejercitaba -- el bug real
    // encontrado en esta misma ronda (anchors de 90/270 cruzados entre sí,
    // más el signo del ángulo de rotación del texto invertido) pasaba
    // 100% de los tests existentes porque ninguno usaba una página
    // rotada. Se verifica la posición X del primer glifo dibujado: para
    // 90° debe caer cerca del borde DERECHO del MediaBox (x > width/2),
    // para 270° cerca del borde IZQUIERDO (x < width/2) -- exactamente lo
    // que quedaba invertido en el bug real.
    @Test
    fun `numerar una pagina rotada 90 grados ancla el numero cerca del borde derecho`() =
        runTest {
            stubResolver(createTestPdf(pages = 1, rotation = 90))

            val result = useCase(mockk<Uri>(), format = PageNumberFormat.NUMBER_ONLY, messages = messages)

            assertTrue(result is PdfToolResult.Success)
            val file = (result as PdfToolResult.Success).outputFile
            val x = firstGlyphX(file, pageNumber = 1)
            val width = pageWidthOf(file)
            assertTrue(x > width / 2f, "esperaba x=$x cerca del borde derecho (width=$width)")
        }

    @Test
    fun `numerar una pagina rotada 270 grados ancla el numero cerca del borde izquierdo`() =
        runTest {
            stubResolver(createTestPdf(pages = 1, rotation = 270))

            val result = useCase(mockk<Uri>(), format = PageNumberFormat.NUMBER_ONLY, messages = messages)

            assertTrue(result is PdfToolResult.Success)
            val file = (result as PdfToolResult.Success).outputFile
            val x = firstGlyphX(file, pageNumber = 1)
            val width = pageWidthOf(file)
            assertTrue(x < width / 2f, "esperaba x=$x cerca del borde izquierdo (width=$width)")
        }

    @Test
    fun `numerar un archivo que no es un PDF valido devuelve Error`() =
        runTest {
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

    private fun createTestPdf(
        pages: Int,
        rotation: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        repeat(pages) { pdfDoc.addNewPage().setRotation(rotation) }
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageWidthOf(file: File): Float {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val width = pdf.getPage(1).pageSize.width
        pdf.close()
        return width
    }

    // Posición X (coordenadas raw del MediaBox) del punto de anclaje del
    // texto dibujado en la página -- se lee directo del operador de matriz
    // de texto (`a b c d e f Tm`) en el content stream ya descomprimido,
    // en vez de un listener de eventos de iText (API más frágil, dio
    // NullPointerException en la primera versión de este test).
    private fun firstGlyphX(
        file: File,
        pageNumber: Int,
    ): Float {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val content = String(pdf.getPage(pageNumber).contentBytes, Charsets.ISO_8859_1)
        pdf.close()
        val number = """[-+]?[0-9]*\.?[0-9]+"""
        val tmPattern = Regex("($number)\\s+($number)\\s+($number)\\s+($number)\\s+($number)\\s+($number)\\s+Tm")
        val match =
            tmPattern.find(content)
                ?: error("No se encontró el operador Tm en el content stream: $content")
        return match.groupValues[5].toFloat()
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
        val texts =
            (1..pdf.numberOfPages).map {
                PdfTextExtractor.getTextFromPage(pdf.getPage(it)).trim()
            }
        pdf.close()
        return texts
    }
}
