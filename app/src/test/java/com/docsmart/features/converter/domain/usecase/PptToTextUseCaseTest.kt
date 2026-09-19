package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `PptToTextUseCase` no tenía cobertura previa. Cubre en particular el
 * hallazgo B5 de la auditoría general 2026-09-17: `stubResolver()` devuelve
 * un `ByteArrayInputStream` fresco en cada llamada a
 * `openInputStream()` -- si `extractSlideTexts()` volviera a envolver ese
 * stream directo en `ZipInputStream` (el bug real ya corregido acá y en
 * `PptToPdfUseCaseTest`), este test seguiría pasando igual (el stub no
 * reproduce el comportamiento inconsistente real de SAF), así que estos
 * tests verifican el contrato funcional, no el bug en sí -- el fix real se
 * verificó por lectura de código contra el diagnóstico ya documentado en
 * `PptToPdfUseCase`.
 */
class PptToTextUseCaseTest {
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: PptToTextUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_ppttotext_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.getString(any()) } returns "error"
        every { context.getString(any(), any()) } returns "=== Diapositiva N ==="
        useCase = PptToTextUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `genera un txt con el texto de cada diapositiva`() =
        runTest {
            stubResolver(createTestPptx(listOf("Bienvenida", "Agenda del día")))

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Success)
            val outputFile = (result as ConversionResult.Success).outputFile
            assertEquals("txt", outputFile.extension)
            assertEquals(2, result.pageCount)

            val text = outputFile.readText()
            assertTrue(text.contains("Bienvenida"))
            assertTrue(text.contains("Agenda del día"))
        }

    @Test
    fun `presentacion sin texto devuelve Error`() =
        runTest {
            stubResolver(createTestPptx(emptyList()))

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Error)
        }

    @Test
    fun `archivo no legible devuelve Error`() =
        runTest {
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

    /** Construye un .pptx mínimo: solo las entradas ppt/slides/slideN.xml que el parser necesita. */
    private fun createTestPptx(slidesText: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            slidesText.forEachIndexed { index, text ->
                zip.putNextEntry(ZipEntry("ppt/slides/slide${index + 1}.xml"))
                val xml =
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                      <p:cSld><p:spTree><p:sp><p:txBody>
                        <a:p><a:r><a:rPr lang="es-ES" dirty="0"/><a:t>$text</a:t></a:r></a:p>
                      </p:txBody></p:sp></p:spTree></p:cSld>
                    </p:sld>
                    """.trimIndent()
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
