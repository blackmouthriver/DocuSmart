package com.docsmart.features.library.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.library.presentation.components.*

@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit = {},
    viewModel      : LibraryViewModel = hiltViewModel()
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val context    = LocalContext.current

    var hasPermission   by remember { mutableStateOf(checkStoragePermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

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
        if (hasPermission) viewModel.loadDocuments()
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
        item {
            DocuSmartTopBanner(
                screenTitle    = "Biblioteca",
                screenSubtitle = "Todos tus documentos",
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

        // ── Tabs: Dispositivo / Mis archivos ──────────────────────────────────
        item {
            LibraryTabs(
                selectedTab   = uiState.selectedTab,
                deviceCount   = uiState.deviceDocuments.size,
                appFilesCount = uiState.appDocuments.size,
                onTabSelected = { viewModel.onTabSelected(it) }
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
                searchQuery     = uiState.searchQuery
            )
        }
    }
}

// ── Tabs de Dispositivo / Mis archivos ────────────────────────────────────────
@Composable
private fun LibraryTabs(
    selectedTab  : LibraryTab,
    deviceCount  : Int,
    appFilesCount: Int,
    onTabSelected: (LibraryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tab Dispositivo
        LibraryTabItem(
            icon     = Icons.Rounded.PhoneAndroid,
            label    = "Dispositivo",
            count    = deviceCount,
            selected = selectedTab == LibraryTab.DEVICE,
            onClick  = { onTabSelected(LibraryTab.DEVICE) },
            modifier = Modifier.weight(1f)
        )
        // Tab Mis archivos
        LibraryTabItem(
            icon     = Icons.Rounded.Folder,
            label    = "Mis archivos",
            count    = appFilesCount,
            selected = selectedTab == LibraryTab.APP_FILES,
            onClick  = { onTabSelected(LibraryTab.APP_FILES) },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = "$count archivos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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