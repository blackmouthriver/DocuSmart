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
}
