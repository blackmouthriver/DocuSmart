package com.docsmart.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Degradado de 3 tonos derivado del "Color de acento" elegido en Ajustes
 * (`MaterialTheme.colorScheme.primary`, ya resuelto al acento + tema
 * claro/oscuro correctos por [DocuSmartTheme]) -- para todos los banners y
 * fondos de la app que antes tenían este mismo efecto codificado con
 * colores fijos (`DocuBlue`/`SmartBlue`/`IndigoAccent`), ignorando el
 * acento elegido (bug real corregido 2026-09-04, primero en el banner de
 * Home, luego extendido acá al resto de banners con el mismo problema).
 */
@Composable
fun rememberAccentGradient(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary) {
        listOf(
            lerp(primary, Color.White, 0.12f),
            primary,
            lerp(primary, Color.Black, 0.22f),
        )
    }
}

private const val MIN_WHITE_TEXT_CONTRAST = 5f
private const val DARKEN_STEP = 0.05f
private const val MAX_DARKEN_STEPS = 20

private fun contrastWithWhite(color: Color): Float = 1.05f / (color.luminance() + 0.05f)

/**
 * Oscurece [color] de a poco hasta que el texto blanco encima tenga contraste
 * suficiente (>= 5:1, margen sobre el 4.5:1 de WCAG AA porque el degradado
 * aclara un poco su primera parada). Rediseño UI 2026-09-20: en modo
 * oscuro `primary` es un tono claro (celeste/cian/etc.) y el texto blanco
 * de los banners quedaba casi ilegible (~2.5:1) -- se veía en Inicio con
 * el acento cian del usuario.
 */
fun darkenForWhiteText(color: Color): Color {
    var result = color
    var steps = 0
    while (contrastWithWhite(result) < MIN_WHITE_TEXT_CONTRAST && steps < MAX_DARKEN_STEPS) {
        result = lerp(result, Color.Black, DARKEN_STEP)
        steps++
    }
    return result
}

/**
 * Igual que [rememberAccentGradient] pero garantiza que el texto blanco
 * puesto encima se lea (para banners con título/subtítulo blancos). El
 * fondo animado y la pastilla de la barra inferior siguen con el degradado
 * original, porque ahí no hay texto blanco encima.
 */
@Composable
fun rememberBannerGradient(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    // En oscuro `primary` es un tono claro y pastel: oscurecerlo hacia negro lo
    // dejaba grisáceo/apagado (visto en el teléfono con el acento cian). El
    // `primaryContainer` del acento ya es la versión profunda y saturada del
    // mismo tono, pensada justo para esto.
    val isDark = scheme.background.luminance() < 0.5f
    val base = if (isDark) scheme.primaryContainer else scheme.primary
    return remember(base) {
        val safe = darkenForWhiteText(base)
        listOf(
            lerp(safe, Color.White, 0.08f),
            safe,
            lerp(safe, Color.Black, 0.22f),
        )
    }
}

private const val MIN_ICON_CONTRAST = 4.5f
private const val ICON_CONTRAST_STEP = 0.05f
private const val MAX_ICON_CONTRAST_STEPS = 20

private fun contrastBetween(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * Ajusta [color] para que un icono dibujado sobre [background] tenga al menos
 * 3:1 de contraste (mínimo WCAG para elementos gráficos): lo aclara en fondos
 * oscuros y lo oscurece en fondos claros. Si ya contrasta lo suficiente, lo
 * devuelve igual. Hallazgo real en el teléfono: el icono de "Firmar PDF"
 * (azul marino fijo) desaparecía sobre el fondo oscuro.
 */
fun ensureIconContrast(
    color: Color,
    background: Color,
): Color {
    val target = if (background.luminance() < 0.5f) Color.White else Color.Black
    var result = color
    var steps = 0
    while (contrastBetween(result, background) < MIN_ICON_CONTRAST && steps < MAX_ICON_CONTRAST_STEPS) {
        result = lerp(result, target, ICON_CONTRAST_STEP)
        steps++
    }
    return result
}

/**
 * Sombra tenue tintada con el Color de acento, para reemplazar la sombra
 * neutra/negra por defecto de `Card` de Material3 (esa API no expone
 * `ambientColor`/`spotColor` para personalizar el color de la sombra --
 * confirmado en material3-android 1.3.1 -- así que hay que dibujarla a
 * mano con `Modifier.shadow()`, igual que ya se hizo en
 * `DocuSmartBottomBar.kt` para el círculo de la pestaña activa). Pedido
 * explícito del usuario 2026-09-06: "añadirle color de acento también a
 * los sombreados... por ejemplo a las card y a las listas" -- acotado a
 * los accesos rápidos de Inicio, las filas de documentos (Biblioteca/
 * Recientes) y las tarjetas de Favoritos (las mismas pantallas donde ya
 * se agregaron las miniaturas), no a las ~30 tarjetas del resto de la
 * app en esta pasada.
 */
@Composable
fun Modifier.accentShadow(
    shape: Shape,
    elevation: Dp = 2.dp,
    alpha: Float = 0.35f,
): Modifier {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    return this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = color,
        spotColor = color,
    )
}

/**
 * Borde tenue tintado con el Color de acento, mismo criterio que ya usa
 * `DocuSmartBottomBar.kt` para el borde superior de la barra
 * (`lerp(accent, Color.Black, 0.3f).copy(alpha = 0.4f)`) -- pedido
 * explícito del usuario 2026-09-06, mismo día: "agregale también a las
 * tarjetas y demás un border como lo que realizaste en la barra de
 * navegación y que esté atada también al cambio de color de acento".
 * Se aplica a las mismas tarjetas/listas que ya usan [accentShadow]
 * (accesos rápidos de Inicio, filas de documentos de Biblioteca/
 * Recientes, tarjetas de Favoritos), no al resto de la app.
 */
@Composable
fun Modifier.accentBorder(
    shape: Shape,
    width: Dp = 1.dp,
    accentMix: Float = 0.25f,
): Modifier {
    // Rediseño UI 2026-09-20: el borde era el acento oscurecido al 40% de
    // opacidad -- con 9 tarjetas juntas (y acentos vivos como el cian) se veía
    // recargado. Ahora es el borde neutro del tema con solo un toque de acento.
    val color = lerp(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.primary, accentMix)
    return this.border(width = width, color = color, shape = shape)
}

/**
 * Colores de `FilterChip` seleccionado con el Color de acento -- por
 * defecto, Material3 usa `colorScheme.secondaryContainer`/
 * `onSecondaryContainer` para el estado seleccionado, y esos dos tokens
 * quedan fijos en un índigo/lavanda (`Theme.kt`, `DocuSmartTheme` solo
 * recolorea `primary`/`onPrimary`/`primaryContainer`/`onPrimaryContainer`)
 * -- así que un chip seleccionado se veía igual sin importar el acento
 * elegido (bug real encontrado 2026-09-06 al revisar los colores del
 * Escáner). Se usa acá el mismo `primaryContainer`/`onPrimaryContainer`
 * que ya usan las tarjetas de acceso rápido y los banners.
 */
@Composable
fun accentFilterChipColors(): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
