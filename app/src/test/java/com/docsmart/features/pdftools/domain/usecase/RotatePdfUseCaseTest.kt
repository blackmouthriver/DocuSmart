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
 * RF-PDF-04 (docs/requirements/pdf-tools.md). Reescrito para usar
 * `PdfPage.setRotation` (iText7) en lugar de rasterizar cada página con una
 * matriz de rotación manual — conserva texto/vectores y el ángulo aplicado
 * queda escrito como metadato `/Rotate` que cualquier lector de PDF respeta.
 */
class RotatePdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: RotatePdfUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_rotate_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_rotate_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = RotatePdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `rotar 90 grados escribe la rotacion en todas las paginas`() = runTest {
        stubResolver(createTestPdf(pages = 3))

        val result = useCase(mockk<Uri>(), degrees = 90)

        assertTrue(result is PdfToolResult.Success)
        val rotations = pageRotationsOf((result as PdfToolResult.Success).outputFile)
        assertEquals(listOf(90, 90, 90), rotations)
    }

    @Test
    fun `rotar 270 grados sobre una pagina ya rotada 180 acumula 90`() = runTest {
        stubResolver(createTestPdf(pages = 1, initialRotation = 180))

        val result = useCase(mockk<Uri>(), degrees = 270)

        assertTrue(result is PdfToolResult.Success)
        assertEquals(listOf(90), pageRotationsOf((result as PdfToolResult.Success).outputFile))
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestPdf(pages: Int, initialRotation: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        repeat(pages) {
            val page = pdfDoc.addNewPage()
            if (initialRotation != 0) page.setRotation(initialRotation)
        }
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun pageRotationsOf(file: File): List<Int> {
        val reader = PdfReader(file)
        val pdf = PdfDocument(reader)
        val rotations = (1..pdf.numberOfPages).map { pdf.getPage(it).getRotation() }
        pdf.close()
        return rotations
    }
}
