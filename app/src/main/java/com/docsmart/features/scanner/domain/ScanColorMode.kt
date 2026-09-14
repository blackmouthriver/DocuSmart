package com.docsmart.features.scanner.domain

/**
 * HU-41 (backlog UX 2026-08-30/09-14): modo de color aplicable a una página
 * escaneada antes de guardar. ML Kit Document Scanner no expone ninguno de
 * estos -- se resuelven como post-proceso propio sobre el bitmap ya
 * capturado, mismo patrón que RF-SCAN-06/07 (brillo/contraste) en
 * [ScanImageEditor]. `COLOR` es el valor por defecto y no aplica ninguna
 * matriz (AC3: cero regresión para quien no toca esta opción).
 */
enum class ScanColorMode {
    COLOR, BLACK_AND_WHITE, GRAYSCALE, HIGHLIGHT_TEXT
}

// Coeficientes de luminancia ITU-R BT.601 -- los mismos que usa
// internamente `android.graphics.ColorMatrix.setSaturation(0f)`.
private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f

private val COLOR_IDENTITY_MATRIX = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

private val GRAYSCALE_MATRIX = floatArrayOf(
    LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
    LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
    LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

// Blanco y negro: parte de la misma desaturación que GRAYSCALE y le suma un
// contraste fuerte (factor 3x anclado al gris medio) para acercar cada
// píxel a blanco o negro puro -- un umbral real por píxel requeriría
// iterar el bitmap entero (más lento, ver RNF1) sin beneficio visual
// perceptible sobre un documento ya escaneado.
private const val BW_CONTRAST_FACTOR = 3f
private const val MID_GRAY = 128f
private const val BW_TRANSLATE = -MID_GRAY * (BW_CONTRAST_FACTOR - 1f)
private val BLACK_AND_WHITE_MATRIX = floatArrayOf(
    LUMA_R * BW_CONTRAST_FACTOR, LUMA_G * BW_CONTRAST_FACTOR, LUMA_B * BW_CONTRAST_FACTOR, 0f, BW_TRANSLATE,
    LUMA_R * BW_CONTRAST_FACTOR, LUMA_G * BW_CONTRAST_FACTOR, LUMA_B * BW_CONTRAST_FACTOR, 0f, BW_TRANSLATE,
    LUMA_R * BW_CONTRAST_FACTOR, LUMA_G * BW_CONTRAST_FACTOR, LUMA_B * BW_CONTRAST_FACTOR, 0f, BW_TRANSLATE,
    0f, 0f, 0f, 1f, 0f
)

// Resaltar texto: contraste moderado desplazado hacia blanco (no anclado
// al gris medio, a diferencia de `buildColorMatrix`) para "lavar" el fondo
// conservando el texto oscuro legible -- pensado para papel amarillento o
// recibos térmicos desteñidos.
private const val HIGHLIGHT_CONTRAST_FACTOR = 1.6f
private const val HIGHLIGHT_BRIGHTNESS_OFFSET = 40f
private val HIGHLIGHT_TEXT_MATRIX = floatArrayOf(
    HIGHLIGHT_CONTRAST_FACTOR, 0f, 0f, 0f, HIGHLIGHT_BRIGHTNESS_OFFSET,
    0f, HIGHLIGHT_CONTRAST_FACTOR, 0f, 0f, HIGHLIGHT_BRIGHTNESS_OFFSET,
    0f, 0f, HIGHLIGHT_CONTRAST_FACTOR, 0f, HIGHLIGHT_BRIGHTNESS_OFFSET,
    0f, 0f, 0f, 1f, 0f
)

/**
 * Matriz de color 4x5 (mismo formato que [buildColorMatrix], reutilizable
 * tanto para el bake real con `ColorMatrixColorFilter` como para la vista
 * previa en Compose con `ColorFilter.colorMatrix`) para cada [ScanColorMode].
 * Función pura -- sin ninguna clase de `android.graphics`, testeable como
 * JVM unit test normal.
 */
fun buildColorModeMatrix(mode: ScanColorMode): FloatArray = when (mode) {
    ScanColorMode.COLOR -> COLOR_IDENTITY_MATRIX
    ScanColorMode.GRAYSCALE -> GRAYSCALE_MATRIX
    ScanColorMode.BLACK_AND_WHITE -> BLACK_AND_WHITE_MATRIX
    ScanColorMode.HIGHLIGHT_TEXT -> HIGHLIGHT_TEXT_MATRIX
}
