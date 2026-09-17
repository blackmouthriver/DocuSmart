package com.docsmart.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Hallazgo real de la auditoría general 2026-09-17 (quinta pasada, A2):
// el multiplicador propio de "Tamaño de letra" (hasta 1.3x) se componía
// multiplicativamente con el font-scale de accesibilidad del sistema
// (hasta 1.3x-2x según OEM) sin ningún límite superior -- en un
// dispositivo con accesibilidad alta + "Muy grande" el texto final podía
// llegar a ~2.5x-3x el tamaño base, agravando cualquier layout ajustado
// (ej. el propio selector de tamaño de letra, A1).
private const val MAX_COMBINED_FONT_SCALE = 1.8f

private val LightColorScheme = lightColorScheme(
    primary              = DocuBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFDBEAFE),
    onPrimaryContainer   = Color(0xFF1E3A8A),
    secondary            = IndigoAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary             = InfoCyan,
    onTertiary           = Color.White,
    background           = BackgroundLight,
    onBackground         = NavyDark,
    surface              = SurfaceWhite,
    onSurface            = NavyDark,
    surfaceVariant       = Color(0xFFF1F5F9),
    onSurfaceVariant     = SlateGray,
    outline              = SoftBorder,
    outlineVariant       = Color(0xFFCBD5E1),
    error                = ErrorRed,
    onError              = Color.White,
    errorContainer       = Color(0xFFFEE2E2),
    onErrorContainer     = Color(0xFFB91C1C),
    scrim                = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary              = PrimaryDark,
    onPrimary            = Color(0xFF082F49),
    primaryContainer     = SmartBlue,
    onPrimaryContainer   = Color(0xFFDBEAFE),
    secondary            = SecondaryDark,
    onSecondary          = Color(0xFF1E1B4B),
    secondaryContainer   = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary             = InfoCyan,
    onTertiary           = Color(0xFF003543),
    background           = BackgroundDark,
    onBackground         = TextDark,
    surface              = SurfaceDark,
    onSurface            = TextDark,
    surfaceVariant       = CardDark,
    onSurfaceVariant     = TextDarkMuted,
    outline              = OutlineDark,
    outlineVariant       = Color(0xFF1E293B),
    error                = Color(0xFFF87171),
    onError              = Color(0xFF450A0A),
    errorContainer       = Color(0xFF7F1D1D),
    onErrorContainer     = Color(0xFFFECACA),
    scrim                = Color(0xFF000000)
)

// ── Tema Sistema — azul grisáceo suave ────────────────
// Diferente al claro (blanco) y al oscuro (negro azuloso)
// Usa fondos con tinte azulado de la marca DocuSmart
private val SystemColorScheme = lightColorScheme(
    primary              = DocuBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFDBEAFE),
    onPrimaryContainer   = Color(0xFF1E3A8A),
    secondary            = IndigoAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary             = InfoCyan,
    onTertiary           = Color.White,
    background           = BackgroundSystem,   // ← azul grisáceo
    onBackground         = NavyDark,
    surface              = SurfaceSystem,      // ← superficies azuladas
    onSurface            = NavyDark,
    surfaceVariant       = Color(0xFFD6E4F5),  // ← variante azulada
    onSurfaceVariant     = SlateGray,
    outline              = OutlineSystem,      // ← bordes azul grisáceo
    outlineVariant       = Color(0xFFC5D8EE),
    error                = ErrorRed,
    onError              = Color.White,
    errorContainer       = Color(0xFFFEE2E2),
    onErrorContainer     = Color(0xFFB91C1C),
    scrim                = Color(0xFF000000)
)

@Composable
fun DocuSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useSystemTheme: Boolean = false,
    dynamicColor: Boolean = false,
    accentColor: AccentColor = AccentColor.BLUE,
    // HU-UX-05 (backlog UX 2026-08-30): tamaño de letra ajustable.
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // ── Tema Sistema — azul grisáceo ──────────────
        useSystemTheme -> SystemColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // RF-SET-07: el color de acento solo recolorea primary/onPrimary/
    // primaryContainer/onPrimaryContainer -- fondos, superficies y colores
    // de error quedan intactos sin importar el acento elegido. No se aplica
    // sobre Material You dinámico (dynamicColor): ahí el acento ya lo elige
    // el propio wallpaper del sistema, no tendría sentido pisarlo.
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseColorScheme
    } else {
        val tone = if (darkTheme) accentColor.dark else accentColor.light
        baseColorScheme.copy(
            primary = tone.primary,
            onPrimary = tone.onPrimary,
            primaryContainer = tone.container,
            onPrimaryContainer = tone.onContainer
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
                ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Se reduce el multiplicador propio (nunca por debajo de 1x, el
    // tamaño "Normal") lo necesario para que el producto con el
    // font-scale del sistema no supere el techo combinado. Deliberado: si
    // el font-scale de accesibilidad del SISTEMA ya excede el techo por sí
    // solo, no se lo reduce por debajo de 1x para compensar -- sería
    // pisar una decisión de accesibilidad del usuario tomada fuera de la
    // app. El techo real que se garantiza es "nuestro propio multiplicador
    // no agrava una situación ya extrema", no un límite absoluto del
    // producto en cualquier escenario del sistema.
    // Hallazgo real de la revisión adversarial de correctitud sobre este
    // mismo fix: `coerceIn(1f, fontScale)` asume `fontScale >= 1f` --
    // válido hoy (FontScale solo tiene 1.0/1.15/1.3), pero si algún día se
    // agrega una opción menor a 1x sin tocar esta fórmula, `coerceIn` con
    // un rango inválido (mínimo > máximo) lanza `IllegalArgumentException`
    // y tira abajo toda la app. `coerceAtMost`+`coerceAtLeast` encadenados
    // dan el mismo resultado hoy sin ese riesgo latente.
    val systemFontScale = LocalDensity.current.fontScale
    val cappedFontScale = if (systemFontScale > 0f) {
        (MAX_COMBINED_FONT_SCALE / systemFontScale).coerceAtMost(fontScale).coerceAtLeast(1f)
    } else {
        fontScale
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = DocuSmartTypography.scaledBy(cappedFontScale),
        shapes      = DocuSmartShapes,
        content     = content
    )
}