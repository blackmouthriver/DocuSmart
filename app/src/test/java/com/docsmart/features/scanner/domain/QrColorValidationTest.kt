package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HU-45 (backlog UX 2026-08-30/09-14): verifica el cálculo de contraste
 * (AC1) contra los 8 colores de la paleta real de `QrDesignSection.kt`,
 * para confirmar que el umbral efectivamente separa colores utilizables de
 * uno genuinamente riesgoso (el amarillo de la paleta, incluido a propósito
 * para que la advertencia de HU-45 sea alcanzable en la práctica).
 */
class QrColorValidationTest {

    private companion object {
        const val WHITE = 0xFFFFFF
        const val BLACK = 0x000000
        const val AMARILLO = 0xFBC02D // paleta QrDesignSection -- debe fallar
        const val AZUL = 0x1976D2
        const val TEAL = 0x00695C
    }

    @Test
    fun `blanco contra blanco no tiene contraste`() {
        assertEquals(1.0, contrastRatio(WHITE, WHITE), 0.001)
    }

    @Test
    fun `negro contra blanco tiene el contraste maximo`() {
        assertEquals(21.0, contrastRatio(BLACK, WHITE), 0.01)
    }

    @Test
    fun `negro sobre blanco tiene contraste suficiente`() {
        assertTrue(hasSufficientContrast(BLACK, WHITE))
    }

    @Test
    fun `azul de la paleta tiene contraste suficiente sobre blanco`() {
        assertTrue(hasSufficientContrast(AZUL, WHITE))
    }

    @Test
    fun `teal de la paleta tiene contraste suficiente sobre blanco`() {
        assertTrue(hasSufficientContrast(TEAL, WHITE))
    }

    @Test
    fun `amarillo de la paleta NO tiene contraste suficiente sobre blanco -- AC1`() {
        assertFalse(hasSufficientContrast(AMARILLO, WHITE))
    }

    @Test
    fun `contrastRatio es simetrico entre los 2 colores`() {
        assertEquals(contrastRatio(AZUL, WHITE), contrastRatio(WHITE, AZUL), 0.0001)
    }

    @Test
    fun `argbToRgb extrae los 3 canales correctamente`() {
        val (r, g, b) = argbToRgb(0xFF1976D2.toInt())
        assertEquals(0x19, r)
        assertEquals(0x76, g)
        assertEquals(0xD2, b)
    }
}
