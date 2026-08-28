package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
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
 * RF-PDF-01 (docs/requirements/pdf-tools.md). Reescrito para usar iText7
 * (`copyPagesTo`) en vez de rasterizar cada página a bitmap — el enfoque
 * anterior producía un PDF válido pero con todo el texto convertido a
 * imagen (no seleccionable, no buscable). Estos tests fijan el conteo de
 * páginas resultante como contrato del use case.
 */
class MergePdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: MergePdfUseCase

    private val messages = MergePdfMessages(
        minPdfsError = "minPdfsError", readError = "readError",
        generateError = "generateError", success = "success %1\$d %2\$d",
        genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_merge_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_merge_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = MergePdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `merge de dos PDFs suma las paginas de ambos`() = runTest {
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uriA) } answers { ByteArrayInputStream(createTestPdf(3)) }
        every { resolver.openInputStream(uriB) } answers { ByteArrayInputStream(createTestPdf(2)) }
        every { context.contentResolver } returns resolver

        val result = useCase(listOf(uriA, uriB), messages = messages)

        assertTrue(result is PdfToolResult.Success)
        assertEquals(5, pageCountOf((result as PdfToolResult.Success).outputFile))
    }

    @Test
    fun `merge con menos de 2 PDFs devuelve Error sin tocar el sistema de archivos`() = runTest {
        val result = useCase(listOf(mockk<Uri>()), messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun createTestPdf(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        repeat(pages) { pdfDoc.addNewPage() }
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageCountOf(file: File): Int {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val count = pdf.numberOfPages
        pdf.close()
        return count
    }
}
