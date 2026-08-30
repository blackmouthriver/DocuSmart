package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * RF-SCAN-06/RF-SCAN-07: solo cubre la lógica pura (matriz de color y
 * dimensiones de reescalado) -- `ScanImageEditor.applyAdjustments()` en sí
 * usa `Bitmap`/`Canvas`/`ColorMatrixColorFilter` de `android.graphics`
 * (stub "not mocked" en unit tests, requiere Robolectric/instrumentación),
 * mismo límite ya documentado para otros use cases del proyecto que tocan
 * bitmaps reales (`CompressPdfUseCase`, ver `pdf-tools.md`).
 */
class ScanImageEditorTest {

    @Test
    fun `sin ajustes, la matriz de color es la identidad`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = 0)

        assertEquals(
            listOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ),
            matrix.toList()
        )
    }

    @Test
    fun `contraste maximo duplica el factor de escala de cada canal`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = 100)

        assertEquals(2f, matrix[0])
        assertEquals(2f, matrix[6])
        assertEquals(2f, matrix[12])
    }

    @Test
    fun `contraste minimo anula el factor de escala de cada canal`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = -100)

        assertEquals(0f, matrix[0])
        assertEquals(0f, matrix[6])
        assertEquals(0f, matrix[12])
    }

    @Test
    fun `brillo positivo suma un desplazamiento positivo sin tocar el factor`() {
        val matrix = buildColorMatrix(brightness = 100, contrast = 0)

        assertEquals(1f, matrix[0], "el factor de contraste no cambia")
        assertEquals(255f, matrix[4])
        assertEquals(255f, matrix[9])
        assertEquals(255f, matrix[14])
    }

    @Test
    fun `brillo negativo resta un desplazamiento`() {
        val matrix = buildColorMatrix(brightness = -100, contrast = 0)

        assertEquals(-255f, matrix[4])
    }

    @Test
    fun `scaledDimensions al 100 por ciento no cambia las dimensiones`() {
        assertEquals(800 to 600, scaledDimensions(800, 600, 100))
    }

    @Test
    fun `scaledDimensions al 50 por ciento reduce a la mitad`() {
        assertEquals(400 to 300, scaledDimensions(800, 600, 50))
    }

    @Test
    fun `scaledDimensions al 25 por ciento redondea sin llegar a cero`() {
        val (width, height) = scaledDimensions(3, 3, 25)

        assertEquals(1, width)
        assertEquals(1, height)
    }
}
