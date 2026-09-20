package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ronda 20: encodeQrMatrix/qrPixelScale/renderQrPixels (QrContentFormat.kt), ahora
 * usados por generateQrBitmap. Antes estaban sin cablear y sin tests.
 */
class QrRenderTest {
    @Test
    fun `encodeQrMatrix genera una matriz cuadrada con zona de silencio`() {
        val matrix = encodeQrMatrix("https://docsmart.app", hasLogo = false)

        assertNotNull(matrix)
        assertEquals(matrix!!.width, matrix.height)
        // La zona de silencio de 4 modulos deja las esquinas y bordes en blanco.
        assertFalse(matrix[0, 0])
        assertFalse(matrix[matrix.width - 1, matrix.height - 1])
        assertFalse(matrix[3, 3])
    }

    @Test
    fun `encodeQrMatrix con logo usa mas modulos que sin logo por la correccion H`() {
        val text = "https://docsmart.app/documentos/compartidos/informe-anual"

        val plain = encodeQrMatrix(text, hasLogo = false)!!
        val withLogo = encodeQrMatrix(text, hasLogo = true)!!

        assertTrue(withLogo.width >= plain.width)
    }

    @Test
    fun `encodeQrMatrix admite caracteres no ASCII`() {
        assertNotNull(encodeQrMatrix("Reunión de canción y ñandú 日本語", hasLogo = false))
    }

    @Test
    fun `encodeQrMatrix devuelve null si el contenido no cabe en ningun QR`() {
        assertNull(encodeQrMatrix("x".repeat(10_000), hasLogo = false))
        assertNull(encodeQrMatrix("x".repeat(10_000), hasLogo = true))
    }

    @Test
    fun `encodeQrMatrix cae al nivel inferior cuando el nivel preferido no alcanza`() {
        // ~2500 bytes no caben con M (max ~2331) pero si con L (max ~2953).
        assertNotNull(encodeQrMatrix("a".repeat(2_500), hasLogo = false))
        // Con logo: no cabe en H (~1273) pero si en Q (~1663).
        assertNotNull(encodeQrMatrix("a".repeat(1_500), hasLogo = true))
    }

    @Test
    fun `qrPixelScale apunta a 512 px pero nunca baja de 8 px por modulo`() {
        assertEquals(12, qrPixelScale(41))
        assertEquals(8, qrPixelScale(64))
        assertEquals(8, qrPixelScale(185))
        // Contador invalido: no divide por cero.
        assertEquals(512, qrPixelScale(0))
    }

    @Test
    fun `renderQrPixels escala cada modulo y respeta los colores`() {
        val matrix = encodeQrMatrix("A", hasLogo = false)!!
        val scale = 3
        val module = 0xFF112233.toInt()
        val background = 0xFFFFFFFF.toInt()

        val pixels = renderQrPixels(matrix, scale, module, background)

        val side = matrix.width * scale
        assertEquals(side * side, pixels.size)
        assertEquals(background, pixels[0])
        val used = pixels.toSet()
        assertTrue(used.all { it == module || it == background })
        assertTrue(module in used)
        // Todos los pixeles de un mismo modulo comparten color.
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val expected = if (matrix[x, y]) module else background
                assertEquals(expected, pixels[(y * scale) * side + x * scale])
                assertEquals(expected, pixels[(y * scale + scale - 1) * side + x * scale + scale - 1])
            }
        }
    }
}
