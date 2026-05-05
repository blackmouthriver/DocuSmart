package com.docsmart.features.library.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.library.presentation.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // ── Banner azul con logo ───────────────────────
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
                searchQuery = uiState.searchQuery
            )
        }
    }
}