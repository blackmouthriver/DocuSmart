package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
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

class WordToHtmlUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: WordToHtmlUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_wordtohtml_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        useCase = WordToHtmlUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `convierte un docx a HTML con parrafos`() = runTest {
        stubResolver(createTestDocx(listOf("Primer párrafo", "Segundo párrafo")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("html", outputFile.extension)
        val html = outputFile.readText()
        assertTrue(html.contains("<p>Primer párrafo</p>"))
        assertTrue(html.contains("<p>Segundo párrafo</p>"))
    }

    // RF-CONV-07: WordFormatDetectionTest.kt explica por qué el fixture es
    // un .doc real generado con Word y no un byte array sintético.
    @Test
    fun `convierte un doc legado real (OLE2) a HTML detectando el encabezado`() = runTest {
        stubResolver(legacyDocBytes())

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val html = (result as ConversionResult.Success).outputFile.readText()
        assertTrue(html.contains("<h2>Titulo de prueba</h2>"))
        assertTrue(html.contains("<p>Primer parrafo del documento legado.</p>"))
        assertTrue(html.contains("Celda A1"))
    }

    @Test
    fun `docx sin texto extraible devuelve Error`() = runTest {
        stubResolver(createTestDocx(emptyList()))

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

    private fun createTestDocx(paragraphs: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            paragraphs.forEach { text -> doc.createParagraph().createRun().setText(text) }
            doc.write(out)
        }
        return out.toByteArray()
    }

    private fun legacyDocBytes(): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/legacy-sample.doc")) {
            "No se encontró fixtures/legacy-sample.doc en recursos de test"
        }.use { it.readBytes() }
}
