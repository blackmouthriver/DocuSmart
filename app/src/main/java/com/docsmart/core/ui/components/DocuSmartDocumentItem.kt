package com.docsmart.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.*

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

// Modelo para las acciones del menú contextual
data class DocumentAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocuSmartDocumentItem(
    document: DocumentUiModel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    // Callbacks nuevos para el menú contextual
    onOpenClick: (() -> Unit)? = null,
    onConvertClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val iconBackgroundColor = remember(document.type.color) {
        document.type.color.copy(alpha = 0.12f)
    }

    // Estado que controla si el bottom sheet está visible
    var showMenu by remember { mutableStateOf(false) }

    // Bottom sheet del menú contextual
    if (showMenu) {
        DocumentContextMenu(
            document = document,
            onDismiss = { showMenu = false },
            onOpen = {
                showMenu = false
                (onOpenClick ?: onClick)()
            },
            onFavorite = {
                showMenu = false
                onFavoriteClick()
            },
            onConvert = onConvertClick?.let { action ->
                {
                    showMenu = false
                    action()
                }
            },
            onShare = onShareClick?.let { action ->
                {
                    showMenu = false
                    action()
                }
            },
            onDelete = onDeleteClick?.let { action ->
                {
                    showMenu = false
                    action()
                }
            }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // combinedClickable: tap normal abre, long press abre el menú
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícono de tipo de archivo
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

            // Nombre y metadata
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

            Spacer(modifier = Modifier.width(4.dp))

            // Corazón favorito
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

            // Botón tres puntos ⋮
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

// ── Bottom Sheet del menú contextual ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentContextMenu(
    document: DocumentUiModel,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onConvert: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Cabecera con nombre del archivo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(document.type.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = document.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = document.type.color
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${document.size} · ${document.date}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Acciones
            ContextMenuItem(
                icon = Icons.Rounded.OpenInNew,
                label = "Abrir",
                onClick = onOpen
            )

            ContextMenuItem(
                icon = if (document.isFavorite)
                    Icons.Rounded.Favorite
                else
                    Icons.Rounded.FavoriteBorder,
                label = if (document.isFavorite)
                    "Quitar de favoritos"
                else
                    "Agregar a favoritos",
                tint = if (document.isFavorite)
                    MaterialTheme.colorScheme.error
                else
                    null,
                onClick = onFavorite
            )

            if (onConvert != null) {
                ContextMenuItem(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "Convertir",
                    onClick = onConvert
                )
            }

            if (onShare != null) {
                ContextMenuItem(
                    icon = Icons.Rounded.Share,
                    label = "Compartir",
                    onClick = onShare
                )
            }

            if (onDelete != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ContextMenuItem(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Eliminar del historial",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val itemColor = tint ?: MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = itemColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = itemColor
        )
    }
}

// ── Empty State (sin cambios) ─────────────────────────────────────────────────

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