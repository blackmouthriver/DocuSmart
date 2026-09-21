package com.docsmart.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cuarteto de colores que Material3 espera para `primary`/`onPrimary`/
 * `primaryContainer`/`onPrimaryContainer` — agrupado en su propia clase
 * (en vez de 4 parámetros sueltos por variante) para no superar el límite
 * de detekt de parámetros por constructor al tener una versión clara y
 * una oscura por [AccentColor].
 */
data class AccentTone(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * RF-SET-07: personalización de color de acento, elegible por el usuario
 * desde Ajustes. Solo recolorea `primary`/`onPrimary`/`primaryContainer`/
 * `onPrimaryContainer` sobre el esquema de color ya existente (claro/
 * oscuro/sistema) -- fondos, superficies, colores de error y los colores
 * semánticos por tipo de archivo (PDF rojo, Word azul, etc., definidos
 * aparte en `Color.kt`) no cambian, para no romper la identidad visual
 * fuera del acento de botones/pestañas seleccionadas/enlaces.
 *
 * `BLUE` reutiliza exactamente los mismos valores que ya tenía la app
 * (`DocuBlue`/`PrimaryDark`/`SmartBlue`) -- elegirlo (el valor por
 * defecto) no cambia nada visualmente para quien nunca toca este ajuste.
 */
enum class AccentColor(
    val label: String,
    val swatch: Color,
    val light: AccentTone,
    val dark: AccentTone,
) {
    BLUE(
        label = "Azul",
        swatch = DocuBlue,
        light = AccentTone(DocuBlue, Color.White, Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
        dark = AccentTone(PrimaryDark, Color(0xFF082F49), SmartBlue, Color(0xFFDBEAFE)),
    ),
    PURPLE(
        label = "Morado",
        swatch = Color(0xFF7C3AED),
        light = AccentTone(Color(0xFF7C3AED), Color.White, Color(0xFFEDE9FE), Color(0xFF4C1D95)),
        dark = AccentTone(Color(0xFFC4B5FD), Color(0xFF2E1065), Color(0xFF5B21B6), Color(0xFFEDE9FE)),
    ),
    GREEN(
        label = "Verde",
        swatch = Color(0xFF15803D),
        light = AccentTone(Color(0xFF15803D), Color.White, Color(0xFFDCFCE7), Color(0xFF14532D)),
        dark = AccentTone(Color(0xFF86EFAC), Color(0xFF052E16), Color(0xFF166534), Color(0xFFDCFCE7)),
    ),
    ORANGE(
        label = "Naranja",
        swatch = Color(0xFFC2410C),
        light = AccentTone(Color(0xFFC2410C), Color.White, Color(0xFFFFEDD5), Color(0xFF7C2D12)),
        dark = AccentTone(Color(0xFFFDBA74), Color(0xFF431407), Color(0xFF9A3412), Color(0xFFFFEDD5)),
    ),
    PINK(
        label = "Rosa",
        swatch = Color(0xFFDB2777),
        light = AccentTone(Color(0xFFDB2777), Color.White, Color(0xFFFCE7F3), Color(0xFF831843)),
        dark = AccentTone(Color(0xFFF9A8D4), Color(0xFF500724), Color(0xFF9D174D), Color(0xFFFCE7F3)),
    ),
    TEAL(
        label = "Turquesa",
        swatch = Color(0xFF0F766E),
        light = AccentTone(Color(0xFF0F766E), Color.White, Color(0xFFCCFBF1), Color(0xFF134E4A)),
        dark = AccentTone(Color(0xFF5EEAD4), Color(0xFF042F2E), Color(0xFF115E59), Color(0xFFCCFBF1)),
    ),

    // Pedido explícito del usuario 2026-09-14: más colores de acento
    // disponibles (rediseño de Ajustes) -- mismo criterio de tono que los 6
    // anteriores (primary 600 con texto blanco en claro, primary 300 con
    // texto oscuro en oscuro).
    INDIGO(
        label = "Índigo",
        swatch = Color(0xFF4F46E5),
        light = AccentTone(Color(0xFF4F46E5), Color.White, Color(0xFFE0E7FF), Color(0xFF312E81)),
        dark = AccentTone(Color(0xFFA5B4FC), Color(0xFF1E1B4B), Color(0xFF4338CA), Color(0xFFE0E7FF)),
    ),
    RED(
        label = "Rojo",
        swatch = Color(0xFFDC2626),
        light = AccentTone(Color(0xFFDC2626), Color.White, Color(0xFFFEE2E2), Color(0xFF7F1D1D)),
        dark = AccentTone(Color(0xFFFCA5A5), Color(0xFF450A0A), Color(0xFFB91C1C), Color(0xFFFEE2E2)),
    ),
    AMBER(
        label = "Ámbar",
        swatch = Color(0xFFB45309),
        light = AccentTone(Color(0xFFB45309), Color.White, Color(0xFFFEF3C7), Color(0xFF78350F)),
        dark = AccentTone(Color(0xFFFCD34D), Color(0xFF451A03), Color(0xFFB45309), Color(0xFFFEF3C7)),
    ),
    CYAN(
        label = "Cian",
        swatch = Color(0xFF0E7490),
        light = AccentTone(Color(0xFF0E7490), Color.White, Color(0xFFCFFAFE), Color(0xFF164E63)),
        dark = AccentTone(Color(0xFF67E8F9), Color(0xFF083344), Color(0xFF0E7490), Color(0xFFCFFAFE)),
    ),
}
