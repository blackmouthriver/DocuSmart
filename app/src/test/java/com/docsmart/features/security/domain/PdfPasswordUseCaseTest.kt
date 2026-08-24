package com.docsmart.features.security.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
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

/** Cubre RF-SEC-10/11 (HU-SEC-07/08, docs/requirements/security.md). */
class PdfPasswordUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private val useCase = PdfPasswordUseCase()

    private val messages = PdfPasswordMessages(
        readError = "No se pudo leer el archivo",
        emptyFile = "El archivo está vacío",
        protectSuccess = "PDF protegido correctamente",
        protectGenerateError = "El archivo protegido no se generó correctamente (%1\$d bytes)",
        protectError = "No se pudo proteger el PDF: %1\$s",
        removeSuccess = "Contraseña eliminada correctamente",
        removeGenerateError = "El archivo sin contraseña no se generó correctamente (%1\$d bytes)",
        removeError = "No se pudo procesar el PDF: %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_pdf_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_pdf_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `protect cifra un PDF valido y devuelve Success`() = runTest {
        stubResolver(createMinimalPdf())

        val result = useCase.protect(context, mockk<Uri>(), "1234", "documento", messages)

        assertTrue(result is PdfPasswordResult.Success)
        val success = result as PdfPasswordResult.Success
        assertTrue(success.outputFile.exists())
        assertTrue(success.outputFile.length() > 0)
        assertEquals(messages.protectSuccess, success.message)
    }

    @Test
    fun `protect devuelve Error si el archivo no se pudo abrir`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase.protect(context, uri, "1234", "documento", messages)

        assertTrue(result is PdfPasswordResult.Error)
        assertEquals(messages.readError, (result as PdfPasswordResult.Error).message)
    }

    @Test
    fun `protect devuelve Error si el archivo esta vacio`() = runTest {
        stubResolver(ByteArray(0))

        val result = useCase.protect(context, mockk<Uri>(), "1234", "documento", messages)

        assertTrue(result is PdfPasswordResult.Error)
        assertEquals(messages.emptyFile, (result as PdfPasswordResult.Error).message)
    }

    @Test
    fun `removePassword con la contrasena correcta devuelve Success`() = runTest {
        stubResolver(createMinimalPdf())
        val protectResult = useCase.protect(context, mockk<Uri>(), "clave123", "doc", messages)
        val protectedBytes = (protectResult as PdfPasswordResult.Success).outputFile.readBytes()

        stubResolver(protectedBytes)
        val result = useCase.removePassword(context, mockk<Uri>(), "clave123", "doc", messages)

        assertTrue(result is PdfPasswordResult.Success)
        assertEquals(messages.removeSuccess, (result as PdfPasswordResult.Success).message)
    }

    @Test
    fun `removePassword con contrasena incorrecta devuelve WrongPassword`() = runTest {
        stubResolver(createMinimalPdf())
        val protectResult = useCase.protect(context, mockk<Uri>(), "clave123", "doc", messages)
        val protectedBytes = (protectResult as PdfPasswordResult.Success).outputFile.readBytes()

        stubResolver(protectedBytes)
        val result = useCase.removePassword(context, mockk<Uri>(), "clave-equivocada", "doc", messages)

        assertEquals(PdfPasswordResult.WrongPassword, result)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createMinimalPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        pdfDoc.addNewPage()
        pdfDoc.close()
        return out.toByteArray()
    }
}
