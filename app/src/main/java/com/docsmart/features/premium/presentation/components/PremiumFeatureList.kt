package com.docsmart.features.premium.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.*
import com.docsmart.features.premium.domain.model.PremiumFeature

private fun getFeatureIcon(feature: PremiumFeature): ImageVector {
    return when (feature) {
        PremiumFeature.NO_ADS            -> Icons.Rounded.Block
        PremiumFeature.PDF_TO_WORD       -> Icons.Rounded.Description
        PremiumFeature.ADVANCED_OCR      -> Icons.Rounded.Scanner
        PremiumFeature.ADVANCED_COMPRESS -> Icons.Rounded.Compress
        PremiumFeature.UNLIMITED_CONVERT -> Icons.Rounded.AllInclusive
    }
}

@Composable
fun PremiumFeatureList(
    features: List<PremiumFeature>,
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.premium_all_you_get),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        features.forEach { feature ->
            PremiumFeatureItem(
                feature = feature,
                isPremium = isPremium
            )
        }
    }
}

@Composable
private fun PremiumFeatureItem(
    feature: PremiumFeature,
    isPremium: Boolean
) {
    val isUnlocked = feature.isAvailableFree || isPremium

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícono de la función
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (isUnlocked)
                        DocuBlue.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getFeatureIcon(feature),
                contentDescription = null,
                tint = if (isUnlocked)
                    DocuBlue
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Texto
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(feature.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (isUnlocked)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(feature.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Estado bloqueado / desbloqueado
        Icon(
            imageVector = if (isUnlocked)
                Icons.Rounded.CheckCircle
            else
                Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (isUnlocked) SuccessGreen
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}