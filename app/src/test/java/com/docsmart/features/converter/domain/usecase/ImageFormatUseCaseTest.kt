package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/**
 * Hallazgo real de la revisión general 2026-09-16 (cuarta pasada, #24):
 * Imagen→BMP escribía bytes PNG con extensión ".bmp" (Android no tiene
 * codificador BMP real) -- argbPixelsToBmp() es un codificador BMP de 24
 * bits sin comprimir real, extraído de bitmapToBmp() (que sí necesita un
 * android.graphics.Bitmap real, no testeable en este proyecto sin
 * Robolectric) para poder verificar el empaquetado de bytes -- lo
 * realmente propenso a error de este fix -- con datos puros.
 */
class ImageFormatUseCaseTest {
    private fun useCase() = ImageFormatUseCase(mockk<Context>(relaxed = true))

    @Test
    fun `argbPixelsToBmp escribe el encabezado BITMAPFILEHEADER-BITMAPINFOHEADER correcto`() {
        val pixels =
            intArrayOf(
                0xFFFF0000.toInt(),
                // 2x1: rojo, verde
                0xFF00FF00.toInt(),
            )

        val bytes = useCase().argbPixelsToBmp(width = 2, height = 1, pixels = pixels)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // BITMAPFILEHEADER
        assertEquals('B'.code.toByte(), buf.get())
        assertEquals('M'.code.toByte(), buf.get())
        val fileSize = buf.int
        assertEquals(bytes.size, fileSize)
        assertEquals(0, buf.short.toInt()) // reservado 1
        assertEquals(0, buf.short.toInt()) // reservado 2
        assertEquals(54, buf.int) // offset a los datos de píxeles

        // BITMAPINFOHEADER
        assertEquals(40, buf.int) // tamaño de este header
        assertEquals(2, buf.int) // width
        assertEquals(1, buf.int) // height
        assertEquals(1, buf.short.toInt()) // planos
        assertEquals(24, buf.short.toInt()) // bits por píxel
        assertEquals(0, buf.int) // BI_RGB
        val pixelDataSize = buf.int
        assertEquals(0, buf.int) // ppm X
        assertEquals(0, buf.int) // ppm Y
        assertEquals(0, buf.int) // colores usados
        assertEquals(0, buf.int) // colores importantes

        assertEquals(bytes.size - 54, pixelDataSize)
    }

    @Test
    fun `argbPixelsToBmp escribe pixeles BGR de abajo hacia arriba con padding por fila`() {
        // 4 píxeles de ancho * 3 bytes = 12 bytes por fila, ya múltiplo de 4 -- sin padding.
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        val white = 0xFFFFFFFF.toInt()
        // Fila 0 (arriba, en la imagen lógica): rojo, verde
        // Fila 1 (abajo, en la imagen lógica): azul, blanco
        val pixels = intArrayOf(red, green, blue, white)

        val bytes = useCase().argbPixelsToBmp(width = 2, height = 2, pixels = pixels)

        // Header: 54 bytes. Cada fila: 2 píxeles * 3 bytes = 6 bytes sin
        // padding -- (4 - 6%4)%4 = 2 bytes de padding por fila.
        val pixelData = bytes.copyOfRange(54, bytes.size)
        // BMP escribe de abajo hacia arriba: la primera fila de datos es
        // la última fila lógica (azul, blanco), en BGR.
        val expected =
            byteArrayOf(
                0xFF.toByte(),
                0x00,
                // azul  -> B,G,R
                0x00,
                0xFF.toByte(),
                0xFF.toByte(),
                // blanco -> B,G,R
                0xFF.toByte(),
                0x00,
                // padding (2 bytes)
                0x00,
                0x00,
                0x00,
                // rojo  -> B,G,R
                0xFF.toByte(),
                0x00,
                0xFF.toByte(),
                // verde -> B,G,R
                0x00,
                0x00,
                // padding (2 bytes)
                0x00,
            )
        assertEquals(expected.size, pixelData.size)
        assertEquals(expected.toList(), pixelData.toList())
    }

    // ── argbPixelsToBmp: padding y canal alfa ───────────────────────────────

    @Test
    fun `argbPixelsToBmp rellena cada fila a multiplo de 4 para todos los anchos`() {
        // (ancho -> padding esperado por fila): 1->1, 2->2, 3->3, 4->0, 5->1
        val expectedPadding = mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 0, 5 to 1)
        expectedPadding.forEach { (width, padding) ->
            val height = 3
            val bytes = useCase().argbPixelsToBmp(width, height, IntArray(width * height) { 0xFF123456.toInt() })

            val rowSize = width * 3 + padding
            assertEquals(54 + rowSize * height, bytes.size, "tamano total para ancho $width")
            assertEquals(rowSize * height, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(34))
            assertEquals(0, rowSize % 4, "la fila debe ser multiplo de 4 para ancho $width")
        }
    }

    @Test
    fun `argbPixelsToBmp ignora el canal alfa y escribe solo BGR`() {
        // 0x80 de alfa (semitransparente): los bytes de color no deben cambiar.
        val bytes = useCase().argbPixelsToBmp(width = 1, height = 1, pixels = intArrayOf(0x80102030.toInt()))

        assertEquals(0x30.toByte(), bytes[54])
        assertEquals(0x20.toByte(), bytes[55])
        assertEquals(0x10.toByte(), bytes[56])
        // 1 pixel = 3 bytes + 1 de padding.
        assertEquals(58, bytes.size)
    }

    @Test
    fun `argbPixelsToBmp de una imagen de un solo pixel de alto conserva el orden de las columnas`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val bytes = useCase().argbPixelsToBmp(width = 2, height = 1, pixels = intArrayOf(red, blue))

        // rojo -> B=00 G=00 R=FF ; azul -> B=FF G=00 R=00
        val expected = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00)
        assertEquals(expected.toList(), bytes.copyOfRange(54, 60).toList())
    }

    // ── invoke(): caminos alcanzables sin Bitmap real ───────────────────────

    private fun contextWithFiles(): Pair<Context, java.io.File> {
        val filesDir = Files.createTempDirectory("docsmart_imgfmt_").toFile()
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        every { context.getString(R.string.converter_error_read_image) } returns "no se pudo leer"
        every { context.getString(R.string.converter_error_generic_format) } returns "generico %1\$s"
        return context to filesDir
    }

    @Test
    fun `invoke devuelve Error de lectura sin dejar archivos si la imagen no se puede abrir`() =
        runTest {
            val (context, filesDir) = contextWithFiles()
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            val result = ImageFormatUseCase(context)(uri, ConversionType.IMAGE_TO_PNG, "salida")

            assertEquals(ConversionResult.Error("no se pudo leer"), result)
            assertTrue(File(filesDir, "converted").listFiles().isNullOrEmpty())
            filesDir.deleteRecursively()
        }

    @Test
    fun `invoke propaga la cancelacion en vez de convertirla en un Error`() =
        runTest {
            val (context, filesDir) = contextWithFiles()
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
            every { context.contentResolver } returns resolver

            var cancelled = false
            try {
                ImageFormatUseCase(context)(uri, ConversionType.IMAGE_TO_JPG, "salida")
            } catch (e: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled, "la CancellationException debia propagarse")
            filesDir.deleteRecursively()
        }

    @Test
    fun `invoke con una excepcion de lectura devuelve Error generico sin archivos huerfanos`() =
        runTest {
            val (context, filesDir) = contextWithFiles()
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } throws SecurityException("permiso")
            every { context.contentResolver } returns resolver

            val result = ImageFormatUseCase(context)(uri, ConversionType.IMAGE_TO_WEBP, "salida")

            assertEquals(ConversionResult.Error("generico permiso"), result)
            assertTrue(File(filesDir, "converted").listFiles().isNullOrEmpty())
            filesDir.deleteRecursively()
        }
}
