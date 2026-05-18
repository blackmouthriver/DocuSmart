package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// ── Chip de filtro estandarizado ──────────────────────────────────────────────
// Tamaño fijo: todos los chips tienen el mismo ancho mínimo y altura
// Ícono con color propio del formato
@Composable
fun DocuSmartFilterChip(
    label      : String,
    selected   : Boolean,
    onSelected : (Boolean) -> Unit,
    modifier   : Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    iconTint   : Color? = null        // ← color del ícono por formato
) {
    FilterChip(
        selected = selected,
        onClick  = { onSelected(!selected) },
        label    = {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        modifier = modifier
            .height(36.dp)            // ← altura fija igual para todos
            .widthIn(min = 118.dp),    // ← ancho mínimo igual para todos
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector        = leadingIcon,
                    contentDescription = null,
                    tint               = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(16.dp)
                )
            }
        } else null,
        shape  = MaterialTheme.shapes.medium,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor   = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor       = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ── Chip de tipo de archivo ───────────────────────────────────────────────────
// Uso: badge en items de documento
@Composable
fun DocuSmartFileTypeChip(
    label   : String,
    color   : Color,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        onClick = {},
        label   = {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        },
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraSmall,
        colors   = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.12f)
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled     = true,
            borderColor = color.copy(alpha = 0.3f),
            borderWidth = 1.dp
        )
    )
}