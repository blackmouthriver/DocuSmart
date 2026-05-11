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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashMouthBlackScreen(
    onFinished: () -> Unit
) {
    val alpha       = remember { Animatable(0f) }
    val scale       = remember { Animatable(0.82f) }
    val lineProgress = remember { Animatable(0f) }
    val presentaAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Entrada simultánea: fade + scale con rebote suave
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = EaseOutCubic)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            )
        }
        // Línea aparece luego del logo
        delay(550)
        launch {
            lineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = EaseOutCubic)
            )
        }
        delay(200)
        presentaAlpha.animateTo(1f, tween(400))

        // Espera total ~2.5s desde inicio
        delay(1400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            // Ícono empresa con borde sutil
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                // Letra M estilizada
                Text(
                    text = "M",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6)
                )
                // Punto decorativo esquina
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color(0xFF60A5FA))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Nombre de empresa
            Text(
                text = "mouthblack",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "T E C H N O L O G Y",
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF475569),
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Línea decorativa animada
            Box(
                modifier = Modifier
                    .width((100 * lineProgress.value).dp)
                    .height(1.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // "PRESENTA"
            Text(
                text = "P R E S E N T A",
                fontSize = 8.sp,
                color = Color(0xFF334155),
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(presentaAlpha.value)
            )
        }
    }
}