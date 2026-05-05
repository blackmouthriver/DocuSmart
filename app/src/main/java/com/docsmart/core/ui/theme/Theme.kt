package com.docsmart.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Paleta modo claro ─────────────────────────────────
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

// ── Paleta modo oscuro ────────────────────────────────
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

@Composable
fun DocuSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color disponible desde Android 12 — lo desactivamos
    // para mantener siempre la identidad de marca de DocuSmart
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Hace que la barra de estado y navegación sean transparentes
    // y ajusta el color de los iconos según el tema
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = DocuSmartTypography,
        shapes      = DocuSmartShapes,
        content     = content
    )
}