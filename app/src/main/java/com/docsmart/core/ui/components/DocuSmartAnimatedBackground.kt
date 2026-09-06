package com.docsmart.core.ui.components

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.rememberAccentGradient

/**
 * Fondo animado de la app (pedido por el usuario 2026-09-06, adaptado de un
 * diseño de referencia que aportó). Reutiliza el mismo degradado de acento
 * de [rememberAccentGradient] que ya pintan HomeBanner/DocuSmartTopBanner/
 * etc. -- así el fondo "sigue" el Color de acento elegido en Ajustes
 * automáticamente, sin agregar una opción de personalización aparte, y sin
 * repetir el bug de colores fijos corregido antes en esta misma sesión.
 *
 * Formas geométricas con degradado que derivan muy lento (20-30s por ciclo)
 * para no verse estáticas sin llamar la atención sobre el contenido real.
 * Va como capa 0, detrás del contenido (ver MainActivity) -- puramente
 * decorativo, no intercepta toques.
 *
 * Respeta la accesibilidad: si el sistema tiene "Quitar animaciones"
 * activado (`ANIMATOR_DURATION_SCALE == 0`), las formas se pintan quietas.
 */
@Composable
fun DocuSmartAnimatedBackground(modifier: Modifier = Modifier) {
    val accent = rememberAccentGradient() // [claro, primary, oscuro]
    val base = MaterialTheme.colorScheme.background

    val context = LocalContext.current
    val reduceMotion = remember {
        AndroidSettings.Global.getFloat(
            context.contentResolver,
            AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val transition = rememberInfiniteTransition(label = "docuSmartBg")
    val a by drift(transition, 26_000, "bgDriftA", reduceMotion)
    val b by drift(transition, 30_000, "bgDriftB", reduceMotion)
    val c by drift(transition, 23_000, "bgDriftC", reduceMotion)
    val pulse by drift(transition, 12_000, "bgPulse", reduceMotion)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(base)
    ) {
        val w = maxWidth
        val h = maxHeight

        // Claro → acento, arriba a la izquierda
        AccentSquare(
            size = 168.dp,
            corner = 40.dp,
            colors = listOf(accent[0], accent[1]),
            alpha = 0.16f,
            x = (-24).dp + (18.dp * a),
            y = (-30).dp - (26.dp * a),
            rotation = 8f * a
        )

        // Acento → oscuro, a media altura a la derecha
        AccentSquare(
            size = 190.dp,
            corner = 48.dp,
            colors = listOf(accent[1], accent[2]),
            alpha = 0.14f,
            x = w - 144.dp - (22.dp * b),
            y = 90.dp + (20.dp * b),
            rotation = -6f + 12f * b
        )

        // Claro → oscuro, abajo a la izquierda, respira con escala
        AccentSquare(
            size = 120.dp,
            corner = 30.dp,
            colors = listOf(accent[0], accent[2]),
            alpha = 0.15f,
            x = 40.dp + (14.dp * c),
            y = h - 216.dp + (18.dp * c),
            rotation = 12f - 16f * c,
            scale = 1f + 0.08f * c
        )

        // Destello claro que respira, arriba al centro
        AccentSquare(
            size = 44.dp,
            corner = 13.dp,
            colors = listOf(Color.White, accent[0]),
            alpha = 0.35f + 0.35f * pulse,
            x = w * 0.5f,
            y = 40.dp,
            rotation = 0f,
            shadowColor = accent[1],
            shadowAlpha = 0.25f,
            elevation = 8.dp
        )
    }
}

@Composable
private fun drift(
    transition: InfiniteTransition,
    durationMillis: Int,
    label: String,
    reduceMotion: Boolean
): State<Float> = if (reduceMotion) {
    remember { mutableFloatStateOf(0.5f) }
} else {
    transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = label
    )
}

@Composable
private fun AccentSquare(
    size: Dp,
    corner: Dp,
    colors: List<Color>,
    alpha: Float,
    x: Dp,
    y: Dp,
    rotation: Float,
    scale: Float = 1f,
    shadowColor: Color = colors.last(),
    shadowAlpha: Float = 0.35f,
    elevation: Dp = 20.dp
) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .scale(scale)
            .rotate(rotation)
            .alpha(alpha)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(corner),
                ambientColor = shadowColor.copy(alpha = shadowAlpha),
                spotColor = shadowColor.copy(alpha = shadowAlpha)
            )
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(colors))
    )
}
