package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun RecentDocuments(
    documents: List<DocumentUiModel>,
    // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
    // Media -- G8): `isLoading` ya existía en HomeUiState pero nunca se
    // consultaba acá -- la sección decidía solo por `documents.isEmpty()`.
    // Si loadRecentlyOpened() tarda (almacenamiento externo lento), el
    // usuario veía un instante "Sin documentos recientes" aunque sí
    // tuviera, que luego aparecían de golpe.
    isLoading: Boolean = false,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    onOpenFileClick: () -> Unit = {},
    onConvertClick: ((DocumentUiModel) -> Unit)? = null,
    onCreateQrClick: ((DocumentUiModel) -> Unit)? = null,
    onMakeSearchableClick: ((DocumentUiModel) -> Unit)? = null,
    onSignClick: ((DocumentUiModel) -> Unit)? = null,
    onMoveToSecureFolderClick: ((DocumentUiModel) -> Unit)? = null,
    onDeleteClick: ((String) -> Unit)? = null,
    // ← NUEVO (id, newName)
    onRenameClick: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Estado del dialog de renombrar
    var documentToRename by remember { mutableStateOf<DocumentUiModel?>(null) }

    // Dialog de renombrar
    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm = { newName ->
                onRenameClick?.invoke(doc.id, newName)
                documentToRename = null
            },
            onDismiss = { documentToRename = null },
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Auditoría de testers 2026-09-12 ("botones pequeños"): este
            // TextButton por defecto renderizaba a ~38dp de alto, por debajo
            // del mínimo táctil de Android -- se fuerza explícitamente a
            // 48dp en vez de depender del default del componente.
            TextButton(onClick = onSeeAllClick, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isLoading && documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (documents.isEmpty()) {
            DocuSmartEmptyState(
                icon = Icons.Rounded.FolderOff,
                title = stringResource(R.string.home_no_recent_title),
                description = stringResource(R.string.home_no_recent_desc),
                actionLabel = stringResource(R.string.home_open_file_action),
                onAction = onOpenFileClick,
            )
        } else {
            val shape = MaterialTheme.shapes.large
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .accentShadow(shape = shape)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .accentBorder(shape = shape),
            ) {
                documents.forEachIndexed { index, document ->
                    val shareLabel = stringResource(R.string.home_share_document, document.name)
                    DocuSmartDocumentItem(
                        document = document,
                        onClick = { onDocumentClick(document) },
                        onFavoriteClick = { onFavoriteClick(document.id) },
                        showDivider = index < documents.size - 1,
                        onOpenClick = { onDocumentClick(document) },
                        onConvertClick = onConvertClick?.let { cb -> { cb(document) } },
                        onCreateQrClick = onCreateQrClick?.let { cb -> { cb(document) } },
                        onMakeSearchableClick = onMakeSearchableClick?.let { cb -> { cb(document) } },
                        onSignClick = onSignClick?.let { cb -> { cb(document) } },
                        onMoveToSecureFolderClick = onMoveToSecureFolderClick?.let { cb -> { cb(document) } },
                        onShareClick = { shareDocument(context, document, shareLabel) },
                        onRenameClick =
                            if (onRenameClick != null) {
                                { documentToRename = document }
                            } else {
                                null
                            },
                        onDeleteClick = onDeleteClick?.let { cb -> { cb(document.id) } },
                    )
                }
            }
        }
    }
}
