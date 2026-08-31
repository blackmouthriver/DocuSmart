package com.docsmart.features.splash.presentation

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.docsmart.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash de marca de la app (DocuSmart) -- rediseño 2026-08-30, handoff
 * del usuario (`docs/requirements/backlog-mejoras-ux-2026-08-30.md` §13).
 * Reemplaza el logo de dos documentos superpuestos por la opción 2a ("la
 * línea revela"): la mira de 4 esquinas ya está en pantalla y el
 * documento aparece de arriba hacia abajo detrás de una línea de escaneo.
 * `onFinished` mantiene el mismo contrato con `DocuSmartNavGraph` (splash
 * → Onboarding/Home), sin cambios de navegación.
 *
 * Tipografías: familias del sistema como respaldo -- Plus Jakarta Sans
 * ExtraBold / JetBrains Mono quedan pendientes de integrar (backlog).
 */
private object DsBrand {
    val BlueLight = Color(0xFF1E9BFF)
    val BlueMid   = Color(0xFF2563FF)
    val Indigo    = Color(0xFF3B1FE0)
    val White     = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFDFE6FF)
    val Scan      = Color(0xFF7EE0FF)

    val Gradient = Brush.linearGradient(
        colorStops = arrayOf(0f to BlueLight, 0.45f to BlueMid, 1f to Indigo),
        start = Offset(0f, 0f),
        end   = Offset(900f, 1600f)
    )
}

private val Display = FontFamily.SansSerif
private val Mono     = FontFamily.Monospace

/**
 * Marca DocuSmart: mira de 4 esquinas + documento revelado por una línea
 * de escaneo que baja.
 * @param reveal 0f = documento invisible, 1f = documento completo.
 * @param scan   0f = línea arriba (fuera), 1f = línea abajo (fuera).
 */
@Composable
private fun DocusmartMark(
    size    : Dp = 150.dp,
    reveal  : Float = 1f,
    scan    : Float = 1f,
    showScan: Boolean = true,
    modifier: Modifier = Modifier
) {
    val frameH   = size * 1.12f
    val docInsetX = size * 0.147f
    val docInsetY = size * 0.119f
    val bracket   = size * 0.173f
    val stroke    = size * 0.027f

    Box(Modifier.size(size, frameH).then(modifier)) {
        Box(
            modifier = Modifier
                .padding(horizontal = docInsetX, vertical = docInsetY)
                .fillMaxSize()
                .drawWithContent {
                    clipRect(bottom = this.size.height * reveal.coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                }
                .shadow(14.dp, RoundedCornerShape(size * 0.08f))
                .clip(RoundedCornerShape(size * 0.08f))
                .background(DsBrand.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = size * 0.133f),
                verticalArrangement = Arrangement.spacedBy(size * 0.08f)
            ) {
                PaperLine(1f, size)
                PaperLine(1f, size)
                PaperLine(0.62f, size)
            }
        }

        if (showScan) {
            val travel = frameH * (scan.coerceIn(0f, 1f) - 0.5f)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = size * 0.093f)
                    .fillMaxWidth()
                    .height(size * 0.02f)
                    .graphicsLayer { translationY = travel.toPx() }
                    .background(DsBrand.Scan, RoundedCornerShape(50))
            )
        }

        Bracket(Alignment.TopStart, bracket, stroke, RoundedCornerShape(topStart = size * 0.053f))
        Bracket(Alignment.TopEnd, bracket, stroke, RoundedCornerShape(topEnd = size * 0.053f))
        Bracket(Alignment.BottomStart, bracket, stroke, RoundedCornerShape(bottomStart = size * 0.053f))
        Bracket(Alignment.BottomEnd, bracket, stroke, RoundedCornerShape(bottomEnd = size * 0.053f))
    }
}

@Composable
private fun PaperLine(fraction: Float, size: Dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(size * 0.047f)
            .background(DsBrand.PaperLine, RoundedCornerShape(50))
    )
}

@Composable
private fun BoxScope.Bracket(
    align : Alignment,
    len   : Dp,
    stroke: Dp,
    shape : RoundedCornerShape
) {
    val horizontalTop = align == Alignment.TopStart || align == Alignment.TopEnd
    val leftSide       = align == Alignment.TopStart || align == Alignment.BottomStart
    Box(Modifier.align(align).size(len)) {
        Box(
            modifier = Modifier
                .align(if (horizontalTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .height(stroke)
                .background(DsBrand.White, shape)
        )
        Box(
            modifier = Modifier
                .align(if (leftSide) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(stroke)
                .background(DsBrand.White, shape)
        )
    }
}

@Composable
fun SplashDocuSmartScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    val scan   = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val word   = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            launch { scan.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { reveal.animateTo(1f, tween(880, delayMillis = 60, easing = LinearEasing)) }
            launch { word.animateTo(1f, tween(420, delayMillis = 620, easing = FastOutSlowInEasing)) }
            delay(1250)
        }
        delay(250)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(DsBrand.Gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            DocusmartMark(
                size     = 150.dp,
                reveal   = reveal.value,
                scan     = scan.value,
                showScan = !reduceMotion && scan.value < 0.98f
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 24f
                }
            ) {
                Text(
                    text = "docusmart",
                    style = TextStyle(
                        fontFamily    = Display,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color         = DsBrand.White
                    )
                )
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = TextStyle(
                        fontFamily    = Mono,
                        fontSize      = 10.sp,
                        letterSpacing = 2.6.sp,
                        color         = DsBrand.White.copy(alpha = 0.72f)
                    )
                )
            }
        }
    }
}
