package com.docsmart.features.viewer.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
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
    // HU-46: alterna el modo de anotación (resaltar/nota) -- solo PDF.
    isAnnotating: Boolean = false,
    onAnnotateClick: () -> Unit = {},
    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): una
    // vista previa de Carpeta Segura (ver ViewerUiState.isReadOnlyPreview)
    // opera sobre una copia efímera, no el documento real -- Anotar/
    // Renombrar/Eliminar quedan ocultos para no simular acciones que en
    // realidad no tocan el archivo protegido.
    isReadOnlyPreview: Boolean = false,
    // Backlog UX #50: cuántas notas de Modo Estudio están vinculadas a este
    // documento -- el ítem del menú "⋮" solo aparece si hay al menos una.
    linkedNotesCount: Int = 0,
    onOpenLinkedNotesClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Botón atrás
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.viewer_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Nombre del archivo
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Buscar
                // Bug real encontrado 2026-09-14 (repaso general): estas 3
                // content descriptions estaban hardcodeadas en español.
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.viewer_search_content_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Favorito
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector =
                            if (isFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                        contentDescription = stringResource(R.string.viewer_favorite_content_desc),
                        tint =
                            if (isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                // Compartir
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.general_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // HU-46: Anotar (resaltar/nota) -- solo tiene sentido sobre
                // un PDF real, mismo criterio que "Hacer buscable"/"Firmar".
                // Oculto en vista previa de solo lectura (ver arriba).
                if (isPdf && !isReadOnlyPreview) {
                    IconButton(onClick = onAnnotateClick) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = stringResource(R.string.viewer_annotate_content_desc),
                            tint =
                                if (isAnnotating) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }

                // Más opciones (renombrar/eliminar/OCR/firmar/Carpeta Segura)
                // — RF-VIS-06/HU-42
                ViewerMoreOptionsMenu(
                    isPdf = isPdf,
                    isReadOnlyPreview = isReadOnlyPreview,
                    linkedNotesCount = linkedNotesCount,
                    actions =
                        ViewerMenuActions(
                            onConvert = onConvertClick,
                            onCreateQr = onCreateQrClick,
                            onMakeSearchable = onMakeSearchableClick,
                            onSign = onSignClick,
                            onMoveToSecureFolder = onMoveToSecureFolderClick,
                            onRename = onRenameClick,
                            onDelete = onDeleteClick,
                            onOpenLinkedNotes = onOpenLinkedNotesClick,
                        ),
                )
            }
        }
    }
}

// Extraído de ViewerTopBar() (LongMethod de detekt, disparado al agregar
// los 2 accesos de HU-42 gateados por `isPdf`) -- el menú "⋮" completo.
private data class ViewerMenuActions(
    val onConvert: () -> Unit,
    val onCreateQr: () -> Unit,
    val onMakeSearchable: () -> Unit,
    val onSign: () -> Unit,
    val onMoveToSecureFolder: () -> Unit,
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
    val onOpenLinkedNotes: () -> Unit,
)

@Composable
private fun ViewerMoreOptionsMenu(
    isPdf: Boolean,
    isReadOnlyPreview: Boolean,
    linkedNotesCount: Int,
    actions: ViewerMenuActions,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.viewer_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            // Atajos "Convertir"/"Crear QR" desde el documento ya abierto
            // (backlog UX 2026-08-30, HU-UX-01/02, AC5) -- van antes de
            // Renombrar/Eliminar por ser acciones no destructivas, igual
            // que en DocumentContextMenu.
            // Hallazgo real de la auditoría general 2026-09-17 (quinta
            // pasada): estos 4 ítems (Convertir/Crear QR/Hacer buscable/
            // Firmar) eran los únicos del menú que NO se ocultaban en
            // isReadOnlyPreview -- durante una vista previa, `document.id`
            // es la ruta efímera de `secure_preview/`, y los 4 la sacan
            // hacia Convertidor/Herramientas PDF, que escriben su salida en
            // almacenamiento normal fuera de Carpeta Segura, sin PIN.
            if (!isReadOnlyPreview) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_convert)) },
                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        actions.onConvert()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_create_qr)) },
                    leadingIcon = { Icon(Icons.Rounded.QrCode, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        actions.onCreateQr()
                    },
                )
                // HU-42: "Hacer buscable"/"Firmar" solo tienen sentido para
                // un PDF real -- mismo criterio que DocumentContextMenu.
                if (isPdf) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.doc_item_make_searchable)) },
                        leadingIcon = { Icon(Icons.Rounded.FindInPage, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            actions.onMakeSearchable()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.doc_item_sign)) },
                        leadingIcon = { Icon(Icons.Rounded.Draw, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            actions.onSign()
                        },
                    )
                }
            }
            // Hallazgo real de la auditoría general 2026-09-17: este ítem
            // era el único que NO se ocultaba en isReadOnlyPreview (a
            // diferencia de Renombrar/Eliminar más abajo) -- durante una
            // vista previa, `document.id` es la ruta efímera de
            // `secure_preview/`, así que "Mover a Carpeta Segura" copiaba
            // esa copia temporal como si fuera un documento nuevo Y borraba
            // el archivo que el Visor está mostrando en ese momento.
            if (!isReadOnlyPreview) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.doc_item_move_to_secure_folder)) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        actions.onMoveToSecureFolder()
                    },
                )
            }
            // Backlog UX #50, AC1: indicador de que este documento tiene
            // notas de Modo Estudio vinculadas -- solo aparece si hay al
            // menos una.
            if (linkedNotesCount > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_linked_notes, linkedNotesCount)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        actions.onOpenLinkedNotes()
                    },
                )
            }
            // Hallazgo real de la revisión general 2026-09-16 (cuarta
            // pasada): renombrar/eliminar durante una vista previa de
            // Carpeta Segura solo tocan la copia efímera de caché, nunca el
            // archivo protegido real -- simulan una acción que en realidad
            // no pasó. Se ocultan en modo de solo lectura.
            if (!isReadOnlyPreview) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_rename)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        actions.onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_delete)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        actions.onDelete()
                    },
                )
            }
        }
    }
}
