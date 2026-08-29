package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfFormField
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * RF-PDF-12. Solo detecta campos de tipo texto (`PdfName.Tx`) -- mismo
 * criterio de alcance que RF-PDF-10 (solo texto, no imágenes existentes).
 */
class DetectFormFieldsUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: DetectFormFieldsUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_detectform_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        useCase = DetectFormFieldsUseCase(context)
    }

    @Test
    fun `detecta los campos de texto del formulario con su nombre y valor actual`() = runTest {
        stubResolver(createPdfWithForm())

        val fields = useCase(mockk<Uri>())

        assertEquals(2, fields.size)
        assertTrue(fields.any { it.name == "nombre" && it.currentValue == "" })
        assertTrue(fields.any { it.name == "email" && it.currentValue == "correo@ejemplo.com" })
    }

    @Test
    fun `un PDF sin AcroForm devuelve lista vacia`() = runTest {
        stubResolver(createPdfWithoutForm())

        val fields = useCase(mockk<Uri>())

        assertTrue(fields.isEmpty())
    }

    @Test
    fun `un archivo que no es un PDF valido devuelve lista vacia sin lanzar excepcion`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val fields = useCase(mockk<Uri>())

        assertTrue(fields.isEmpty())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdfWithForm(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val page = pdfDoc.addNewPage()
        val form = PdfAcroForm.getAcroForm(pdfDoc, true)
        val nameField = PdfFormField.createText(pdfDoc, Rectangle(50f, 700f, 200f, 30f), "nombre", "")
        val emailField = PdfFormField.createText(pdfDoc, Rectangle(50f, 650f, 200f, 30f), "email", "correo@ejemplo.com")
        form.addField(nameField, page)
        form.addField(emailField, page)
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun createPdfWithoutForm(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        pdfDoc.addNewPage()
        pdfDoc.close()
        return out.toByteArray()
    }
}
