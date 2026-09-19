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
    darken: Float = 0.3f,
    alpha: Float = 0.4f,
): Modifier {
    val color = lerp(MaterialTheme.colorScheme.primary, Color.Black, darken).copy(alpha = alpha)
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
