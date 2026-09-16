package com.docsmart.features.converter.domain.usecase

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt() // 2x1: rojo, verde
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
        val expected = byteArrayOf(
            0xFF.toByte(), 0x00, 0x00, // azul  -> B,G,R
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // blanco -> B,G,R
            0x00, 0x00, // padding (2 bytes)
            0x00, 0x00, 0xFF.toByte(), // rojo  -> B,G,R
            0x00, 0xFF.toByte(), 0x00, // verde -> B,G,R
            0x00, 0x00 // padding (2 bytes)
        )
        assertEquals(expected.size, pixelData.size)
        assertEquals(expected.toList(), pixelData.toList())
    }
}
