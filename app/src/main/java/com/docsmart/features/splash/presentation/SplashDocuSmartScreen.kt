package com.docsmart.features.splash.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashDocuSmartScreen(
    onFinished: () -> Unit
) {
    val iconScale    = remember { Animatable(0f) }
    val iconAlpha    = remember { Animatable(0f) }
    val textAlpha    = remember { Animatable(0f) }
    val textSlide    = remember { Animatable(24f) }
    val dotsAlpha    = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Ícono entra con bounce
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            )
        }
        launch {
            iconAlpha.animateTo(1f, tween(400))
        }

        // 2. Nombre sube desde abajo
        delay(300)
        launch {
            textAlpha.animateTo(1f, tween(500))
        }
        launch {
            textSlide.animateTo(
                targetValue = 0f,
                animationSpec = tween(500, easing = EaseOutCubic)
            )
        }

        // 3. Tagline
        delay(500)
        taglineAlpha.animateTo(1f, tween(400))

        // 4. Dots indicadores
        delay(200)
        dotsAlpha.animateTo(1f, tween(300))

        // Total ~1.5s + tiempo acumulado de delays anteriores
        delay(500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF1D4ED8),
                        0.5f to Color(0xFF2563EB),
                        1.0f to Color(0xFF4338CA)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ícono app
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(iconScale.value)
                    .alpha(iconAlpha.value)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                // Dos documentos superpuestos (representación del logo)
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-6).dp, y = 6.dp)
                            .size(38.dp, 46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(38.dp, 46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                    ) {
                        // Líneas simulando texto
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(4) { i ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (i == 3) 0.55f else 0.85f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF2563EB).copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Nombre DocuSmart
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )) { append("Docu") }
                    withStyle(SpanStyle(
                        color = Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Light
                    )) { append("Smart") }
                },
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = textSlide.value.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tagline
            Text(
                text = "gestión inteligente de documentos",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(taglineAlpha.value)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Indicadores de página
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(dotsAlpha.value)
            ) {
                // Dot activo (largo)
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
                // Dots inactivos
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}