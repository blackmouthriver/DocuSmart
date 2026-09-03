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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.*

data class DocumentUiModel(
    val id        : String,
    val name      : String,
    val type      : DocumentType,
    val size      : String,
    val date      : String,
    val isFavorite: Boolean = false,
    // Bytes reales detrás de `size` (ya formateado para mostrar) -- 0L por
    // defecto para no romper los call sites que no lo necesitan hoy.
    // Agregado para H5 (backlog-mejoras-ux-2026-08-30.md §12): sumar el
    // espacio real ocupado en la Papelera sin tener que re-parsear el
    // string formateado (frágil entre locales por el separador decimal).
    val sizeBytes : Long = 0L
)

enum class DocumentType(val label: String, val color: Color) {
    PDF        ("PDF",    ColorPdf),
    WORD       ("Word",   ColorWord),
    EXCEL      ("Excel",  ColorExcel),
    POWERPOINT ("PPT",    ColorPowerPoint),
    IMAGE      ("Imagen", ColorImage),
    TEXT       ("Texto",  ColorText),
    ZIP        ("ZIP",    ColorZip),
    OCR        ("OCR",    ColorOcr)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocuSmartDocumentItem(
    document       : DocumentUiModel,
    onClick        : () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier       : Modifier = Modifier,
    showDivider    : Boolean = true,
    onOpenClick    : (() -> Unit)? = null,
    onConvertClick : (() -> Unit)? = null,
    onCreateQrClick: (() -> Unit)? = null,
    onShareClick   : (() -> Unit)? = null,
    onRenameClick  : (() -> Unit)? = null,
    onDeleteClick  : (() -> Unit)? = null
) {
    val iconBg = remember(document.type.color) {
        document.type.color.copy(alpha = 0.12f)
    }

    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        DocumentContextMenu(
            document   = document,
            onDismiss  = { showMenu = false },
            onOpen     = { showMenu = false; (onOpenClick ?: onClick)() },
            onFavorite = { showMenu = false; onFavoriteClick() },
            onRename   = onRenameClick?.let   { a -> { showMenu = false; a() } },
            onConvert  = onConvertClick?.let  { a -> { showMenu = false; a() } },
            onCreateQr = onCreateQrClick?.let { a -> { showMenu = false; a() } },
            onShare    = onShareClick?.let    { a -> { showMenu = false; a() } },
            onDelete   = onDeleteClick?.let   { a -> { showMenu = false; a() } }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = document.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = document.type.color
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = document.name,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = "${document.size} · ${document.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector        = if (document.isFavorite) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (document.isFavorite) stringResource(R.string.doc_item_remove_favorite)
                    else stringResource(R.string.doc_item_add_favorite),
                    tint               = if (document.isFavorite) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector        = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.viewer_more_options),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentContextMenu(
    document  : DocumentUiModel,
    onDismiss : () -> Unit,
    onOpen    : () -> Unit,
    onFavorite: () -> Unit,
    onRename  : (() -> Unit)? = null,
    onConvert : (() -> Unit)? = null,
    onCreateQr: (() -> Unit)? = null,
    onShare   : (() -> Unit)? = null,
    onDelete  : (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            DocumentContextMenuHeader(document)

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )

            ContextMenuItem(
                icon    = Icons.Rounded.OpenInNew,
                label   = stringResource(R.string.qr_open_document),
                onClick = onOpen
            )
            ContextMenuItem(
                icon    = if (document.isFavorite) Icons.Rounded.Favorite
                else Icons.Rounded.FavoriteBorder,
                label   = if (document.isFavorite) stringResource(R.string.doc_item_remove_favorite)
                else stringResource(R.string.doc_item_add_favorite),
                tint    = if (document.isFavorite) MaterialTheme.colorScheme.error else null,
                onClick = onFavorite
            )
            OptionalContextMenuItems(onRename, onConvert, onCreateQr, onShare, onDelete)
        }
    }
}

@Composable
private fun DocumentContextMenuHeader(document: DocumentUiModel) {
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
                text  = document.type.label,
                style = MaterialTheme.typography.labelSmall,
                color = document.type.color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = document.name,
                style    = MaterialTheme.typography.titleSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${document.size} · ${document.date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OptionalContextMenuItems(
    onRename  : (() -> Unit)?,
    onConvert : (() -> Unit)?,
    onCreateQr: (() -> Unit)?,
    onShare   : (() -> Unit)?,
    onDelete  : (() -> Unit)?
) {
    if (onRename != null) {
        ContextMenuItem(
            icon    = Icons.Rounded.DriveFileRenameOutline,
            label   = stringResource(R.string.viewer_rename),
            onClick = onRename
        )
    }
    if (onConvert != null) {
        ContextMenuItem(
            icon    = Icons.Rounded.SwapHoriz,
            label   = stringResource(R.string.viewer_convert),
            onClick = onConvert
        )
    }
    if (onCreateQr != null) {
        ContextMenuItem(
            icon    = Icons.Rounded.QrCode,
            label   = stringResource(R.string.viewer_create_qr),
            onClick = onCreateQr
        )
    }
    if (onShare != null) {
        ContextMenuItem(
            icon    = Icons.Rounded.Share,
            label   = stringResource(R.string.general_share),
            onClick = onShare
        )
    }
    if (onDelete != null) {
        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant
        )
        ContextMenuItem(
            icon    = Icons.Rounded.DeleteOutline,
            label   = stringResource(R.string.general_delete),
            tint    = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

@Composable
private fun ContextMenuItem(
    icon   : ImageVector,
    label  : String,
    tint   : Color? = null,
    onClick: () -> Unit
) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = color,
            modifier           = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}

@Composable
fun RenameDocumentDialog(
    currentName: String,
    onConfirm  : (String) -> Unit,
    onDismiss  : () -> Unit
) {
    val dotIndex  = currentName.lastIndexOf('.')
    val nameOnly  = if (dotIndex > 0) currentName.substring(0, dotIndex) else currentName
    val extension = if (dotIndex > 0) currentName.substring(dotIndex) else ""

    var textValue by remember { mutableStateOf(nameOnly) }
    val isValid   = textValue.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = MaterialTheme.shapes.large,
        title = {
            Text(
                text  = stringResource(R.string.doc_item_rename_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            OutlinedTextField(
                value          = textValue,
                onValueChange  = { textValue = it },
                label          = { Text(stringResource(R.string.viewer_rename_label)) },
                suffix         = if (extension.isNotEmpty()) {
                    { Text(extension, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else null,
                singleLine     = true,
                isError        = !isValid,
                supportingText = if (!isValid) {
                    { Text(stringResource(R.string.viewer_rename_empty_error)) }
                } else null,
                shape    = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(textValue.trim() + extension) },
                enabled = isValid
            ) { Text(stringResource(R.string.viewer_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

@Composable
fun DocuSmartEmptyState(
    icon        : ImageVector,
    title       : String,
    description : String,
    actionLabel : String? = null,
    onAction    : (() -> Unit)? = null,
    modifier    : Modifier = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier           = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = description,
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