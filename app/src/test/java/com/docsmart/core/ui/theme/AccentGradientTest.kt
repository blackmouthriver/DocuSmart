package com.docsmart.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contraste de los acentos y de los helpers de color del rediseño (2026-09-20).
 * Son funciones puras: no necesitan dispositivo.
 */
class AccentGradientTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    fun `todos los acentos en claro superan 4,5 a 1 con texto blanco`() {
        AccentColor.entries.forEach { accent ->
            val ratio = contrast(accent.light.primary, accent.light.onPrimary)
            assertTrue(ratio >= 4.5f, "${accent.label} claro: $ratio")
        }
    }

    @Test
    fun `todos los acentos en oscuro superan 4,5 a 1 entre primary y onPrimary`() {
        AccentColor.entries.forEach { accent ->
            val ratio = contrast(accent.dark.primary, accent.dark.onPrimary)
            assertTrue(ratio >= 4.5f, "${accent.label} oscuro: $ratio")
        }
    }

    @Test
    fun `darkenForWhiteText deja igual un color que ya contrasta`() {
        val dark = Color(0xFF15803D)
        assertEquals(dark, darkenForWhiteText(dark))
    }

    @Test
    fun `darkenForWhiteText oscurece un color claro hasta contrastar con blanco`() {
        val pastel = Color(0xFF67E8F9)
        val result = darkenForWhiteText(pastel)
        assertNotEquals(pastel, result)
        assertTrue(contrast(result, Color.White) >= 5f)
    }

    @Test
    fun `darkenForWhiteText termina aun con el blanco puro`() {
        val result = darkenForWhiteText(Color.White)
        assertTrue(contrast(result, Color.White) >= 3f)
    }

    @Test
    fun `ensureIconContrast no toca un icono que ya contrasta`() {
        val icon = Color(0xFFEF4444)
        val background = Color(0xFFFFFFFF)
        assertEquals(icon, ensureIconContrast(icon, background, minContrast = 3f))
    }

    @Test
    fun `ensureIconContrast aclara un icono oscuro sobre fondo oscuro`() {
        val navy = Color(0xFF0F172A)
        val background = Color(0xFF111729)
        val result = ensureIconContrast(navy, background)
        assertTrue(result.luminance() > navy.luminance())
        assertTrue(contrast(result, background) >= 4.5f)
    }

    @Test
    fun `ensureIconContrast oscurece un icono claro sobre fondo claro`() {
        val gold = Color(0xFFFBBF24)
        val background = Color(0xFFFFFFFF)
        val result = ensureIconContrast(gold, background)
        assertTrue(result.luminance() < gold.luminance())
        assertTrue(contrast(result, background) >= 4.5f)
    }

    @Test
    fun `la escala de espaciado es creciente y respeta el tactil minimo`() {
        val scale =
            listOf(
                DocuSmartSpacing.xs,
                DocuSmartSpacing.sm,
                DocuSmartSpacing.md,
                DocuSmartSpacing.lg,
                DocuSmartSpacing.xl,
                DocuSmartSpacing.xxl,
            )
        assertEquals(scale.sortedBy { it.value }, scale)
        assertEquals(DocuSmartSpacing.lg, DocuSmartSpacing.screenHorizontal)
        assertEquals(48f, DocuSmartSpacing.minTouchTarget.value)
        assertTrue(DocuSmartElevation.none.value < DocuSmartElevation.card.value)
        assertTrue(DocuSmartElevation.card.value < DocuSmartElevation.raised.value)
        assertTrue(DocuSmartElevation.raised.value < DocuSmartElevation.overlay.value)
    }
}
