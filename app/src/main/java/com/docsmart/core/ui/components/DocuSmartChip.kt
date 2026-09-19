package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

// ── Chip de filtro estandarizado ──────────────────────────────────────────────
// Tamaño fijo: todos los chips tienen el mismo ancho mínimo y altura
// Ícono con color propio del formato
@Composable
fun DocuSmartFilterChip(
    label: String,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    // ← color del ícono por formato
    iconTint: Color? = null,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelected(!selected) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier =
            modifier
                .height(36.dp) // ← altura fija igual para todos
                .widthIn(min = 118.dp),
        // ← ancho mínimo igual para todos
        leadingIcon =
            if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                null
            },
        shape = MaterialTheme.shapes.medium,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

// ── Chip de tipo de archivo ───────────────────────────────────────────────────
// Uso: badge en items de documento
@Composable
fun DocuSmartFileTypeChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        },
        // H22 (auditoría de accesibilidad TalkBack 2026-09-18): este chip es
        // puramente informativo (badge de tipo de archivo), pero
        // SuggestionChip siempre expone un rol/acción de clic -- TalkBack lo
        // anunciaba como "botón" aunque `onClick = {}` no hace nada.
        // `clearAndSetSemantics` reemplaza la semántica del subárbol
        // (incluida la acción de clic) solo por el texto visible, sin tocar
        // la apariencia visual del chip.
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        shape = MaterialTheme.shapes.extraSmall,
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = color.copy(alpha = 0.12f),
            ),
        border =
            SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = color.copy(alpha = 0.3f),
                borderWidth = 1.dp,
            ),
    )
}
