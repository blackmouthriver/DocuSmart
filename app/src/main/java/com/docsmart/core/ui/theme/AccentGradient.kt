package com.docsmart.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
            lerp(primary, Color.Black, 0.22f)
        )
    }
}
