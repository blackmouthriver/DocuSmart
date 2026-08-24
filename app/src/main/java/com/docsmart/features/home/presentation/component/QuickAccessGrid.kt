package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.cards.DocuSmartQuickAccessCard
import com.docsmart.core.ui.theme.*

data class QuickAccessItem(
    val icon   : ImageVector,
    val label  : String,
    val color  : Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessGrid(
    onScanClick      : () -> Unit,
    onImageToPdfClick: () -> Unit,
    onSafeBoxClick   : () -> Unit,
    onStudyModeClick : () -> Unit,
    onQrClick        : () -> Unit = {},
    onQrReaderClick  : () -> Unit = {},  // ← leer QR
    onQrCreatorClick : () -> Unit = {},  // ← crear QR
    modifier         : Modifier = Modifier
) {
    var showScannerSheet by remember { mutableStateOf(false) }
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showScannerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScannerSheet = false },
            sheetState       = sheetState,
            containerColor   = MaterialTheme.colorScheme.surface,
            tonalElevation   = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(InfoCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.DocumentScanner,
                            contentDescription = null,
                            tint               = InfoCyan,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text       = "Scanner",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = "Elige una opción",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 20.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(Modifier.height(4.dp))

                // Opción 1 — Escanear documento
                ScannerOption(
                    icon     = Icons.Rounded.DocumentScanner,
                    title    = "Escanear documento",
                    subtitle = "Captura automática con ML Kit",
                    color    = InfoCyan,
                    onClick  = {
                        showScannerSheet = false
                        onScanClick()
                    }
                )

                // Opción 2 — Leer QR ← llama onQrReaderClick
                ScannerOption(
                    icon     = Icons.Rounded.QrCodeScanner,
                    title    = "Leer código QR",
                    subtitle = "Detecta URL, texto y más",
                    color    = MaterialTheme.colorScheme.primary,
                    onClick  = {
                        showScannerSheet = false
                        onQrReaderClick()  // ← correcto
                    }
                )

                // Opción 3 — Crear QR ← llama onQrCreatorClick
                ScannerOption(
                    icon     = Icons.Rounded.QrCode,
                    title    = "Crear código QR",
                    subtitle = "Genera QR para URL, texto, imagen o doc",
                    color    = SuccessGreen,
                    onClick  = {
                        showScannerSheet = false
                        onQrCreatorClick()  // ← correcto
                    }
                )
            }
        }
    }

    val items = listOf(
        QuickAccessItem(
            icon    = Icons.Rounded.DocumentScanner,
            label   = stringResource(R.string.home_scan),
            color   = InfoCyan,
            onClick = { showScannerSheet = true }
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.Image,
            label   = stringResource(R.string.home_img_pdf),
            color   = ColorPdf,
            onClick = onImageToPdfClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.Lock,
            label   = stringResource(R.string.home_security),
            color   = PremiumGold,
            onClick = onSafeBoxClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.MenuBook,
            label   = stringResource(R.string.home_study),
            color   = SuccessGreen,
            onClick = onStudyModeClick
        )
    )

    Column(modifier = modifier) {
        Text(
            text  = stringResource(R.string.home_quick_access),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                DocuSmartQuickAccessCard(
                    icon     = item.icon,
                    label    = item.label,
                    onClick  = item.onClick,
                    iconTint = item.color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScannerOption(
    icon    : ImageVector,
    title   : String,
    subtitle: String,
    color   : Color,
    onClick : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = color,
                modifier           = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector        = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp)
        )
    }
}