package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `IconButton` con un tooltip (mantener presionado) que explica su función --
 * feedback de testers 2026-09-12: varios íconos sin texto visible (ej. "ver
 * documento"/"compartir" en un lote de conversión) no se entendían a primera
 * vista. Primer uso de `TooltipBox` en el proyecto, por eso queda como un
 * componente compartido en vez de repetir el boilerplate en cada pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    tooltipText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = tooltipText,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
