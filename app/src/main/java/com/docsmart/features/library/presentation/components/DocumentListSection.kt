package com.docsmart.features.library.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
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
fun DocumentListSection(
    documents      : List<DocumentUiModel>,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: (String) -> Unit,
    searchQuery    : String,
    onRenameClick  : ((String, String) -> Unit)? = null,
    onDeleteClick  : ((String) -> Unit)? = null,
    onConvertClick : ((DocumentUiModel) -> Unit)? = null,
    onCreateQrClick: ((DocumentUiModel) -> Unit)? = null,
    modifier       : Modifier = Modifier
) {
    val context = LocalContext.current
    var documentToRename by remember { mutableStateOf<DocumentUiModel?>(null) }

    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm   = { newName ->
                onRenameClick?.invoke(doc.id, newName)
                documentToRename = null
            },
            onDismiss = { documentToRename = null }
        )
    }

    Column(modifier = modifier) {
        Text(
            text = if (searchQuery.isBlank()) "${documents.size} documentos"
            else "${documents.size} resultados para \"$searchQuery\"",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (documents.isEmpty()) {
            DocuSmartEmptyState(
                icon        = Icons.Rounded.SearchOff,
                title       = "Sin resultados",
                description = if (searchQuery.isBlank()) "No hay documentos en esta categoría"
                else "No encontramos \"$searchQuery\"",
                modifier    = Modifier.padding(top = 32.dp)
            )
        } else {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(
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
                        onRenameClick   = if (onRenameClick != null) {
                            { documentToRename = document }
                        } else null,
                        onShareClick    = { shareDocument(context, document) },
                        onConvertClick  = onConvertClick?.let  { cb -> { cb(document) } },
                        onCreateQrClick = onCreateQrClick?.let { cb -> { cb(document) } },
                        onDeleteClick   = onDeleteClick?.let   { cb -> { cb(document.id) } }
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