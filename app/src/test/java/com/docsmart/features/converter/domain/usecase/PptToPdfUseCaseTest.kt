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
 * Cubre el bug real encontrado en Conversión (docs/requirements/conversion.md):
 * "PPT → PDF" estaba enrutado a `WordToPdfUseCase`, que solo puede parsear
 * .docx (Apache POI `XWPFDocument`) — convertir un .pptx real siempre
 * lanzaba una excepción de formato. Este use case reemplaza ese hueco
 * reutilizando el mismo parseo XML que `PptToTextUseCase`, componiendo el
 * resultado como PDF en vez de texto plano.
 */
class PptToPdfUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: PptToPdfUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_ppttopdf_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        useCase = PptToPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `genera un PDF con el texto de cada diapositiva`() = runTest {
        stubResolver(createTestPptx(listOf("Bienvenida", "Agenda del día")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("pdf", outputFile.extension)
        assertEquals(2, result.pageCount)

        val extracted = extractPdfText(outputFile)
        assertTrue(extracted.contains("Bienvenida"))
        assertTrue(extracted.contains("Agenda del día"))
    }

    @Test
    fun `presentacion sin texto devuelve Error`() = runTest {
        stubResolver(createTestPptx(emptyList()))

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

    /** Construye un .pptx mínimo: solo las entradas ppt/slides/slideN.xml que el parser necesita. */
    private fun createTestPptx(slidesText: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            slidesText.forEachIndexed { index, text ->
                zip.putNextEntry(ZipEntry("ppt/slides/slide${index + 1}.xml"))
                val xml = """
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

    private fun extractPdfText(file: File): String {
        val pdf = PdfDocument(PdfReader(file))
        val text = (1..pdf.numberOfPages).joinToString("\n") { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
        pdf.close()
        return text
    }
}
