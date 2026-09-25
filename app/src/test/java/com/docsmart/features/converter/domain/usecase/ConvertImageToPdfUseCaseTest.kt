package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Ronda 14 de la auditoría del Convertidor: este use case tenía ~1.6% de
 * cobertura. El camino feliz de `invoke()` construye Bitmaps/Canvas/
 * android.graphics.pdf.PdfDocument reales -- no ejercitable en un test
 * unitario JVM puro sin Robolectric (mismo límite ya documentado en
 * PdfToImageUseCaseTest/ImageFormatUseCaseTest). Los tests de abajo cubren
 * los caminos que SÍ son alcanzables sin decodificar un Bitmap real: la
 * lista vacía, el caso "todas las imágenes fallan al abrir su stream" (no
 * llega a tocar BitmapFactory, ver loadBitmapFromUri) y, sobre todo, el bug
 * real corregido en esta ronda: `loadBitmapFromUri()` atrapaba
 * `CancellationException` con su propio `catch (e: Exception)` genérico
 * interno, sin relanzarla -- el fix de "Hallazgo 1" (relanzar
 * CancellationException antes del catch genérico) estaba aplicado en
 * invoke() pero NO se había propagado a este método anidado.
 */
class ConvertImageToPdfUseCaseTest {
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ConvertImageToPdfUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_imgtopdf_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        useCase = ConvertImageToPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `lista de imagenes vacia devuelve Error sin tocar el sistema de archivos`() =
        runTest {
            val result = useCase(emptyList(), "salida")

            assertTrue(result is ConversionResult.Error)
            assertTrue(File(filesDir, "converted").listFiles().isNullOrEmpty())
        }

    @Test
    fun `si todas las imagenes fallan al abrir su stream, devuelve Error sin dejar un PDF huerfano`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            // openInputStream() devuelve null -- loadBitmapFromUri() corta antes
            // de siquiera llamar a BitmapFactory (?.use{} con receptor null).
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            val result = useCase(listOf(uri), "salida")

            assertTrue(result is ConversionResult.Error)
            val orphanedPdfs = File(filesDir, "converted").listFiles { f -> f.extension == "pdf" }
            assertTrue(orphanedPdfs.isNullOrEmpty(), "no debería quedar un .pdf huérfano: ${orphanedPdfs?.map { it.name }}")
        }

    // Bug real corregido en esta ronda: ver el comentario de la clase.
    // Se prueba loadBitmapFromUri() directamente (expuesta como `internal`
    // para esto) en vez de a través de invoke()/buildPdfFromImages(): ese
    // último construye un android.graphics.pdf.PdfDocument real, y su
    // finally { pdfDocument.close() } lanza "not mocked" en un test JVM
    // puro -- esa excepción de infraestructura reemplazaría/enmascararía la
    // CancellationException real que este test verifica (el finally de un
    // try/finally que también lanza sustituye la excepción que se estaba
    // propagando).
    @Test
    fun `una cancelacion leyendo una imagen se propaga en vez de tratarse como imagen fallida`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
        every { context.contentResolver } returns resolver

        assertThrows(CancellationException::class.java) {
            useCase.loadBitmapFromUri(uri)
        }
    }

    // ── EXIF: decision pura de orientacion ──────────────────────────────────

    /** Aplica [t] a un punto (x, y) con el eje Y hacia abajo, como android.graphics.Matrix. */
    private fun mapPoint(
        t: ExifTransform,
        x: Int,
        y: Int,
    ): Pair<Int, Int> {
        var px = x
        var py = y
        // rotacion horaria en pantalla (Y hacia abajo): 90 -> (x, y) = (-y, x)
        repeat((t.rotationDegrees / 90f).toInt()) {
            val nx = -py
            val ny = px
            px = nx
            py = ny
        }
        if (t.flipHorizontal) px = -px
        if (t.flipVertical) py = -py
        return px to py
    }

    @Test
    fun `exifTransformFor no transforma orientacion normal, indefinida ni desconocida`() {
        assertNull(exifTransformFor(ExifInterface.ORIENTATION_NORMAL))
        assertNull(exifTransformFor(ExifInterface.ORIENTATION_UNDEFINED))
        assertNull(exifTransformFor(99))
    }

    @Test
    fun `exifTransformFor traduce las rotaciones simples`() {
        assertEquals(90f, exifTransformFor(ExifInterface.ORIENTATION_ROTATE_90)!!.rotationDegrees)
        assertEquals(180f, exifTransformFor(ExifInterface.ORIENTATION_ROTATE_180)!!.rotationDegrees)
        assertEquals(270f, exifTransformFor(ExifInterface.ORIENTATION_ROTATE_270)!!.rotationDegrees)
    }

    @Test
    fun `exifTransformFor traduce los espejos simples`() {
        val h = exifTransformFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)!!
        val v = exifTransformFor(ExifInterface.ORIENTATION_FLIP_VERTICAL)!!

        assertEquals(ExifTransform(0f, flipHorizontal = true, flipVertical = false), h)
        assertEquals(ExifTransform(0f, flipHorizontal = false, flipVertical = true), v)
    }

    // Bug real corregido en la ronda 15: TRANSPOSE (5) y TRANSVERSE (7) caian en
    // el `else` y se ignoraban -- la foto salia espejada y rotada en el PDF.
    @Test
    fun `TRANSPOSE equivale a reflejar sobre la diagonal principal`() {
        val t = exifTransformFor(ExifInterface.ORIENTATION_TRANSPOSE)!!

        // transpuesta: (x, y) -> (y, x)
        assertEquals(3 to 1, mapPoint(t, 1, 3))
        assertEquals(-2 to 5, mapPoint(t, 5, -2))
    }

    @Test
    fun `TRANSVERSE equivale a reflejar sobre la diagonal secundaria`() {
        val t = exifTransformFor(ExifInterface.ORIENTATION_TRANSVERSE)!!

        // transversa: (x, y) -> (-y, -x)
        assertEquals(-3 to -1, mapPoint(t, 1, 3))
        assertEquals(2 to -5, mapPoint(t, 5, -2))
    }

    @Test
    fun `las rotaciones EXIF 6 y 8 son horaria de 90 y de 270 grados`() {
        val r90 = exifTransformFor(ExifInterface.ORIENTATION_ROTATE_90)!!
        val r270 = exifTransformFor(ExifInterface.ORIENTATION_ROTATE_270)!!

        assertEquals(-3 to 1, mapPoint(r90, 1, 3))
        assertEquals(3 to -1, mapPoint(r270, 1, 3))
    }

    // ── readExifOrientation: el EXIF es opcional ────────────────────────────

    // Bug real corregido en la ronda 15: un fallo leyendo el EXIF descartaba la
    // imagen completa como "no se pudo cargar".
    @Test
    fun `un fallo leyendo el EXIF devuelve orientacion normal en vez de descartar la imagen`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } throws IOException("sin metadatos")
        every { context.contentResolver } returns resolver

        assertEquals(ExifInterface.ORIENTATION_NORMAL, readExifOrientation(context, uri))
    }

    @Test
    fun `un stream nulo al leer el EXIF devuelve orientacion normal`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        assertEquals(ExifInterface.ORIENTATION_NORMAL, readExifOrientation(context, uri))
    }

    @Test
    fun `una cancelacion leyendo el EXIF se propaga`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
        every { context.contentResolver } returns resolver

        assertThrows(CancellationException::class.java) { readExifOrientation(context, uri) }
    }

    // ── computePageDrawRect/embedTargetSize/needsDownscale: funciones puras ──
    // Ronda 21: solo se ejercitaban indirectamente vía las pruebas
    // instrumentadas (una imagen concreta por test) -- acá se cubren las 3
    // ramas del recuadro (limitado por ancho, por alto, o sin reescalar
    // porque ya entra) y los bordes de embedTargetSize/needsDownscale.

    @Test
    fun `computePageDrawRect no agranda una imagen mas chica que el area disponible`() {
        val rect =

            computePageDrawRect(bitmapWidth = 100, bitmapHeight = 50, pageWidth = 595, pageHeight = 842, margin = 20)

        assertEquals(100f, rect.width, 0.01f)
        assertEquals(50f, rect.height, 0.01f)
        assertEquals(247.5f, rect.left, 0.01f)
        assertEquals(396f, rect.top, 0.01f)
    }

    @Test
    fun `computePageDrawRect limita por ancho cuando la imagen es muy ancha`() {
        val rect =

            computePageDrawRect(bitmapWidth = 2000, bitmapHeight = 1000, pageWidth = 595, pageHeight = 842, margin = 20)

        // maxWidth = 595 - 2*20 = 555 -- el ancho dibujado llega justo a ese tope.
        assertEquals(555f, rect.width, 0.5f)
        assertEquals(20f, rect.left, 0.5f)
        assertTrue(rect.height < 842f - 2 * 20)
    }

    @Test
    fun `computePageDrawRect limita por alto cuando la imagen es muy alta`() {
        val rect =

            computePageDrawRect(bitmapWidth = 1000, bitmapHeight = 3000, pageWidth = 595, pageHeight = 842, margin = 20)

        // maxHeight = 842 - 2*20 = 802 -- el alto dibujado llega justo a ese tope.
        assertEquals(802f, rect.height, 0.5f)
        assertEquals(20f, rect.top, 0.5f)
        assertTrue(rect.width < 595f - 2 * 20)
    }

    @Test
    fun `embedTargetSize multiplica el recuadro por los pixeles por punto pedidos`() {
        val (width, height) = embedTargetSize(drawWidthPts = 100f, drawHeightPts = 50f, multiplier = 3)

        assertEquals(300, width)
        assertEquals(150, height)
    }

    @Test
    fun `embedTargetSize nunca baja de 1 pixel aunque el recuadro sea casi nulo`() {
        val (width, height) = embedTargetSize(drawWidthPts = 0.1f, drawHeightPts = 0.1f, multiplier = 1)

        assertEquals(1, width)
        assertEquals(1, height)
    }

    @Test
    fun `embedTargetSize redondea al entero mas cercano`() {
        val (width, height) = embedTargetSize(drawWidthPts = 10.6f, drawHeightPts = 10.4f, multiplier = 1)

        assertEquals(11, width)
        assertEquals(10, height)
    }

    @Test
    fun `needsDownscale es falso cuando el bitmap ya entra en el objetivo`() {
        assertEquals(
            false,
            needsDownscale(bitmapWidth = 100, bitmapHeight = 100, targetWidth = 200, targetHeight = 200),
        )
        assertEquals(
            false,
            needsDownscale(bitmapWidth = 200, bitmapHeight = 200, targetWidth = 200, targetHeight = 200),
        )
    }

    @Test
    fun `needsDownscale es verdadero si el bitmap excede el objetivo en ancho o en alto`() {
        assertEquals(true, needsDownscale(bitmapWidth = 300, bitmapHeight = 100, targetWidth = 200, targetHeight = 200))
        assertEquals(true, needsDownscale(bitmapWidth = 100, bitmapHeight = 300, targetWidth = 200, targetHeight = 200))
    }
}
