package com.docsmart.features.splash.presentation

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash de marca de empresa (mouthblack) -- rediseño 2026-08-30, handoff
 * del usuario (`docs/requirements/backlog-mejoras-ux-2026-08-30.md` §13).
 * Reemplaza el logo dibujado a mano (letra "M" en una tarjeta) por el
 * nuevo círculo mordido + lockup horizontal, manteniendo intacta la
 * integración de navegación existente (`onFinished` es el único contrato
 * con `DocuSmartNavGraph`, sin cambios ahí).
 *
 * Tipografías: se usan las familias del sistema (`SansSerif`/`Monospace`)
 * como respaldo -- Space Grotesk Bold / JetBrains Mono Bold quedan
 * pendientes de integrar (backlog, no bloquea este cambio visual).
 */
private object MbBrand {
    val Black  = Color(0xFF0B0B0B)
    val White  = Color(0xFFFFFFFF)
    val Accent = Color(0xFF35D08A)

    const val BITE_RATIO   = 0.45f
    const val BITE_OUT     = 0.18f
    const val GLYPH_SHIFT  = 0.095f
}

private val WordmarkFamily = FontFamily.SansSerif
private val MonoFamily     = FontFamily.Monospace

/**
 * Ícono de marca: círculo con un "mordisco" recortado en el borde derecho.
 * @param biteProgress 0f = mordisco fuera del círculo (logo intacto),
 *                     1f = posición de reposo, >1f = mordisco más profundo.
 */
@Composable
private fun MouthblackIcon(
    size        : Dp = 84.dp,
    circleColor : Color = MbBrand.White,
    glyphColor  : Color = MbBrand.Black,
    biteProgress: Float = 1f,
    modifier    : Modifier = Modifier
) {
    val bite        = size * MbBrand.BITE_RATIO
    val restOffset  = size * MbBrand.BITE_OUT
    val startOffset = size * 0.88f
    val offsetX     = startOffset + (restOffset - startOffset) * biteProgress

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawCircle(
                    color = Color.Black,
                    radius = bite.toPx() / 2f,
                    center = Offset(
                        x = this.size.width + offsetX.toPx() - bite.toPx() / 2f,
                        y = this.size.height / 2f
                    ),
                    blendMode = BlendMode.Clear
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = true; shape = CircleShape }
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "mb",
                style = TextStyle(
                    fontFamily    = MonoFamily,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = (size.value * 0.40f).sp,
                    letterSpacing = (-size.value * 0.015f).sp,
                    color         = glyphColor,
                    textAlign     = TextAlign.Center
                ),
                modifier = Modifier.graphicsLayer {
                    translationX = -size.toPx() * MbBrand.GLYPH_SHIFT
                }
            )
        }
    }
}

@Composable
private fun MouthblackLockup(
    iconSize      : Dp = 84.dp,
    biteProgress  : Float = 1f,
    showDescriptor: Boolean = true,
    modifier      : Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MouthblackIcon(
            size         = iconSize,
            circleColor  = MbBrand.White,
            glyphColor   = MbBrand.Black,
            biteProgress = biteProgress
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "mouthblack",
                style = TextStyle(
                    fontFamily = WordmarkFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = (iconSize.value * 0.30f).sp,
                    color      = MbBrand.White
                )
            )
            if (showDescriptor) {
                Text(
                    text = "DEV & TECH",
                    style = TextStyle(
                        fontFamily    = MonoFamily,
                        fontSize      = (iconSize.value * 0.12f).sp,
                        letterSpacing = (iconSize.value * 0.029f).sp,
                        color         = MbBrand.Accent
                    )
                )
            }
        }
    }
}

@Composable
fun SplashMouthBlackScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    val scale = remember { Animatable(if (reduceMotion) 1f else 0.35f) }
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val bite  = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        0.35f at 0 using FastOutSlowInEasing
                        1.06f at 300
                        1f at 420
                    }
                )
            }
            launch { alpha.animateTo(1f, tween(320, easing = LinearEasing)) }
            launch {
                bite.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 700
                        0f at 0 using FastOutSlowInEasing
                        1.55f at 220
                        0.45f at 380
                        1.35f at 520
                        1f at 700
                    }
                )
            }
            delay(1150)
        }
        delay(250)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MbBrand.Black),
        contentAlignment = Alignment.Center
    ) {
        MouthblackLockup(
            iconSize     = 84.dp,
            biteProgress = bite.value,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "V1.0",
                style = TextStyle(
                    fontFamily    = MonoFamily,
                    fontSize      = 10.sp,
                    letterSpacing = 2.sp,
                    color         = Color(0xFF4A4A46)
                )
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
