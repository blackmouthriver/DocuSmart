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
    val onContainer: Color
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
    val dark: AccentTone
) {
    BLUE(
        label = "Azul",
        swatch = DocuBlue,
        light = AccentTone(DocuBlue, Color.White, Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
        dark = AccentTone(PrimaryDark, Color(0xFF082F49), SmartBlue, Color(0xFFDBEAFE))
    ),
    PURPLE(
        label = "Morado",
        swatch = Color(0xFF7C3AED),
        light = AccentTone(Color(0xFF7C3AED), Color.White, Color(0xFFEDE9FE), Color(0xFF4C1D95)),
        dark = AccentTone(Color(0xFFC4B5FD), Color(0xFF2E1065), Color(0xFF5B21B6), Color(0xFFEDE9FE))
    ),
    GREEN(
        label = "Verde",
        swatch = Color(0xFF16A34A),
        light = AccentTone(Color(0xFF16A34A), Color.White, Color(0xFFDCFCE7), Color(0xFF14532D)),
        dark = AccentTone(Color(0xFF86EFAC), Color(0xFF052E16), Color(0xFF166534), Color(0xFFDCFCE7))
    ),
    ORANGE(
        label = "Naranja",
        swatch = Color(0xFFEA580C),
        light = AccentTone(Color(0xFFEA580C), Color.White, Color(0xFFFFEDD5), Color(0xFF7C2D12)),
        dark = AccentTone(Color(0xFFFDBA74), Color(0xFF431407), Color(0xFF9A3412), Color(0xFFFFEDD5))
    ),
    PINK(
        label = "Rosa",
        swatch = Color(0xFFDB2777),
        light = AccentTone(Color(0xFFDB2777), Color.White, Color(0xFFFCE7F3), Color(0xFF831843)),
        dark = AccentTone(Color(0xFFF9A8D4), Color(0xFF500724), Color(0xFF9D174D), Color(0xFFFCE7F3))
    ),
    TEAL(
        label = "Turquesa",
        swatch = Color(0xFF0D9488),
        light = AccentTone(Color(0xFF0D9488), Color.White, Color(0xFFCCFBF1), Color(0xFF134E4A)),
        dark = AccentTone(Color(0xFF5EEAD4), Color(0xFF042F2E), Color(0xFF115E59), Color(0xFFCCFBF1))
    )
}
