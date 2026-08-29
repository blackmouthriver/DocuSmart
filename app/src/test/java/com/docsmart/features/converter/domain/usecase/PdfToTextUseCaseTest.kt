package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
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
 * No existía test para este use case -- por eso nadie había detectado que
 * `pdfDoc.close()` se llamaba ANTES de leer `pdfDoc.numberOfPages` para
 * construir el `ConversionResult.Success`, lo que hacía que TODA conversión
 * PDF → Texto fallara siempre con
 * `PdfException: Document was closed. It is impossible to execute action.`
 * (iText7 invalida el documento al cerrarlo). Encontrado verificando
 * RF-CONV-08 en dispositivo real -- no es un bug del lote, es preexistente
 * y afectaba también la conversión de un solo archivo.
 */
class PdfToTextUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: PdfToTextUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_pdftotext_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_pdftotext_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = PdfToTextUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `extrae el texto real de un PDF de una pagina`() = runTest {
        stubResolver(createPdf(listOf("Hola mundo")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val success = result as ConversionResult.Success
        assertEquals(1, success.pageCount)
        assertTrue(success.outputFile.readText().contains("Hola mundo"))
    }

    @Test
    fun `cuenta correctamente las paginas de un PDF de varias paginas`() = runTest {
        stubResolver(createPdf(listOf("Primera página", "Segunda página", "Tercera página")))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        assertEquals(3, (result as ConversionResult.Success).pageCount)
    }

    @Test
    fun `puede llamarse varias veces seguidas sin interferir entre si`() = runTest {
        // RF-CONV-08: PdfToTextUseCase se invoca repetidamente dentro del
        // mismo lote -- antes usaba un nombre de archivo de caché fijo
        // ("temp_text.pdf"), frágil ante llamadas repetidas.
        stubResolver(createPdf(listOf("Archivo A")))
        val resultA = useCase(mockk<Uri>(), "salidaA")

        stubResolver(createPdf(listOf("Archivo B")))
        val resultB = useCase(mockk<Uri>(), "salidaB")

        assertTrue(resultA is ConversionResult.Success)
        assertTrue(resultB is ConversionResult.Success)
        assertTrue((resultA as ConversionResult.Success).outputFile.readText().contains("Archivo A"))
        assertTrue((resultB as ConversionResult.Success).outputFile.readText().contains("Archivo B"))
    }

    // NOTA: no se cubre "PDF sin texto extraíble → Error" -- el use case
    // agrega un encabezado "=== Página N ===" a CADA página incondicionalmente
    // antes de comprobar `text.isBlank()`, así que esa rama nunca es
    // alcanzable en la práctica (siempre hay al menos el encabezado). Hallazgo
    // real encontrado al escribir este test, fuera de alcance de RF-CONV-08 --
    // reportado por separado.

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

    private fun createPdf(pageLines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        pageLines.forEach { line ->
            val page = pdfDoc.addNewPage()
            val canvas = PdfCanvas(page)
            canvas.beginText().setFontAndSize(font, 12f).moveText(50.0, 700.0)
            canvas.showText(line)
            canvas.endText()
        }
        if (pageLines.isEmpty()) pdfDoc.addNewPage()
        pdfDoc.close()
        return out.toByteArray()
    }
}
