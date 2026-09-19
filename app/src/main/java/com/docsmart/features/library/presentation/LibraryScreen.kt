package com.docsmart.features.library.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.ui.util.ReloadOnScreenResume
import com.docsmart.features.library.presentation.components.*
import timber.log.Timber

@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit = {},
    onTrashClick: () -> Unit = {},
    // Atajos desde el menú "⋮" de un documento (backlog UX 2026-08-30,
    // HU-UX-01/02) -- sin acción por defecto porque, a diferencia de Home,
    // Biblioteca no tiene un CTA genérico de Convertir/QR al cual caer.
    onConvertClick: (DocumentUiModel) -> Unit = {},
    onCreateQrClick: (DocumentUiModel) -> Unit = {},
    onMakeSearchableClick: (DocumentUiModel) -> Unit = {},
    onSignClick: (DocumentUiModel) -> Unit = {},
    onMoveToSecureFolderClick: (DocumentUiModel) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (M3).
    LaunchedEffect(uiState.linkFolderError) {
        uiState.linkFolderError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissLinkFolderError()
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            hasPermission = permissions.values.any { it }
            permissionDenied = permissions.values.all { !it }
            if (hasPermission) viewModel.loadDocuments()
        }

    // Fila 22 del backlog UX: vincular Descargas por SAF para ver PDF/Word/
    // Excel/PowerPoint/Texto reales del dispositivo (ver DownloadsAccessManager
    // -- en Android 13+ no hay permiso equivalente a READ_MEDIA_IMAGES para
    // documentos que otras apps dejaron en Descargas).
    val linkedFolderUri by viewModel.linkedDownloadsFolderUri.collectAsStateWithLifecycle()
    // Nombre real de la carpeta (ej. "DMSS") para mostrarlo en el atajo en
    // vez de un genérico "Carpeta" -- pedido explícito del usuario.
    val linkedFolderName =
        remember(linkedFolderUri) {
            linkedFolderUri?.let { viewModel.linkedFolderDisplayName(it) }
        }
    val linkFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> uri?.let { viewModel.onDownloadsFolderPicked(it) } }

    // Efectos de permiso (solicitud inicial, recarga al concederlo, recarga
    // en cada resume, y el fix R11 de recheck en cada resume) extraídos a
    // LibraryPermissionEffects más abajo -- ver el comentario ahí.
    LibraryPermissionEffects(context, viewModel, hasPermission, permissionLauncher) { hasPermission = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // Pedido explícito del usuario 2026-09-07: mismo margen/espaciado de
        // banner que el resto de las pantallas -- ver DocuSmartScreenHeader.
        // RF-VIS-07: el botón de papelera vivía acá, en una esquina del banner
        // -- quedaba perdido junto al título/subtítulo. Se movió junto a las
        // pestañas Dispositivo/Mis archivos (ver LibraryTabs más abajo), donde
        // el usuario ya está mirando para elegir qué documentos ver.
        item {
            DocuSmartScreenHeader(
                adUnitId = AdConstants.BANNER_LIBRARY_ID,
                adManager = viewModel.adManager,
            ) {
                DocuSmartTopBanner(
                    screenTitle = stringResource(R.string.library_title),
                    screenSubtitle = stringResource(R.string.library_subtitle),
                )
            }
        }

        // ── Sin permisos ──────────────────────────────────────────────────────
        if (!hasPermission) {
            item {
                NoPermissionContent(
                    permissionDenied = permissionDenied,
                    onRequestPermission = { permissionLauncher.launch(getRequiredPermissions()) },
                )
            }
            return@LazyColumn
        }

        // ── Buscador ──────────────────────────────────────────────────────────
        item {
            LibraryHeader(
                searchQuery = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onClear = { viewModel.clearSearch() },
                totalDocuments = uiState.allDocuments.size,
            )
        }

        // ── Tabs: Dispositivo / Mis archivos + Papelera (+ atajo a la carpeta
        // vinculada, si hay una) ────────────────────────────────────────────────
        item {
            LibraryTabs(
                selectedTab = uiState.selectedTab,
                deviceCount = uiState.deviceDocuments.size,
                appFilesCount = uiState.appDocuments.size,
                trashCount = uiState.trashCount,
                linkedFolderUri = linkedFolderUri,
                linkedFolderName = linkedFolderName,
                onTabSelected = { viewModel.onTabSelected(it) },
                onTrashClick = onTrashClick,
                onOpenFolderClick = { openLinkedFolder(context, it) },
            )
        }

        // ── Descripción de la pestaña activa (feedback real de testers
        // 2026-09-12: "Biblioteca lacking context" -- no quedaba claro qué
        // diferencia hay entre "Dispositivo" y "Mis archivos"). Un texto
        // corto que cambia según la pestaña seleccionada, en vez de forzar
        // una tercera línea dentro de cada tarjeta de LibraryTabItem (con
        // fuente "Grande"/"Muy grande" ya apenas entran 2 líneas ahí, ver
        // HU-UX-05).
        item {
            Text(
                text =
                    when (uiState.selectedTab) {
                        LibraryTab.DEVICE -> stringResource(R.string.library_tab_device_description)
                        LibraryTab.APP_FILES -> stringResource(R.string.library_tab_app_files_description)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Vincular Descargas (fila 22 backlog UX): solo en la pestaña
        // Dispositivo y mientras no haya carpeta vinculada -- una vez vinculada,
        // loadDocumentsFromLinkedFolder() ya trae PDF/Word/Excel/PowerPoint/
        // Texto reales y esta tarjeta deja de tener sentido.
        if (uiState.selectedTab == LibraryTab.DEVICE && linkedFolderUri == null) {
            item {
                LinkDownloadsFolderCard(
                    onLinkClick = {
                        linkFolderLauncher.launch(viewModel.downloadsFolderPickerInitialUri())
                    },
                )
            }
        }

        // ── Filtros de categoría (FlowRow) ────────────────────────────────────
        item {
            CategoryFilter(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) },
            )
        }

        // ── Favoritos (solo si no hay búsqueda ni filtro activo) ──────────────
        if (uiState.searchQuery.isBlank() && uiState.selectedCategory == null) {
            item {
                FavoritesSection(
                    favorites = uiState.favorites,
                    onDocumentClick = { doc -> onDocumentClick(doc.id) },
                    onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                    onRenameClick = { id, newName -> viewModel.renameDocument(id, newName) },
                    onDeleteClick = { id -> viewModel.removeDocument(id) },
                    onConvertClick = onConvertClick,
                    onCreateQrClick = onCreateQrClick,
                    onMakeSearchableClick = onMakeSearchableClick,
                    onSignClick = onSignClick,
                    onMoveToSecureFolderClick = onMoveToSecureFolderClick,
                )
            }
        }

        // ── Lista de documentos ───────────────────────────────────────────────
        item {
            DocumentListSection(
                documents = uiState.filteredDocuments,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onRenameClick = { id, newName -> viewModel.renameDocument(id, newName) },
                onDeleteClick = { id -> viewModel.removeDocument(id) },
                onConvertClick = onConvertClick,
                onCreateQrClick = onCreateQrClick,
                onMakeSearchableClick = onMakeSearchableClick,
                onSignClick = onSignClick,
                onMoveToSecureFolderClick = onMoveToSecureFolderClick,
                searchQuery = uiState.searchQuery,
            )
        }
    }
}

// Efectos de permiso de LibraryScreen, extraídos a una función aparte para
// mantener el tamaño del Composable principal dentro del límite de detekt
// (LongMethod). Sin cambios de comportamiento respecto a como vivían inline.
@Composable
private fun LibraryPermissionEffects(
    context: android.content.Context,
    viewModel: LibraryViewModel,
    hasPermission: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onPermissionChanged: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(getRequiredPermissions())
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadDocuments()
            viewModel.loadTrashCount()
        }
    }

    // Hallazgo real de la revisión general 2026-09-16 (#52): esta pantalla
    // solo cargaba la lista una vez (al obtener el permiso), así que un
    // documento movido/borrado en otra pantalla (ej. Visor) seguía
    // apareciendo como "fantasma" al volver atrás.
    ReloadOnScreenResume(enabled = hasPermission) {
        viewModel.loadDocuments()
        viewModel.loadTrashCount()
    }

    // Hallazgo de auditoría (R11): si el usuario deniega el permiso, sale a
    // Ajustes del sistema, lo concede ahí manualmente y vuelve a DocuSmart
    // SIN abandonar la pestaña Biblioteca (solo cambia de app y vuelve), el
    // `ReloadOnScreenResume` de arriba nunca se disparaba porque su propia
    // condición (`enabled = hasPermission`) seguía en `false` -- nada volvía
    // a consultar el permiso real. Este segundo observer, sin condición,
    // revisa el permiso en cada ON_RESUME (mismo patrón que AgendaScreen/
    // NotesTab) para poder salir del estado "Sin permisos" sin reintentar
    // manualmente.
    ReloadOnScreenResume(enabled = true) {
        onPermissionChanged(checkStoragePermission(context))
    }
}

// ── Tabs de Dispositivo / Mis archivos + Papelera ─────────────────────────────
// RF-VIS-07: la papelera vivía en el banner azul, en una esquina fácil de
// pasar por alto. Se movió acá, al lado de las pestañas -- mismo lugar donde
// el usuario ya está mirando para decidir qué documentos ver, y con la misma
// altura que las pestañas (Modifier.height(IntrinsicSize.Min) en el Row +
// fillMaxHeight() en el botón) para que se vea como parte del mismo grupo,
// no un elemento suelto.
@Composable
private fun LibraryTabs(
    selectedTab: LibraryTab,
    deviceCount: Int,
    appFilesCount: Int,
    trashCount: Int,
    linkedFolderUri: Uri?,
    linkedFolderName: String?,
    onTabSelected: (LibraryTab) -> Unit,
    onTrashClick: () -> Unit,
    onOpenFolderClick: (Uri) -> Unit,
) {
    // Sin carpeta vinculada: 1 fila de 3. Con carpeta vinculada: grilla 2x2
    // -- 4 en una sola fila angostaba tanto las tarjetas que "Dispositivo"
    // se partía en dos líneas (regresión detectada y corregida a pedido del
    // usuario 2026-09-03).
    if (linkedFolderUri == null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryTabItem(
                icon = Icons.Rounded.PhoneAndroid,
                label = stringResource(R.string.library_tab_device),
                subtitle = stringResource(R.string.library_tab_file_count, deviceCount),
                selected = selectedTab == LibraryTab.DEVICE,
                onClick = { onTabSelected(LibraryTab.DEVICE) },
                modifier = Modifier.weight(1f),
            )
            LibraryTabItem(
                icon = Icons.Rounded.Folder,
                label = stringResource(R.string.library_tab_app_files),
                subtitle = stringResource(R.string.library_tab_file_count, appFilesCount),
                selected = selectedTab == LibraryTab.APP_FILES,
                onClick = { onTabSelected(LibraryTab.APP_FILES) },
                modifier = Modifier.weight(1f),
            )
            // Papelera -- mismo componente visual que las pestañas (ícono +
            // label + contador, weight(1f)) en vez de una tarjeta de ancho
            // fijo sin texto. Bug real reportado por el usuario 2026-08-30:
            // se veía solo el ícono, sin título, y de tamaño distinto.
            LibraryTabItem(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.library_trash),
                subtitle = stringResource(R.string.library_tab_file_count, trashCount),
                selected = false,
                onClick = onTrashClick,
                modifier = Modifier.weight(1f),
                isTab = false,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryTabItem(
                    icon = Icons.Rounded.PhoneAndroid,
                    label = stringResource(R.string.library_tab_device),
                    subtitle = stringResource(R.string.library_tab_file_count, deviceCount),
                    selected = selectedTab == LibraryTab.DEVICE,
                    onClick = { onTabSelected(LibraryTab.DEVICE) },
                    modifier = Modifier.weight(1f),
                )
                LibraryTabItem(
                    icon = Icons.Rounded.Folder,
                    label = stringResource(R.string.library_tab_app_files),
                    subtitle = stringResource(R.string.library_tab_file_count, appFilesCount),
                    selected = selectedTab == LibraryTab.APP_FILES,
                    onClick = { onTabSelected(LibraryTab.APP_FILES) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryTabItem(
                    icon = Icons.Rounded.DeleteOutline,
                    label = stringResource(R.string.library_trash),
                    subtitle = stringResource(R.string.library_tab_file_count, trashCount),
                    selected = false,
                    onClick = onTrashClick,
                    modifier = Modifier.weight(1f),
                    isTab = false,
                )
                // Atajo a la carpeta vinculada por SAF (fila 22 backlog UX):
                // muestra el nombre real de la carpeta elegida (ej. "DMSS"),
                // no un genérico "Carpeta" -- pedido explícito del usuario.
                LibraryTabItem(
                    icon = Icons.Rounded.FolderOpen,
                    label = linkedFolderName ?: stringResource(R.string.library_folder_shortcut_label),
                    subtitle = stringResource(R.string.library_folder_shortcut_subtitle),
                    selected = false,
                    onClick = { onOpenFolderClick(linkedFolderUri) },
                    modifier = Modifier.weight(1f),
                    isTab = false,
                )
            }
        }
    }
}

@Composable
private fun LibraryTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Hallazgo real de la auditoría general 2026-09-17 (séptima ronda,
    // Media -- A4): "Dispositivo"/"Mis archivos" son pestañas reales
    // (participan de `selected`), pero "Papelera"/el atajo de carpeta
    // reutilizan el mismo componente visual sin serlo -- antes ambos
    // casos usaban `.clickable{}` puro, sin `role`/`selected` para
    // TalkBack, así que ninguna pestaña anunciaba "seleccionada". La
    // barra de navegación inferior ya usa `Modifier.selectable(...)`
    // para el mismo patrón (`DocuSmartBottomBar.kt`) -- se alinea acá.
    isTab: Boolean = true,
) {
    val shape = MaterialTheme.shapes.large
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    Box(
        modifier =
            modifier
                .then(
                    // Sombra/borde de acento solo cuando NO está seleccionada --
                    // igual que antes con 0.dp, la tarjeta seleccionada no lleva
                    // sombra, y usa su propio borde grueso de "seleccionado".
                    if (!selected) {
                        Modifier.accentShadow(shape = shape, elevation = 2.dp)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(containerColor)
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier.accentBorder(shape = shape)
                    },
                )
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = if (isTab) Role.Tab else Role.Button,
                ),
    ) {
        // HU-UX-05: con "Grande"/"Muy grande" activo, "Dispositivo"/"Mis
        // archivos"/"Papelera" no entran ni en 2 líneas compartiendo el ancho
        // con el ícono en una Row -- layout vertical (ícono arriba, texto
        // centrado abajo, mismo patrón que una barra de navegación inferior)
        // le da al texto todo el ancho de la tarjeta. A tamaño Normal el
        // texto ya entraba en una línea, así que no hay cambio visual ahí
        // más que el ícono ahora arriba en vez de al lado.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                // Card angostada por el atajo de carpeta agregado en la fila
                // 22 del backlog UX -- labelMedium en vez de labelLarge para
                // que "Dispositivo"/"Mis archivos" sigan entrando en una
                // línea con las 4 columnas.
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Vincular carpeta de Descargas (SAF) ────────────────────────────────────────
// Fila 22 del backlog UX: en Android 13+ no hay forma de que la app vea, solo
// con permisos, los PDF/Word/Excel/PowerPoint/Texto que otras apps (el
// navegador, WhatsApp, etc.) dejaron en Descargas -- la única alternativa real
// (sin pedir "acceso a todos los archivos") es que el usuario vincule la
// carpeta una vez con el selector nativo de Android.
@Composable
private fun LinkDownloadsFolderCard(onLinkClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.library_link_downloads_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.library_link_downloads_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onLinkClick,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.library_link_downloads_button))
            }
        }
    }
}

// ── Sin permisos ──────────────────────────────────────────────────────────────
@Composable
private fun NoPermissionContent(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        // Bug real encontrado 2026-09-14 (repaso general): estos 5 textos
        // estaban hardcodeados en español, saltándose el sistema de 12
        // idiomas que ya usa el resto de la pantalla.
        Text(
            text =
                stringResource(
                    if (permissionDenied) {
                        R.string.library_no_permission_denied_title
                    } else {
                        R.string.library_no_permission_required_title
                    },
                ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text =
                stringResource(
                    if (permissionDenied) {
                        R.string.library_no_permission_denied_body
                    } else {
                        R.string.library_no_permission_required_body
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!permissionDenied) {
            Button(onClick = onRequestPermission, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.library_no_permission_allow_button))
            }
        }
    }
}

// ── Helpers de permisos ───────────────────────────────────────────────────────
// READ_MEDIA_VIDEO removido 2026-09-10: DocuSmart no tiene ninguna función
// que use contenido de video, ver comentario en MainActivity.kt.
private fun getRequiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun checkStoragePermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PermissionChecker.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED
    }

// Atajo a la carpeta vinculada por SAF (fila 22 backlog UX): delega en
// cualquier app que sepa abrir un árbol de documentos (normalmente el
// gestor de archivos del sistema) en vez de reimplementar un navegador de
// carpetas propio -- ningún dispositivo probado carece de una app así,
// pero se cubre igual el caso raro con un aviso en vez de un cierre.
private fun openLinkedFolder(
    context: android.content.Context,
    folderUri: Uri,
) {
    try {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w("openLinkedFolder: sin app para ACTION_VIEW: ${e.javaClass.simpleName}")
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.library_folder_shortcut_no_app),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}
