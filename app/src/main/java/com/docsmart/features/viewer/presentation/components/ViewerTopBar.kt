package com.docsmart.features.viewer.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ViewerTopBar(
    fileName: String,
    isFavorite: Boolean,
    visible: Boolean,
    onBackClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onSearchClick: () -> Unit,
    onConvertClick: () -> Unit,
    onCreateQrClick: () -> Unit,
    // Backlog UX 2026-08-30/09-10 (HU-42): mismo criterio de
    // DocumentContextMenu -- "Hacer buscable"/"Firmar" solo tienen sentido
    // para un PDF real, así que se gatean con `isPdf`. "Mover a Carpeta
    // Segura" no tiene esa restricción.
    isPdf: Boolean,
    onMakeSearchableClick: () -> Unit,
    onSignClick: () -> Unit,
    onMoveToSecureFolderClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón atrás
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.viewer_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Nombre del archivo
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Buscar
                // Bug real encontrado 2026-09-14 (repaso general): estas 3
                // content descriptions estaban hardcodeadas en español.
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.viewer_search_content_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Favorito
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (isFavorite)
                            Icons.Rounded.Favorite
                        else
                            Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.viewer_favorite_content_desc),
                        tint = if (isFavorite)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Compartir
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.general_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Más opciones (renombrar/eliminar/OCR/firmar/Carpeta Segura)
                // — RF-VIS-06/HU-42
                ViewerMoreOptionsMenu(
                    isPdf   = isPdf,
                    actions = ViewerMenuActions(
                        onConvert            = onConvertClick,
                        onCreateQr           = onCreateQrClick,
                        onMakeSearchable     = onMakeSearchableClick,
                        onSign               = onSignClick,
                        onMoveToSecureFolder = onMoveToSecureFolderClick,
                        onRename             = onRenameClick,
                        onDelete             = onDeleteClick
                    )
                )
            }
        }
    }
}

// Extraído de ViewerTopBar() (LongMethod de detekt, disparado al agregar
// los 2 accesos de HU-42 gateados por `isPdf`) -- el menú "⋮" completo.
private data class ViewerMenuActions(
    val onConvert           : () -> Unit,
    val onCreateQr          : () -> Unit,
    val onMakeSearchable    : () -> Unit,
    val onSign              : () -> Unit,
    val onMoveToSecureFolder: () -> Unit,
    val onRename            : () -> Unit,
    val onDelete            : () -> Unit
)

@Composable
private fun ViewerMoreOptionsMenu(isPdf: Boolean, actions: ViewerMenuActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.viewer_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            // Atajos "Convertir"/"Crear QR" desde el documento ya abierto
            // (backlog UX 2026-08-30, HU-UX-01/02, AC5) -- van antes de
            // Renombrar/Eliminar por ser acciones no destructivas, igual
            // que en DocumentContextMenu.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.viewer_convert)) },
                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onConvert() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.viewer_create_qr)) },
                leadingIcon = { Icon(Icons.Rounded.QrCode, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onCreateQr() }
            )
            // HU-42: "Hacer buscable"/"Firmar" solo tienen sentido para un
            // PDF real -- mismo criterio que DocumentContextMenu.
            if (isPdf) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.doc_item_make_searchable)) },
                    leadingIcon = { Icon(Icons.Rounded.FindInPage, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onMakeSearchable() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.doc_item_sign)) },
                    leadingIcon = { Icon(Icons.Rounded.Draw, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onSign() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doc_item_move_to_secure_folder)) },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onMoveToSecureFolder() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.viewer_rename)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; actions.onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.viewer_delete)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = { menuExpanded = false; actions.onDelete() }
            )
        }
    }
}