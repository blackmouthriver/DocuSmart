package com.docsmart.features.viewer.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

private const val EPS = 0.5f

/**
 * Hallazgo #16 de la revisión general 2026-09-16 (cuarta pasada): verifica
 * de punta a punta, con un PDF real, que un resaltado se dibuje en las
 * coordenadas CRUDAS correctas del MediaBox cuando la página tiene
 * `/Rotate` distinto de 0 -- iText7 es JVM puro (no hace falta Robolectric
 * ni un dispositivo con `PdfRenderer` para esto, a diferencia de lo que se
 * asumió al catalogar el hallazgo originalmente). Los valores esperados de
 * `pagina rotada 90 grados` son los mismos usados a mano para derivar
 * `visualRectToRawPageRect()` en `AnnotationCoordinatesTest`.
 */
class FlattenAnnotationsPdfUseCaseTest {
    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: FlattenAnnotationsPdfUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_flatten_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_flatten_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = FlattenAnnotationsPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `resaltado en pagina sin rotar se dibuja sin transformar (sin regresion)`() =
        runTest {
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))
            val annotation = highlight(xPts = 15f, yPts = 25f, widthPts = 40f, heightPts = 50f)

            val result = useCase(mockk<Uri>(relaxed = true), listOf(annotation))

            assertNotNull(result)
            val rect = readRectangleOperator(result!!)
            assertEquals(15f, rect[0], EPS)
            assertEquals(25f, rect[1], EPS)
            assertEquals(40f, rect[2], EPS)
            assertEquals(50f, rect[3], EPS)
        }

    @Test
    fun `resaltado en pagina rotada 90 grados se dibuja en las coordenadas crudas correctas`() =
        runTest {
            // Página cruda de 200x300 puntos rotada 90° -- rawX0 = W-(vy+vh) =
            // 200-(180+10) = 10, rawY0 = vx = 10, rawAncho = vh = 10, rawAlto = vw = 20.
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 90))
            val annotation = highlight(xPts = 10f, yPts = 180f, widthPts = 20f, heightPts = 10f)

            val result = useCase(mockk<Uri>(relaxed = true), listOf(annotation))

            assertNotNull(result)
            val rect = readRectangleOperator(result!!)
            assertEquals(10f, rect[0], EPS)
            assertEquals(10f, rect[1], EPS)
            assertEquals(10f, rect[2], EPS)
            assertEquals(20f, rect[3], EPS)
        }

    @Test
    fun `resaltado en pagina rotada 180 grados se dibuja en las coordenadas crudas correctas`() =
        runTest {
            // rawX0 = W-(vx+vw) = 200-(10+30) = 160, rawY0 = H-(vy+vh) = 300-(20+40) = 240.
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 180))
            val annotation = highlight(xPts = 10f, yPts = 20f, widthPts = 30f, heightPts = 40f)

            val result = useCase(mockk<Uri>(relaxed = true), listOf(annotation))

            assertNotNull(result)
            val rect = readRectangleOperator(result!!)
            assertEquals(160f, rect[0], EPS)
            assertEquals(240f, rect[1], EPS)
            assertEquals(30f, rect[2], EPS)
            assertEquals(40f, rect[3], EPS)
        }

    @Test
    fun `resaltado en pagina rotada 270 grados se dibuja en las coordenadas crudas correctas`() =
        runTest {
            // rawX0 = vy = 20, rawY0 = H-(vx+vw) = 300-(10+30) = 260.
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 270))
            val annotation = highlight(xPts = 10f, yPts = 20f, widthPts = 30f, heightPts = 40f)

            val result = useCase(mockk<Uri>(relaxed = true), listOf(annotation))

            assertNotNull(result)
            val rect = readRectangleOperator(result!!)
            assertEquals(20f, rect[0], EPS)
            assertEquals(260f, rect[1], EPS)
            assertEquals(40f, rect[2], EPS)
            assertEquals(30f, rect[3], EPS)
        }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun highlight(
        xPts: Float,
        yPts: Float,
        widthPts: Float,
        heightPts: Float,
    ) = AnnotationEntity(
        id = "a1",
        documentId = "doc",
        type = AnnotationType.HIGHLIGHT,
        page = 1,
        xPts = xPts,
        yPts = yPts,
        widthPts = widthPts,
        heightPts = heightPts,
        color = 0xFFFFEB3B.toInt(),
        text = "",
        createdAt = 0L,
    )

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createRotatedPdf(
        rawWidth: Float,
        rawHeight: Float,
        rotation: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val page = pdfDoc.addNewPage(PageSize(rawWidth, rawHeight))
        page.setRotation(rotation)
        pdfDoc.close()
        return out.toByteArray()
    }

    // El resaltado se dibuja como un único operador "re" (rectángulo) seguido
    // de "f" (fill) en el content stream crudo -- se extrae con una regex en
    // vez de un rasterizador (iText7 no incluye uno) porque lo que hay que
    // verificar es exactamente en qué coordenadas del MediaBox quedó el
    // rectángulo, no su apariencia visual.
    private fun readRectangleOperator(file: File): List<Float> {
        val pdfDoc = PdfDocument(PdfReader(file))
        val content = String(pdfDoc.getPage(1).contentBytes, Charsets.ISO_8859_1)
        pdfDoc.close()
        val match =
            Regex("""(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)\s+re""").find(content)
                ?: error("No se encontró el operador 're' (rectángulo) en el content stream:\n$content")
        return match.groupValues.drop(1).map { it.toFloat() }
    }
}
