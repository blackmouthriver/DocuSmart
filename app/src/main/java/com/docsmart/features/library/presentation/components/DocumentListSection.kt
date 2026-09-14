package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartDocumentItem
import com.docsmart.core.ui.components.DocuSmartEmptyState
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.RenameDocumentDialog
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.util.shareDocument

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
    onMakeSearchableClick   : ((DocumentUiModel) -> Unit)? = null,
    onSignClick             : ((DocumentUiModel) -> Unit)? = null,
    onMoveToSecureFolderClick: ((DocumentUiModel) -> Unit)? = null,
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
            val shape = MaterialTheme.shapes.large
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .accentShadow(shape = shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .accentBorder(shape = shape)
            ) {
                documents.forEachIndexed { index, document ->
                    val shareLabel = stringResource(R.string.home_share_document, document.name)
                    DocuSmartDocumentItem(
                        document        = document,
                        onClick         = { onDocumentClick(document) },
                        onFavoriteClick = { onFavoriteClick(document.id) },
                        showDivider     = index < documents.size - 1,
                        onOpenClick     = { onDocumentClick(document) },
                        onRenameClick   = if (onRenameClick != null) {
                            { documentToRename = document }
                        } else null,
                        onShareClick    = { shareDocument(context, document, shareLabel) },
                        onConvertClick  = onConvertClick?.let  { cb -> { cb(document) } },
                        onCreateQrClick = onCreateQrClick?.let { cb -> { cb(document) } },
                        onMakeSearchableClick    = onMakeSearchableClick?.let    { cb -> { cb(document) } },
                        onSignClick              = onSignClick?.let              { cb -> { cb(document) } },
                        onMoveToSecureFolderClick = onMoveToSecureFolderClick?.let { cb -> { cb(document) } },
                        onDeleteClick   = onDeleteClick?.let   { cb -> { cb(document.id) } }
                    )
                }
            }
        }
    }
}