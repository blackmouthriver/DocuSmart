package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class WordToPdfUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: WordToPdfUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_wordtopdf_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        useCase = WordToPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `convierte un docx a PDF con parrafos y tablas`() = runTest {
        stubResolver(createTestDocx(listOf("Primer párrafo"), listOf(listOf("A1", "B1"))))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("pdf", outputFile.extension)
        val extracted = extractPdfText(outputFile)
        assertTrue(extracted.contains("Primer párrafo"))
        assertTrue(extracted.contains("A1"))
    }

    // RF-CONV-07: WordFormatDetectionTest.kt explica por qué el fixture es
    // un .doc real generado con Word y no un byte array sintético.
    @Test
    fun `convierte un doc legado real (OLE2) a PDF`() = runTest {
        stubResolver(legacyDocBytes())

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        val extracted = extractPdfText(outputFile)
        assertTrue(extracted.contains("Titulo de prueba"))
        assertTrue(extracted.contains("Celda A1"))
    }

    // Hallazgo real de la auditoría general 2026-09-17 (B4): antes se
    // procesaban TODOS los párrafos y DESPUÉS todas las tablas (2 pasadas
    // separadas) -- una tabla en medio de dos párrafos salía siempre al
    // final, sin importar su posición real en el documento. `bodyElements`
    // conserva el orden real.
    @Test
    fun `conserva el orden real de parrafo, tabla y parrafo del documento original`() = runTest {
        stubResolver(createInterleavedDocx())

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val extracted = extractPdfText((result as ConversionResult.Success).outputFile)
        val introIndex = extracted.indexOf("Intro")
        val tableIndex = extracted.indexOf("CeldaTabla")
        val conclusionIndex = extracted.indexOf("Conclusion")
        assertTrue(introIndex in 0 until tableIndex, "Intro debe aparecer antes que la tabla")
        assertTrue(tableIndex in 0 until conclusionIndex, "la tabla debe aparecer antes que Conclusion")
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

    private fun createTestDocx(paragraphs: List<String>, tableRows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            paragraphs.forEach { text -> doc.createParagraph().createRun().setText(text) }
            if (tableRows.isNotEmpty()) {
                val table = doc.createTable(tableRows.size, tableRows.first().size)
                tableRows.forEachIndexed { r, row ->
                    row.forEachIndexed { c, value -> table.getRow(r).getCell(c).text = value }
                }
            }
            doc.write(out)
        }
        return out.toByteArray()
    }

    /** Intro (párrafo) → tabla con "CeldaTabla" → Conclusion (párrafo), en ese orden real. */
    private fun createInterleavedDocx(): ByteArray {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            doc.createParagraph().createRun().setText("Intro")
            val table = doc.createTable(1, 1)
            table.getRow(0).getCell(0).text = "CeldaTabla"
            doc.createParagraph().createRun().setText("Conclusion")
            doc.write(out)
        }
        return out.toByteArray()
    }

    private fun legacyDocBytes(): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/legacy-sample.doc")) {
            "No se encontró fixtures/legacy-sample.doc en recursos de test"
        }.use { it.readBytes() }

    private fun extractPdfText(file: File): String {
        val pdf = PdfDocument(PdfReader(file))
        val text = (1..pdf.numberOfPages).joinToString("\n") { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
        pdf.close()
        return text
    }
}
