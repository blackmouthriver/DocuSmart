package com.docsmart.features.library.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocumentContextMenu
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.RenameDocumentDialog

@Composable
fun FavoritesSection(
    favorites      : List<DocumentUiModel>,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: ((String) -> Unit)? = null,
    onRenameClick  : ((String, String) -> Unit)? = null,
    onDeleteClick  : ((String) -> Unit)? = null,
    modifier       : Modifier = Modifier
) {
    if (favorites.isEmpty()) return

    val context = LocalContext.current
    var menuDocument   by remember { mutableStateOf<DocumentUiModel?>(null) }
    var renameDocument by remember { mutableStateOf<DocumentUiModel?>(null) }

    // Menú contextual
    menuDocument?.let { doc ->
        DocumentContextMenu(
            document   = doc,
            onDismiss  = { menuDocument = null },
            onOpen     = { menuDocument = null; onDocumentClick(doc) },
            onFavorite = { menuDocument = null; onFavoriteClick?.invoke(doc.id) },
            onRename   = if (onRenameClick != null) {
                { menuDocument = null; renameDocument = doc }
            } else null,
            onShare    = {
                menuDocument = null
                shareDocument(context, doc)
            },
            onDelete   = onDeleteClick?.let { cb ->
                { menuDocument = null; cb(doc.id) }
            }
        )
    }

    // Dialog de renombrar
    renameDocument?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm   = { newName ->
                onRenameClick?.invoke(doc.id, newName)
                renameDocument = null
            },
            onDismiss = { renameDocument = null }
        )
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier              = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.Favorite,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.error,
                modifier           = Modifier.size(16.dp)
            )
            Text(
                text  = "Favoritos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding        = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(favorites, key = { it.id }) { doc ->
                FavoriteDocumentCard(
                    document  = doc,
                    onClick   = { onDocumentClick(doc) },
                    onLongClick = { menuDocument = doc }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteDocumentCard(
    document   : DocumentUiModel,
    onClick    : () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(160.dp),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(document.type.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = document.type.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = document.type.color
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text     = document.name,
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.labelMedium.fontSize * 1.3
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text  = document.size,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shareDocument(context: Context, document: DocumentUiModel) {
    try {
        val uri    = Uri.parse(document.id)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, document.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ${document.name}"))
    } catch (e: Exception) {
        try {
            val file = java.io.File(document.id)
            val uri  = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir ${document.name}"))
        } catch (e2: Exception) { e2.printStackTrace() }
    }
}