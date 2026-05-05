package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.cards.DocuSmartQuickAccessCard
import com.docsmart.core.ui.theme.*

data class QuickAccessItem(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun QuickAccessGrid(
    onScanClick: () -> Unit,
    onImageToPdfClick: () -> Unit,
    onSafeBoxClick: () -> Unit,
    onStudyModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        QuickAccessItem(
            icon = Icons.Rounded.DocumentScanner,
            label = "Escanear",
            color = InfoCyan,
            onClick = onScanClick
        ),
        QuickAccessItem(
            icon = Icons.Rounded.Image,
            label = "Img→PDF",
            color = ColorPdf,
            onClick = onImageToPdfClick
        ),
        QuickAccessItem(
            icon = Icons.Rounded.Lock,
            label = "Seguridad",
            color = PremiumGold,
            onClick = onSafeBoxClick
        ),
        QuickAccessItem(
            icon = Icons.Rounded.MenuBook,
            label = "Estudio",
            color = SuccessGreen,
            onClick = onStudyModeClick
        )
    )

    Column(modifier = modifier) {
        Text(
            text = "Accesos rápidos",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                DocuSmartQuickAccessCard(
                    icon = item.icon,
                    label = item.label,
                    onClick = item.onClick,
                    iconTint = item.color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}