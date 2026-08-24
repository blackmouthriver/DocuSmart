package com.docsmart.features.home.presentation.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocuSmartDocumentItem
import com.docsmart.core.ui.components.DocuSmartEmptyState
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.RenameDocumentDialog

@Composable
fun RecentDocuments(
    documents      : List<DocumentUiModel>,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onSeeAllClick  : () -> Unit,
    onOpenFileClick: () -> Unit = {},
    onConvertClick : ((DocumentUiModel) -> Unit)? = null,
    onDeleteClick  : ((String) -> Unit)? = null,
    onRenameClick  : ((String, String) -> Unit)? = null,  // ← NUEVO (id, newName)
    modifier       : Modifier = Modifier
) {
    val context = LocalContext.current

    // Estado del dialog de renombrar
    var documentToRename by remember { mutableStateOf<DocumentUiModel?>(null) }

    // Dialog de renombrar
    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm   = { newName ->
                onRenameClick?.invoke(doc.id, newName)
                documentToRename = null
            },
            onDismiss   = { documentToRename = null }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "Recientes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAllClick) {
                Text(
                    text  = "Ver todos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (documents.isEmpty()) {
            DocuSmartEmptyState(
                icon        = Icons.Rounded.FolderOff,
                title       = "Sin documentos recientes",
                description = "Abre un archivo para verlo aquí",
                actionLabel = "Abrir archivo",
                onAction    = onOpenFileClick
            )
        } else {
            Card(
                shape  = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                documents.forEachIndexed { index, document ->
                    DocuSmartDocumentItem(
                        document        = document,
                        onClick         = { onDocumentClick(document) },
                        onFavoriteClick = { onFavoriteClick(document.id) },
                        showDivider     = index < documents.size - 1,
                        onOpenClick     = { onDocumentClick(document) },
                        onConvertClick  = onConvertClick?.let { cb -> { cb(document) } },
                        onShareClick    = { shareDocument(context, document) },
                        onRenameClick   = if (onRenameClick != null) {
                            { documentToRename = document }
                        } else null,
                        onDeleteClick   = onDeleteClick?.let { cb -> { cb(document.id) } }
                    )
                }
            }
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
                context, "${context.packageName}.provider", file
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