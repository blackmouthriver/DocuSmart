package com.docsmart.features.premium.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.*

@Composable
fun PremiumBanner(
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    // Bug real corregido 2026-09-04 (backlog UX §7, HU-UX-06): este
    // degradado estaba fijo en tonos de azul, ignorando el "Color de
    // acento" elegido en Ajustes.
    val bannerGradient = rememberAccentGradient()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(brush = Brush.linearGradient(colors = bannerGradient)),
        contentAlignment = Alignment.Center
    ) {
        // Círculos decorativos
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .background(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 30.dp)
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            if (isPremium) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.premium_you_are_premium),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.premium_enjoy_unlimited),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.settings_premium),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.settings_premium_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}