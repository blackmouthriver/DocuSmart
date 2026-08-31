package com.docsmart.features.security.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DocuSmartTopBanner

@Composable
fun SecurityMenuScreen(
    onBack        : () -> Unit = {},
    onSecureFolder: () -> Unit = {},
    onPdfPassword : () -> Unit = {},
    viewModel     : SecurityMenuViewModel = hiltViewModel()
) {
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── AdMob — solo para usuarios free (backlog UX §8) ───────────
        if (!isPremium) {
            DocuSmartBannerAd(
                adUnitId  = AdConstants.BANNER_SECURITY_ID,
                adManager = viewModel.adManager
            )
        }

        // Header
        DocuSmartTopBanner(
            screenTitle    = stringResource(R.string.security_title),
            screenSubtitle = stringResource(R.string.security_subtitle),
            onBack         = onBack
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text       = stringResource(R.string.security_what_to_do),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface
        )

        // Tarjeta Carpeta Segura
        SecurityOptionCard(
            icon        = Icons.Rounded.Lock,
            title       = stringResource(R.string.security_secure_folder),
            description = stringResource(R.string.security_secure_folder_desc),
            color       = MaterialTheme.colorScheme.primary,
            badge       = stringResource(R.string.security_pin_required),
            onClick     = onSecureFolder
        )

        // Tarjeta Contraseña PDF
        SecurityOptionCard(
            icon        = Icons.Rounded.Password,
            title       = stringResource(R.string.security_pdf_password),
            description = stringResource(R.string.security_pdf_password_desc),
            color       = MaterialTheme.colorScheme.error,
            badge       = stringResource(R.string.security_no_pin),
            onClick     = onPdfPassword
        )
    }
}

@Composable
private fun SecurityOptionCard(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    title      : String,
    description: String,
    color      : androidx.compose.ui.graphics.Color,
    badge      : String,
    onClick    : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = MaterialTheme.shapes.extraLarge,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Surface(
                shape  = MaterialTheme.shapes.large,
                color  = color.copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text     = badge,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}