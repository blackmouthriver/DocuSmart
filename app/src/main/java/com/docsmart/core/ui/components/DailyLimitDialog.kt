package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Diálogo de límite diario alcanzado, compartido entre Conversión y
 * Herramientas PDF (antes vivía duplicado como privado en cada pantalla).
 */
@Composable
fun DailyLimitDialog(
    usedCount      : Int,
    limit          : Int,
    itemLabelPlural: String,
    isRewardedReady: Boolean,
    onWatchAd      : () -> Unit,
    onDismiss      : () -> Unit,
    onGetPremium   : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = MaterialTheme.shapes.extraLarge,
        icon             = {
            Icon(Icons.Rounded.HourglassEmpty, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                "Límite diario alcanzado",
                style     = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Has usado $usedCount de $limit $itemLabelPlural de hoy.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    progress   = { usedCount.toFloat() / limit },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small),
                    color      = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer
                )
                Text(
                    "El límite se reinicia mañana.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = onWatchAd,
                    enabled  = isRewardedReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRewardedReady) "Ver anuncio → +1 uso"
                        else "Anuncio no disponible aún"
                    )
                }
                OutlinedButton(
                    onClick  = onGetPremium,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Obtener Premium → sin límites")
                }
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancelar") }
            }
        },
        dismissButton = {}
    )
}
