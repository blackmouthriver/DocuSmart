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

/**
 * Cubre el bug real encontrado en Conversión (docs/requirements/conversion.md):
 * "Word → Texto" estaba enrutado a `WordToPdfUseCase` — seleccionar esa
 * opción entregaba un PDF, no un .txt. Este use case reemplaza ese hueco.
 */
class WordToTextUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: WordToTextUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_wordtotext_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        useCase = WordToTextUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `extrae el texto de los parrafos del docx a un archivo txt`() = runTest {
        stubResolver(createTestDocx(listOf("Primer párrafo", "Segundo párrafo")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("txt", outputFile.extension)
        val text = outputFile.readText()
        assertTrue(text.contains("Primer párrafo"))
        assertTrue(text.contains("Segundo párrafo"))
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
            paragraphs.forEach { text ->
                val paragraph = doc.createParagraph()
                paragraph.createRun().setText(text)
            }
            doc.write(out)
        }
        return out.toByteArray()
    }
}
