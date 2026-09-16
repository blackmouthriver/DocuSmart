package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
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
import java.util.Base64

/**
 * HU-53 (backlog UX 2026-09-10): "Extraer imágenes" produce N archivos a
 * partir de un único PDF -- estos tests usan PDFs reales creados con iText7
 * (imágenes embebidas de verdad, no simuladas) para verificar AC1 (N
 * imágenes -> N archivos), AC2 (sin imágenes -> mensaje claro, no un
 * resultado vacío) y el recorrido recursivo de Form XObjects (imágenes que
 * un generador de PDF envuelve en un formulario en vez de referenciarlas
 * directo desde la página, común en exportaciones de Word/Office).
 */
class ExtractImagesFromPdfUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ExtractImagesFromPdfUseCase

    private val messages = ExtractImagesMessages(
        readError = "readError", noPages = "noPages", noImages = "noImages",
        success = "success %1\$d", genericError = "genericError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_extractimg_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_extractimg_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = ExtractImagesFromPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `PDF con 3 imagenes en paginas distintas produce 3 archivos`() = runTest {
        stubResolver(createPdfWithImages(imageCount = 3))

        val result = useCase(mockk<Uri>(), messages = messages)

        assertTrue(result is PdfToolResult.MultiSuccess)
        val files = (result as PdfToolResult.MultiSuccess).outputFiles
        assertEquals(3, files.size)
        files.forEach { assertTrue(it.exists() && it.length() > 0) }
    }

    @Test
    fun `PDF sin imagenes devuelve Error en vez de un resultado vacio`() = runTest {
        stubResolver(createPdfWithImages(imageCount = 0, plainPages = 2))

        val result = useCase(mockk<Uri>(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
        assertEquals("noImages", (result as PdfToolResult.Error).message)
    }

    @Test
    fun `PDF invalido devuelve Error de lectura`() = runTest {
        stubResolver("esto no es un pdf".toByteArray())

        val result = useCase(mockk<Uri>(), messages = messages)

        assertTrue(result is PdfToolResult.Error)
    }

    @Test
    fun `imagen envuelta en un Form XObject tambien se extrae`() = runTest {
        stubResolver(createPdfWithImageInsideForm())

        val result = useCase(mockk<Uri>(), messages = messages)

        assertTrue(result is PdfToolResult.MultiSuccess)
        assertEquals(1, (result as PdfToolResult.MultiSuccess).outputFiles.size)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdfWithImages(imageCount: Int, plainPages: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        repeat(imageCount) {
            val page = pdfDoc.addNewPage()
            val canvas = PdfCanvas(page)
            val imageData = ImageDataFactory.create(createPngBytes())
            canvas.addImageFittedIntoRectangle(imageData, Rectangle(10f, 10f, 50f, 50f), false)
        }
        repeat(plainPages) { pdfDoc.addNewPage() }
        pdfDoc.close()
        return out.toByteArray()
    }

    private fun createPdfWithImageInsideForm(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val page = pdfDoc.addNewPage()

        val formXObject = PdfFormXObject(Rectangle(0f, 0f, 50f, 50f))
        val formCanvas = PdfCanvas(formXObject, pdfDoc)
        val imageData = ImageDataFactory.create(createPngBytes())
        formCanvas.addImageFittedIntoRectangle(imageData, Rectangle(0f, 0f, 50f, 50f), false)

        val pageCanvas = PdfCanvas(page)
        pageCanvas.addXObjectAt(formXObject, 0f, 0f)

        pdfDoc.close()
        return out.toByteArray()
    }

    // PNG mínimo (1x1 transparente) codificado en base64 -- evita depender
    // de java.awt/ImageIO (no disponibles en el classpath de este módulo de
    // tests), suficiente para que ImageDataFactory.create() lo reconozca
    // como una imagen embebida real.
    private fun createPngBytes(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    )
}
