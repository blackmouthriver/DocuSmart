package com.docsmart.features.library.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.library.presentation.components.*

@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit = {},
    onTrashClick   : () -> Unit = {},
    // Atajos desde el menú "⋮" de un documento (backlog UX 2026-08-30,
    // HU-UX-01/02) -- sin acción por defecto porque, a diferencia de Home,
    // Biblioteca no tiene un CTA genérico de Convertir/QR al cual caer.
    onConvertClick : (DocumentUiModel) -> Unit = {},
    onCreateQrClick: (DocumentUiModel) -> Unit = {},
    viewModel      : LibraryViewModel = hiltViewModel()
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val context    = LocalContext.current

    var hasPermission   by remember { mutableStateOf(checkStoragePermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission    = permissions.values.any { it }
        permissionDenied = permissions.values.all { !it }
        if (hasPermission) viewModel.loadDocuments()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(getRequiredPermissions())
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadDocuments()
            viewModel.loadTrashCount()
        }
    }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding  = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // ── AdMob — solo para usuarios free ──────────────────────────────────
        if (!isPremium) {
            item {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_LIBRARY_ID,
                    adManager = viewModel.adManager,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // ── Banner azul ───────────────────────────────────────────────────────
        // RF-VIS-07: el botón de papelera vivía acá, en una esquina del banner
        // -- quedaba perdido junto al título/subtítulo. Se movió junto a las
        // pestañas Dispositivo/Mis archivos (ver LibraryTabs más abajo), donde
        // el usuario ya está mirando para elegir qué documentos ver.
        item {
            DocuSmartTopBanner(
                screenTitle    = stringResource(R.string.library_title),
                screenSubtitle = stringResource(R.string.library_subtitle),
                modifier       = Modifier.padding(horizontal = 20.dp)
            )
        }

        // ── Sin permisos ──────────────────────────────────────────────────────
        if (!hasPermission) {
            item {
                NoPermissionContent(
                    permissionDenied    = permissionDenied,
                    onRequestPermission = { permissionLauncher.launch(getRequiredPermissions()) }
                )
            }
            return@LazyColumn
        }

        // ── Buscador ──────────────────────────────────────────────────────────
        item {
            LibraryHeader(
                searchQuery    = uiState.searchQuery,
                onQueryChange  = { viewModel.onSearchQueryChange(it) },
                onClear        = { viewModel.clearSearch() },
                totalDocuments = uiState.allDocuments.size
            )
        }

        // ── Tabs: Dispositivo / Mis archivos + Papelera ────────────────────────
        item {
            LibraryTabs(
                selectedTab   = uiState.selectedTab,
                deviceCount   = uiState.deviceDocuments.size,
                appFilesCount = uiState.appDocuments.size,
                trashCount    = uiState.trashCount,
                onTabSelected = { viewModel.onTabSelected(it) },
                onTrashClick  = onTrashClick
            )
        }

        // ── Filtros de categoría (FlowRow) ────────────────────────────────────
        item {
            CategoryFilter(
                selectedCategory   = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )
        }

        // ── Favoritos (solo si no hay búsqueda ni filtro activo) ──────────────
        if (uiState.searchQuery.isBlank() && uiState.selectedCategory == null) {
            item {
                FavoritesSection(
                    favorites       = uiState.favorites,
                    onDocumentClick = { doc -> onDocumentClick(doc.id) },
                    onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                    onRenameClick   = { id, newName -> viewModel.renameDocument(id, newName) },
                    onDeleteClick   = { id -> viewModel.removeDocument(id) }
                )
            }
        }

        // ── Lista de documentos ───────────────────────────────────────────────
        item {
            DocumentListSection(
                documents       = uiState.filteredDocuments,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onRenameClick   = { id, newName -> viewModel.renameDocument(id, newName) },
                onDeleteClick   = { id -> viewModel.removeDocument(id) },
                onConvertClick  = onConvertClick,
                onCreateQrClick = onCreateQrClick,
                searchQuery     = uiState.searchQuery
            )
        }
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
    selectedTab  : LibraryTab,
    deviceCount  : Int,
    appFilesCount: Int,
    trashCount   : Int,
    onTabSelected: (LibraryTab) -> Unit,
    onTrashClick : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tab Dispositivo
        LibraryTabItem(
            icon     = Icons.Rounded.PhoneAndroid,
            label    = stringResource(R.string.library_tab_device),
            count    = deviceCount,
            selected = selectedTab == LibraryTab.DEVICE,
            onClick  = { onTabSelected(LibraryTab.DEVICE) },
            modifier = Modifier.weight(1f)
        )
        // Tab Mis archivos
        LibraryTabItem(
            icon     = Icons.Rounded.Folder,
            label    = stringResource(R.string.library_tab_app_files),
            count    = appFilesCount,
            selected = selectedTab == LibraryTab.APP_FILES,
            onClick  = { onTabSelected(LibraryTab.APP_FILES) },
            modifier = Modifier.weight(1f)
        )
        // Papelera -- mismo componente visual que las pestañas (ícono +
        // label + contador, weight(1f)) en vez de una tarjeta de ancho fijo
        // sin texto. Bug real reportado por el usuario 2026-08-30: se veía
        // solo el ícono, sin título, y de un tamaño distinto a sus vecinas.
        LibraryTabItem(
            icon     = Icons.Rounded.DeleteOutline,
            label    = stringResource(R.string.library_trash),
            count    = trashCount,
            selected = false,
            onClick  = onTrashClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LibraryTabItem(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    count   : Int,
    selected: Boolean,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick   = onClick,
        modifier  = modifier,
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 0.dp else 2.dp
        ),
        border    = if (selected)
            androidx.compose.foundation.BorderStroke(
                1.5.dp, MaterialTheme.colorScheme.primary
            )
        else null
    ) {
        // HU-UX-05: con "Grande"/"Muy grande" activo, "Dispositivo"/"Mis
        // archivos"/"Papelera" no entran ni en 2 líneas compartiendo el ancho
        // con el ícono en una Row -- layout vertical (ícono arriba, texto
        // centrado abajo, mismo patrón que una barra de navegación inferior)
        // le da al texto todo el ancho de la tarjeta. A tamaño Normal el
        // texto ya entraba en una línea, así que no hay cambio visual ahí
        // más que el ícono ahora arriba en vez de al lado.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text       = label,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text      = stringResource(R.string.library_tab_file_count, count),
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Sin permisos ──────────────────────────────────────────────────────────────
@Composable
private fun NoPermissionContent(
    permissionDenied   : Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector        = Icons.Rounded.FolderOff,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text      = if (permissionDenied) "Permiso denegado" else "Acceso a archivos requerido",
            style     = MaterialTheme.typography.titleMedium,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text  = if (permissionDenied)
                "Ve a Ajustes del sistema → DocuSmart → Permisos para habilitarlo manualmente."
            else
                "DocuSmart necesita acceso a tus archivos para mostrar tus documentos.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!permissionDenied) {
            Button(onClick = onRequestPermission, shape = MaterialTheme.shapes.medium) {
                Text("Permitir acceso")
            }
        }
    }
}

// ── Helpers de permisos ───────────────────────────────────────────────────────
private fun getRequiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun checkStoragePermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PermissionChecker.PERMISSION_GRANTED
    else
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED