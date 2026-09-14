package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * HU-41 (backlog UX 2026-08-30/09-14): solo cubre la lógica pura (matriz de
 * color por modo) -- el bake real (`ScanImageEditor.applyColorMode()`) usa
 * `Bitmap`/`Canvas`/`ColorMatrixColorFilter` de `android.graphics`, mismo
 * límite ya documentado para `ScanImageEditorTest`.
 */
class ScanColorModeTest {

    @Test
    fun `Color es la matriz identidad -- AC3, sin filtro`() {
        val matrix = buildColorModeMatrix(ScanColorMode.COLOR)

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
    fun `Escala de grises usa los mismos coeficientes de luminancia en los 3 canales`() {
        val matrix = buildColorModeMatrix(ScanColorMode.GRAYSCALE)

        // Las 3 filas de color (R, G, B de salida) deben ser idénticas entre
        // sí -- cada canal de salida es la misma combinación de luminancia.
        val row0 = matrix.copyOfRange(0, 5).toList()
        val row1 = matrix.copyOfRange(5, 10).toList()
        val row2 = matrix.copyOfRange(10, 15).toList()
        assertEquals(row0, row1)
        assertEquals(row1, row2)
        // Los 3 coeficientes de color de una fila deben sumar 1 (blanco
        // sigue siendo blanco tras la conversión).
        assertEquals(1f, row0[0] + row0[1] + row0[2], 0.001f)
        // El canal alfa no se toca.
        assertEquals(1f, matrix[18])
    }

    @Test
    fun `Blanco y negro desatura igual que escala de grises pero con contraste mayor`() {
        val grayscale = buildColorModeMatrix(ScanColorMode.GRAYSCALE)
        val blackAndWhite = buildColorModeMatrix(ScanColorMode.BLACK_AND_WHITE)

        // Mismos coeficientes relativos de luminancia (misma dirección),
        // escalados por un factor de contraste mayor a 1.
        val scale = blackAndWhite[0] / grayscale[0]
        assertEquals(scale, blackAndWhite[1] / grayscale[1], 0.001f)
        assertEquals(scale, blackAndWhite[2] / grayscale[2], 0.001f)
        assert(scale > 1f) { "Blanco y negro debe tener más contraste que Escala de grises" }
    }

    @Test
    fun `Resaltar texto sube el contraste y desplaza el punto medio hacia blanco`() {
        val matrix = buildColorModeMatrix(ScanColorMode.HIGHLIGHT_TEXT)

        assert(matrix[0] > 1f) { "debe aumentar el contraste (factor de escala > 1)" }
        assert(matrix[4] > 0f) { "debe desplazar hacia blanco (offset positivo)" }
        // No toca el color -- solo escala uniforme por canal (mismo factor
        // en R/G/B, sin mezclar canales entre sí).
        assertEquals(0f, matrix[1])
        assertEquals(0f, matrix[2])
    }
}
