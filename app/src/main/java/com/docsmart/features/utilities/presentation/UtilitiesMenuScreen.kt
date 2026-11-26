package com.docsmart.features.utilities.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorExcel
import com.docsmart.core.ui.theme.ColorImage
import com.docsmart.core.ui.theme.ColorPowerPoint
import com.docsmart.core.ui.theme.InfoCyan
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow

@Composable
fun UtilitiesMenuScreen(
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    onHash: () -> Unit = {},
    onPassword: () -> Unit = {},
    onCounter: () -> Unit = {},
    onRename: () -> Unit = {},
) {
    UtilityScaffold(
        title = stringResource(R.string.util_title),
        subtitle = stringResource(R.string.util_subtitle),
        onBack = onBack,
        onHome = onHome,
    ) {
        UtilityMenuCard(Icons.Rounded.Fingerprint, R.string.util_hash, R.string.util_hash_desc, InfoCyan, onHash)
        UtilityMenuCard(
            Icons.Rounded.Password,
            R.string.util_password,
            R.string.util_password_desc,
            ColorPowerPoint,
            onPassword,
        )
        UtilityMenuCard(
            Icons.Rounded.TextFields,
            R.string.util_counter,
            R.string.util_counter_desc,
            ColorExcel,
            onCounter,
        )
        UtilityMenuCard(
            Icons.Rounded.DriveFileRenameOutline,
            R.string.util_rename,
            R.string.util_rename_desc,
            ColorImage,
            onRename,
        )
    }
}

@Composable
private fun UtilityMenuCard(
    icon: ImageVector,
    titleRes: Int,
    descriptionRes: Int,
    color: Color,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .clickable(onClick = onClick)
                .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
