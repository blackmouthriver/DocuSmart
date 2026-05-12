package com.docsmart.features.library.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Estado de permisos ────────────────────────────
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    // ── Launcher de permisos ──────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.any { it }
        permissionDenied = permissions.values.all { !it }
        if (hasPermission) {
            viewModel.loadDocuments()
        }
    }

    // ── Solicitar permisos al entrar ──────────────────
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val permissions = getRequiredPermissions()
            permissionLauncher.launch(permissions)
        }
    }

    // ── Recargar si ya tiene permiso ──────────────────
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadDocuments()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        item {
            DocuSmartTopBanner(
                screenTitle = "Biblioteca",
                screenSubtitle = "Todos tus documentos",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        item {
            DocuSmartBannerAd(
                adUnitId = AdConstants.BANNER_LIBRARY_ID,
                adManager = viewModel.adManager,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // ── Sin permisos ──────────────────────────────
        if (!hasPermission) {
            item {
                NoPermissionContent(
                    permissionDenied = permissionDenied,
                    onRequestPermission = {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                )
            }
            return@LazyColumn
        }

        item {
            LibraryHeader(
                searchQuery = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onClear = { viewModel.clearSearch() },
                totalDocuments = uiState.allDocuments.size
            )
        }

        item {
            CategoryFilter(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )
        }

        if (uiState.searchQuery.isBlank() && uiState.selectedCategory == null) {
            item {
                FavoritesSection(
                    favorites = uiState.favorites,
                    onDocumentClick = { doc -> onDocumentClick(doc.id) }
                )
            }
        }

        item {
            DocumentListSection(
                documents = uiState.filteredDocuments,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onRenameClick = { id, newName -> viewModel.renameDocument(id, newName) },
                searchQuery = uiState.searchQuery
            )
        }
    }
}

// ── Pantalla sin permisos ─────────────────────────────
@Composable
private fun NoPermissionContent(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = if (permissionDenied)
                "Permiso denegado"
            else
                "Acceso a archivos requerido",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (permissionDenied)
                "Ve a Ajustes del sistema → DocuSmart → Permisos para habilitarlo manualmente."
            else
                "DocuSmart necesita acceso a tus archivos para mostrar tus documentos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!permissionDenied) {
            Button(
                onClick = onRequestPermission,
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Permitir acceso")
            }
        }
    }
}

// ── Helpers de permisos ───────────────────────────────
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ — permisos granulares
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        // Android 12 y menor
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PermissionChecker.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}