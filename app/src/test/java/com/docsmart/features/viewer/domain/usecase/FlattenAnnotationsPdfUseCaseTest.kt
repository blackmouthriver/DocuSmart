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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `sin anotaciones devuelve null sin tocar el origen`() =
        runTest {
            // contentResolver no esta stubeado: si se intentara copiar, mockk lanzaria.
            assertNull(useCase(mockk<Uri>(relaxed = true), emptyList()))
        }

    @Test
    fun `el resaltado usa el color de la anotacion`() =
        runTest {
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))

            val result = useCase(mockk<Uri>(relaxed = true), listOf(highlight(10f, 20f, 30f, 40f)))

            assertNotNull(result)
            val pdfDoc = PdfDocument(PdfReader(result!!))
            val content = String(pdfDoc.getPage(1).contentBytes, Charsets.ISO_8859_1)
            pdfDoc.close()
            // 0xFFFFEB3B -> r=255, g=235 (0.92), b=59 (0.23)
            assertTrue(Regex("""1\s+0\.92\d*\s+0\.23\d*\s+rg""").containsMatchIn(content), content)
        }

    @Test
    fun `una nota agrega un comentario nativo con su texto en el punto de anclaje`() =
        runTest {
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))

            val result = useCase(mockk<Uri>(relaxed = true), listOf(note(xPts = 50f, yPts = 60f, text = "revisar")))

            assertNotNull(result)
            val pdfDoc = PdfDocument(PdfReader(result!!))
            val annotations = pdfDoc.getPage(1).annotations
            pdfDoc.close()
            assertEquals(1, annotations.size)
            assertEquals("revisar", annotations[0].contents.toUnicodeString())
            val rect = annotations[0].rectangle.toRectangle()
            // Centro (50,60), lado 16 -> esquina inferior izquierda (42,52).
            assertEquals(42f, rect.x, EPS)
            assertEquals(52f, rect.y, EPS)
            assertEquals(16f, rect.width, EPS)
        }

    @Test
    fun `una nota en pagina rotada 90 grados se ancla en el punto crudo transformado`() =
        runTest {
            // visual (10,180) en pagina cruda 200x300 rotada 90 -> centro crudo (200-180, 10) = (20, 10).
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 90))

            val result = useCase(mockk<Uri>(relaxed = true), listOf(note(xPts = 10f, yPts = 180f, text = "n")))

            assertNotNull(result)
            val pdfDoc = PdfDocument(PdfReader(result!!))
            val rect = pdfDoc.getPage(1).annotations[0].rectangle.toRectangle()
            pdfDoc.close()
            assertEquals(12f, rect.x, EPS)
            assertEquals(2f, rect.y, EPS)
        }

    @Test
    fun `anotaciones de paginas fuera de rango se ignoran sin fallar`() =
        runTest {
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))
            val outOfRange = highlight(1f, 2f, 3f, 4f).copy(page = 5)
            val zeroPage = highlight(1f, 2f, 3f, 4f).copy(page = 0)

            val result = useCase(mockk<Uri>(relaxed = true), listOf(outOfRange, zeroPage))

            assertNotNull(result)
            val pdfDoc = PdfDocument(PdfReader(result!!))
            val content = String(pdfDoc.getPage(1).contentBytes, Charsets.ISO_8859_1)
            pdfDoc.close()
            assertFalse(content.contains(" re"), content)
        }

    @Test
    fun `no deja archivos temporales en cacheDir tras aplanar`() =
        runTest {
            stubResolver(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))

            val result = useCase(mockk<Uri>(relaxed = true), listOf(highlight(1f, 2f, 3f, 4f)))

            assertNotNull(result)
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
            assertTrue(result!!.absolutePath.startsWith(filesDir.absolutePath))
        }

    @Test
    fun `origen con esquema file inexistente devuelve null`() =
        runTest {
            val uri = mockk<Uri>()
            every { uri.scheme } returns "file"
            every { uri.path } returns File(cacheDir, "no-existe.pdf").absolutePath

            assertNull(useCase(uri, listOf(highlight(1f, 2f, 3f, 4f))))
        }

    @Test
    fun `origen con esquema file legible se aplana desde esa ruta`() =
        runTest {
            val source = File(filesDir, "origen.pdf")
            source.writeBytes(createRotatedPdf(rawWidth = 200f, rawHeight = 300f, rotation = 0))
            val uri = mockk<Uri>()
            every { uri.scheme } returns "file"
            every { uri.path } returns source.absolutePath

            val result = useCase(uri, listOf(highlight(15f, 25f, 40f, 50f)))

            assertNotNull(result)
            assertEquals(15f, readRectangleOperator(result!!)[0], EPS)
        }

    @Test
    fun `un content uri sin flujo legible devuelve null`() =
        runTest {
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(any()) } returns null
            every { context.contentResolver } returns resolver

            assertNull(useCase(mockk<Uri>(relaxed = true), listOf(highlight(1f, 2f, 3f, 4f))))
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `un PDF corrupto devuelve null y no deja salida parcial ni temporales`() =
        runTest {
            stubResolver("esto no es un pdf".toByteArray())

            val result = useCase(mockk<Uri>(relaxed = true), listOf(highlight(1f, 2f, 3f, 4f)))

            assertNull(result)
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
            // Regresion: reader/writer se cerraban solo si PdfDocument() no lanzaba,
            // dejando el archivo de salida abierto y sin poder borrarse.
            assertTrue(File(filesDir, "viewer_share").listFiles().orEmpty().isEmpty())
        }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun note(
        xPts: Float,
        yPts: Float,
        text: String,
    ) = highlight(xPts, yPts, 0f, 0f).copy(type = AnnotationType.NOTE, text = text)

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
