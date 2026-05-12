package com.docsmart.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
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
    private val repository: DocumentRepository,
    private val favoritesRepository: FavoritesRepository  // ← NUEVO
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { loadDocuments() }

    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // DocumentRepository ya aplica isFavorite desde FavoritesRepository
                val docs = repository.loadAllDocuments()
                _uiState.update { state ->
                    state.copy(
                        allDocuments      = docs,
                        filteredDocuments = applyCurrentFilters(docs, state),
                        favorites         = docs.filter { it.isFavorite },
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

    fun toggleFavorite(documentId: String) {
        viewModelScope.launch {
            // Persiste en disco
            val isNowFavorite = favoritesRepository.toggleFavorite(documentId)

            // Actualiza estado en memoria inmediatamente (sin esperar reload)
            val updated = _uiState.value.allDocuments.map { doc ->
                if (doc.id == documentId) doc.copy(isFavorite = isNowFavorite) else doc
            }
            _uiState.update { state ->
                state.copy(
                    allDocuments      = updated,
                    filteredDocuments = applyCurrentFilters(updated, state),
                    favorites         = updated.filter { it.isFavorite }
                )
            }
            Timber.d("LibraryViewModel: toggleFavorite $documentId → $isNowFavorite")
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val filtered = applyCurrentFilters(state.allDocuments, state.copy(searchQuery = query))
            state.copy(searchQuery = query, filteredDocuments = filtered)
        }
    }

    fun onCategorySelected(type: DocumentType?) {
        _uiState.update { state ->
            val newCategory = if (state.selectedCategory == type) null else type
            val filtered = applyCurrentFilters(
                state.allDocuments,
                state.copy(selectedCategory = newCategory)
            )
            state.copy(selectedCategory = newCategory, filteredDocuments = filtered)
        }
    }

    fun clearSearch() { onSearchQueryChange("") }

    fun refresh() { loadDocuments() }

    private fun applyCurrentFilters(
        docs: List<DocumentUiModel>,
        state: LibraryUiState
    ): List<DocumentUiModel> = docs.filter { doc ->
        val matchesQuery    = state.searchQuery.isBlank() ||
                doc.name.contains(state.searchQuery, ignoreCase = true)
        val matchesCategory = state.selectedCategory == null ||
                doc.type == state.selectedCategory
        matchesQuery && matchesCategory
    }

    fun renameDocument(documentId: String, newName: String) {
        viewModelScope.launch {
            try {
                val isAppFile = !documentId.startsWith("content://")

                if (isAppFile) {
                    // Archivo generado por la app → renombra en disco
                    val file    = java.io.File(documentId)
                    val newFile = java.io.File(file.parent, newName)
                    val renamed = file.renameTo(newFile)

                    if (renamed) {
                        // Actualiza aliases con la nueva ruta
                        favoritesRepository.removeAlias(documentId)
                        // Recarga para reflejar nueva ruta
                        loadDocuments()
                        Timber.d("Archivo renombrado en disco: $newFile")
                    } else {
                        Timber.w("No se pudo renombrar en disco: $documentId")
                        // Fallback: guarda alias aunque el rename físico falle
                        favoritesRepository.saveAlias(documentId, newName)
                        updateNameInState(documentId, newName)
                    }
                } else {
                    // Archivo del dispositivo → guarda alias
                    favoritesRepository.saveAlias(documentId, newName)
                    updateNameInState(documentId, newName)
                    Timber.d("Alias guardado para $documentId → $newName")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error renombrando documento")
                // Fallback seguro: siempre guarda alias
                favoritesRepository.saveAlias(documentId, newName)
                updateNameInState(documentId, newName)
            }
        }
    }

    private fun updateNameInState(documentId: String, newName: String) {
        val updated = _uiState.value.allDocuments.map { doc ->
            if (doc.id == documentId) doc.copy(name = newName) else doc
        }
        _uiState.update { state ->
            state.copy(
                allDocuments      = updated,
                filteredDocuments = applyCurrentFilters(updated, state),
                favorites         = updated.filter { it.isFavorite }
            )
        }
    }

}