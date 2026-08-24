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

// Tab de la biblioteca
enum class LibraryTab { DEVICE, APP_FILES }

data class LibraryUiState(
    val allDocuments      : List<DocumentUiModel> = emptyList(),
    val filteredDocuments : List<DocumentUiModel> = emptyList(),
    val deviceDocuments   : List<DocumentUiModel> = emptyList(), // ← NUEVO
    val appDocuments      : List<DocumentUiModel> = emptyList(), // ← NUEVO
    val favorites         : List<DocumentUiModel> = emptyList(),
    val searchQuery       : String                = "",
    val selectedCategory  : DocumentType?         = null,
    val selectedTab       : LibraryTab            = LibraryTab.DEVICE, // ← NUEVO
    val isLoading         : Boolean               = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    val adManager          : AdManager,
    private val repository : DocumentRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { loadDocuments() }

    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val docs = repository.loadAllDocuments()

                // Separar documentos del dispositivo vs generados por la app
                val deviceDocs = docs.filter { isDeviceDocument(it) }
                val appDocs    = docs.filter { !isDeviceDocument(it) }

                _uiState.update { state ->
                    state.copy(
                        allDocuments      = docs,
                        deviceDocuments   = deviceDocs,
                        appDocuments      = appDocs,
                        filteredDocuments = applyCurrentFilters(
                            if (state.selectedTab == LibraryTab.DEVICE) deviceDocs else appDocs,
                            state
                        ),
                        favorites         = docs.filter { it.isFavorite },
                        isLoading         = false
                    )
                }
                Timber.d("LibraryViewModel: ${docs.size} docs (${deviceDocs.size} dispositivo, ${appDocs.size} app)")
            } catch (e: Exception) {
                Timber.e(e, "LibraryViewModel: error cargando documentos")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // Un documento es "del dispositivo" si su ID es una content:// URI de MediaStore
    private fun isDeviceDocument(doc: DocumentUiModel): Boolean =
        doc.id.startsWith("content://media") ||
                doc.id.startsWith("content://com.android") ||
                doc.id.startsWith("content://downloads")

    // ── Tab seleccionado ──────────────────────────────────────────────────────
    fun onTabSelected(tab: LibraryTab) {
        _uiState.update { state ->
            val sourceDocs = if (tab == LibraryTab.DEVICE)
                state.deviceDocuments else state.appDocuments
            state.copy(
                selectedTab       = tab,
                selectedCategory  = null,   // reset filtro al cambiar tab
                searchQuery       = "",     // reset búsqueda al cambiar tab
                filteredDocuments = sourceDocs
            )
        }
    }

    fun toggleFavorite(documentId: String) {
        viewModelScope.launch {
            val isNowFavorite = favoritesRepository.toggleFavorite(documentId)
            val updated = _uiState.value.allDocuments.map { doc ->
                if (doc.id == documentId) doc.copy(isFavorite = isNowFavorite) else doc
            }
            val deviceDocs = updated.filter { isDeviceDocument(it) }
            val appDocs    = updated.filter { !isDeviceDocument(it) }
            _uiState.update { state ->
                state.copy(
                    allDocuments      = updated,
                    deviceDocuments   = deviceDocs,
                    appDocuments      = appDocs,
                    filteredDocuments = applyCurrentFilters(
                        if (state.selectedTab == LibraryTab.DEVICE) deviceDocs else appDocs,
                        state
                    ),
                    favorites         = updated.filter { it.isFavorite }
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val sourceDocs = if (state.selectedTab == LibraryTab.DEVICE)
                state.deviceDocuments else state.appDocuments
            val filtered = applyCurrentFilters(sourceDocs, state.copy(searchQuery = query))
            state.copy(searchQuery = query, filteredDocuments = filtered)
        }
    }

    fun onCategorySelected(type: DocumentType?) {
        _uiState.update { state ->
            val newCategory = if (state.selectedCategory == type) null else type
            val sourceDocs  = if (state.selectedTab == LibraryTab.DEVICE)
                state.deviceDocuments else state.appDocuments
            val filtered = applyCurrentFilters(sourceDocs, state.copy(selectedCategory = newCategory))
            state.copy(selectedCategory = newCategory, filteredDocuments = filtered)
        }
    }

    fun clearSearch() { onSearchQueryChange("") }
    fun refresh()     { loadDocuments() }

    private fun applyCurrentFilters(
        docs : List<DocumentUiModel>,
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
                    val file    = java.io.File(documentId)
                    val newFile = java.io.File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        favoritesRepository.removeAlias(documentId)
                        loadDocuments()
                    } else {
                        favoritesRepository.saveAlias(documentId, newName)
                        updateNameInState(documentId, newName)
                    }
                } else {
                    favoritesRepository.saveAlias(documentId, newName)
                    updateNameInState(documentId, newName)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error renombrando documento")
                favoritesRepository.saveAlias(documentId, newName)
                updateNameInState(documentId, newName)
            }
        }
    }

    private fun updateNameInState(documentId: String, newName: String) {
        val updated    = _uiState.value.allDocuments.map { doc ->
            if (doc.id == documentId) doc.copy(name = newName) else doc
        }
        val deviceDocs = updated.filter { isDeviceDocument(it) }
        val appDocs    = updated.filter { !isDeviceDocument(it) }
        _uiState.update { state ->
            state.copy(
                allDocuments      = updated,
                deviceDocuments   = deviceDocs,
                appDocuments      = appDocs,
                filteredDocuments = applyCurrentFilters(
                    if (state.selectedTab == LibraryTab.DEVICE) deviceDocs else appDocs,
                    state
                ),
                favorites         = updated.filter { it.isFavorite }
            )
        }
    }

    fun removeDocument(documentId: String) {
        val updated    = _uiState.value.allDocuments.filter { it.id != documentId }
        val deviceDocs = updated.filter { isDeviceDocument(it) }
        val appDocs    = updated.filter { !isDeviceDocument(it) }
        _uiState.update { state ->
            state.copy(
                allDocuments      = updated,
                deviceDocuments   = deviceDocs,
                appDocuments      = appDocs,
                filteredDocuments = applyCurrentFilters(
                    if (state.selectedTab == LibraryTab.DEVICE) deviceDocs else appDocs,
                    state
                ),
                favorites         = updated.filter { it.isFavorite }
            )
        }
    }
}