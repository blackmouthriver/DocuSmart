package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    onRenameClick  : ((String, String) -> Unit)? = null,  // ← NUEVO
    modifier       : Modifier = Modifier
) {
    var documentToRename by remember { mutableStateOf<DocumentUiModel?>(null) }

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
                        } else null
                    )
                }
            }
        }
    }
}