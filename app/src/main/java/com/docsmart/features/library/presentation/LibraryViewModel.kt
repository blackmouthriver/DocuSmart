package com.docsmart.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LibraryUiState(
    val allDocuments: List<DocumentUiModel>      = emptyList(),
    val filteredDocuments: List<DocumentUiModel> = emptyList(),
    val favorites: List<DocumentUiModel>         = emptyList(),
    val searchQuery: String                      = "",
    val selectedCategory: DocumentType?          = null,
    val isLoading: Boolean                       = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    val adManager: AdManager,
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // ── Favoritos persistidos en memoria ─────────────
    // En la siguiente fase se migrarán a Room/SharedPreferences
    private val favoriteIds = mutableSetOf<String>()

    init { loadDocuments() }

    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val docs = repository.loadAllDocuments()

                // ── Aplicar favoritos guardados ───────
                val docsWithFavorites = docs.map { doc ->
                    doc.copy(isFavorite = favoriteIds.contains(doc.id))
                }

                _uiState.update { state ->
                    state.copy(
                        allDocuments      = docsWithFavorites,
                        filteredDocuments = docsWithFavorites,
                        favorites         = docsWithFavorites.filter { it.isFavorite },
                        isLoading         = false
                    )
                }

                Timber.d("LibraryViewModel: ${docs.size} documentos cargados")

            } catch (e: Exception) {
                Timber.e(e, "LibraryViewModel: error cargando documentos")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onCategorySelected(type: DocumentType?) {
        _uiState.update { state ->
            val newCategory = if (state.selectedCategory == type) null else type
            state.copy(selectedCategory = newCategory)
        }
        applyFilters()
    }

    fun toggleFavorite(documentId: String) {
        if (favoriteIds.contains(documentId)) {
            favoriteIds.remove(documentId)
        } else {
            favoriteIds.add(documentId)
        }

        val updated = _uiState.value.allDocuments.map { doc ->
            if (doc.id == documentId) doc.copy(isFavorite = !doc.isFavorite) else doc
        }

        _uiState.update { state ->
            state.copy(
                allDocuments = updated,
                favorites    = updated.filter { it.isFavorite }
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val result = state.allDocuments.filter { doc ->
            val matchesQuery    = state.searchQuery.isBlank() ||
                    doc.name.contains(state.searchQuery, ignoreCase = true)
            val matchesCategory = state.selectedCategory == null ||
                    doc.type == state.selectedCategory
            matchesQuery && matchesCategory
        }
        _uiState.update { it.copy(filteredDocuments = result) }
    }

    fun clearSearch() { onSearchQueryChange("") }

    // ── Recargar al volver a la pantalla ──────────────
    fun refresh() { loadDocuments() }
}