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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R

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
                stringResource(R.string.daily_limit_title),
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
                    stringResource(R.string.daily_limit_body, usedCount, limit, itemLabelPlural),
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
                    stringResource(R.string.daily_limit_resets_tomorrow),
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
                        stringResource(
                            if (isRewardedReady) R.string.daily_limit_watch_ad
                            else R.string.daily_limit_ad_not_ready
                        )
                    )
                }
                OutlinedButton(
                    onClick  = onGetPremium,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.daily_limit_get_premium))
                }
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.general_cancel)) }
            }
        },
        dismissButton = {}
    )
}
