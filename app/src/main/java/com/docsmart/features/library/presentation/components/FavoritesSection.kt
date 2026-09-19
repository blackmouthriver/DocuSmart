package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.DocumentContextMenu
import com.docsmart.core.ui.components.DocumentThumbnail
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.RenameDocumentDialog
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.util.shareDocument

@Composable
fun FavoritesSection(
    favorites: List<DocumentUiModel>,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: ((String) -> Unit)? = null,
    onRenameClick: ((String, String) -> Unit)? = null,
    onDeleteClick: ((String) -> Unit)? = null,
    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Baja-Media -- B2): faltaban acá, a diferencia del mismo menú en
    // DocumentListSection/RecentDocuments -- un documento favorito perdía
    // 5 de sus 8 acciones reales solo por verse en esta sección.
    onConvertClick: ((DocumentUiModel) -> Unit)? = null,
    onCreateQrClick: ((DocumentUiModel) -> Unit)? = null,
    onMakeSearchableClick: ((DocumentUiModel) -> Unit)? = null,
    onSignClick: ((DocumentUiModel) -> Unit)? = null,
    onMoveToSecureFolderClick: ((DocumentUiModel) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) return

    val context = LocalContext.current
    var menuDocument by remember { mutableStateOf<DocumentUiModel?>(null) }
    var renameDocument by remember { mutableStateOf<DocumentUiModel?>(null) }

    // Menú contextual
    menuDocument?.let { doc ->
        val shareLabel = stringResource(R.string.home_share_document, doc.name)
        DocumentContextMenu(
            document = doc,
            onDismiss = { menuDocument = null },
            onOpen = {
                menuDocument = null
                onDocumentClick(doc)
            },
            onFavorite = {
                menuDocument = null
                onFavoriteClick?.invoke(doc.id)
            },
            onRename =
                if (onRenameClick != null) {
                    {
                        menuDocument = null
                        renameDocument = doc
                    }
                } else {
                    null
                },
            onConvert =
                onConvertClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc)
                    }
                },
            onCreateQr =
                onCreateQrClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc)
                    }
                },
            onMakeSearchable =
                onMakeSearchableClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc)
                    }
                },
            onSign =
                onSignClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc)
                    }
                },
            onMoveToSecureFolder =
                onMoveToSecureFolderClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc)
                    }
                },
            onShare = {
                menuDocument = null
                shareDocument(context, doc, shareLabel)
            },
            onDelete =
                onDeleteClick?.let { cb ->
                    {
                        menuDocument = null
                        cb(doc.id)
                    }
                },
        )
    }

    // Dialog de renombrar
    renameDocument?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm = { newName ->
                onRenameClick?.invoke(doc.id, newName)
                renameDocument = null
            },
            onDismiss = { renameDocument = null },
        )
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                // Hallazgo real de la revisión general 2026-09-16 (#51):
                // hardcodeado en español, fuera de la limpieza de i18n del
                // 2026-09-14.
                text = stringResource(R.string.library_favorites_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(favorites, key = { it.id }) { doc ->
                FavoriteDocumentCard(
                    document = doc,
                    onClick = { onDocumentClick(doc) },
                    onLongClick = { menuDocument = doc },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteDocumentCard(
    document: DocumentUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .width(150.dp)
                .height(160.dp)
                .accentShadow(shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        // H7 (auditoría de accesibilidad TalkBack 2026-09-18):
                        // sin role, TalkBack no anunciaba esta tarjeta como
                        // accionable.
                        role = Role.Button,
                    )
                    .padding(12.dp),
        ) {
            DocumentThumbnail(
                document = document,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = MaterialTheme.shapes.medium,
                labelStyle = MaterialTheme.typography.titleMedium,
                // H7: el nombre ya es visible en el Text de abajo, dentro de
                // esta misma tarjeta con semántica fusionada.
                showContentDescription = false,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = document.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.labelMedium.fontSize * 1.3,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = document.size,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // H6 (auditoría de accesibilidad TalkBack 2026-09-18): antes esta
        // tarjeta solo abría el menú contextual con un onLongClick, sin
        // ningún botón visible descubrible por TalkBack (a diferencia de
        // DocuSmartDocumentItem, que sí tiene un IconButton "⋮" visible) --
        // se agrega el mismo patrón acá.
        IconButton(
            onClick = onLongClick,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.viewer_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        // Nota: se deja en 40dp (no 48dp como en DocuSmartDocumentItem) para
        // no invadir visualmente la miniatura en esta tarjeta más chica
        // (150x160dp); igual mejora sobre el estado anterior, que no tenía
        // ningún control visible.
    }
}
