package com.docsmart.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.*

// ── Modelo de datos del documento ────────────────────
data class DocumentUiModel(
    val id: String,
    val name: String,
    val type: DocumentType,
    val size: String,
    val date: String,
    val isFavorite: Boolean = false
)

enum class DocumentType(val label: String, val color: Color) {
    PDF("PDF", ColorPdf),
    WORD("Word", ColorWord),
    EXCEL("Excel", ColorExcel),
    POWERPOINT("PPT", ColorPowerPoint),
    IMAGE("Imagen", ColorImage),
    TEXT("Texto", ColorText),
    ZIP("ZIP", ColorZip),
    OCR("OCR", ColorOcr)
}

// ── Item de documento en lista ────────────────────────
@Composable
fun DocuSmartDocumentItem(
    document: DocumentUiModel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    // ── Fix Sentinel: cachear color para evitar recomposiciones ──
    // color.copy() es una operación costosa — sin remember se
    // recalcula en cada recomposición del Composable
    val iconBackgroundColor = remember(document.type.color) {
        document.type.color.copy(alpha = 0.12f)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Ícono del tipo de archivo ─────────────
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = document.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = document.type.color
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Nombre y metadatos ────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${document.size} · ${document.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ── Botón favorito ────────────────────────
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (document.isFavorite)
                        Icons.Rounded.Favorite
                    else
                        Icons.Rounded.FavoriteBorder,
                    contentDescription = if (document.isFavorite)
                        "Quitar de favoritos"
                    else
                        "Agregar a favoritos",
                    tint = if (document.isFavorite)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Separador opcional ────────────────────────
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

// ── Estado vacío ──────────────────────────────────────
@Composable
fun DocuSmartEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction, shape = MaterialTheme.shapes.medium) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}