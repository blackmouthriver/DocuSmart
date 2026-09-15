package com.docsmart.features.scanner.domain

/**
 * HU-45 (backlog UX 2026-08-30/09-14): contraste mínimo entre el color de
 * los módulos del QR y el fondo blanco fijo, para no generar un código
 * probablemente ilegible en condiciones reales de cámara (AC1). Basado en
 * la fórmula de luminancia relativa de WCAG 2.0, sin depender de
 * `android.graphics.Color` -- función pura, testeable como JVM unit test
 * normal (mismo criterio ya usado en `ScanColorMode.kt`/`QrContentFormat.kt`
 * para lógica de dominio que no necesita el framework de Android).
 */

// Umbral mínimo de contraste -- más permisivo que el AA de WCAG para texto
// (4.5:1), pero por debajo de esto un código QR empieza a fallar de forma
// real en condiciones de luz/cámara variables (RNF1: debe seguir siendo
// legible por un lector estándar).
const val QR_MIN_CONTRAST_RATIO = 3.0

/** Extrae los 3 canales de un color empaquetado (0xAARRGGBB o 0xRRGGBB). */
internal fun argbToRgb(argb: Int): Triple<Int, Int, Int> {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return Triple(r, g, b)
}

private fun relativeLuminance(r: Int, g: Int, b: Int): Double {
    fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/**
 * Razón de contraste WCAG entre 2 colores -- 1.0 significa sin contraste
 * (colores idénticos en luminancia), 21.0 es el máximo posible (blanco
 * puro contra negro puro).
 */
fun contrastRatio(colorA: Int, colorB: Int): Double {
    val (ra, ga, ba) = argbToRgb(colorA)
    val (rb, gb, bb) = argbToRgb(colorB)
    val luminanceA = relativeLuminance(ra, ga, ba)
    val luminanceB = relativeLuminance(rb, gb, bb)
    val lighter = maxOf(luminanceA, luminanceB)
    val darker = minOf(luminanceA, luminanceB)
    return (lighter + 0.05) / (darker + 0.05)
}

/** AC1: ¿este color de módulos es lo bastante legible sobre el fondo? */
fun hasSufficientContrast(moduleColor: Int, backgroundColor: Int): Boolean =
    contrastRatio(moduleColor, backgroundColor) >= QR_MIN_CONTRAST_RATIO
