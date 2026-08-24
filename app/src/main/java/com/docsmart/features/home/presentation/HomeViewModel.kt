package com.docsmart.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
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

data class HomeUiState(
    val recentDocuments: List<DocumentUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val userName: String = "Usuario"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val adManager: AdManager,
    private val repository: DocumentRepository,
    private val favoritesRepository: FavoritesRepository  // ← NUEVO
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadRecentDocuments() }

    fun loadRecentDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // DocumentRepository ya aplica isFavorite desde FavoritesRepository
                val docs = repository.loadAllDocuments()
                _uiState.update { state ->
                    state.copy(
                        recentDocuments = docs.take(5),
                        isLoading = false
                    )
                }
                Timber.d("HomeViewModel: ${docs.size} documentos recientes")
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: error cargando recientes")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleFavorite(documentId: String) {
        viewModelScope.launch {
            // Persiste en disco
            val isNowFavorite = favoritesRepository.toggleFavorite(documentId)

            // Actualiza UI en memoria inmediatamente
            _uiState.update { state ->
                state.copy(
                    recentDocuments = state.recentDocuments.map { doc ->
                        if (doc.id == documentId) doc.copy(isFavorite = isNowFavorite) else doc
                    }
                )
            }
            Timber.d("HomeViewModel: toggleFavorite $documentId → $isNowFavorite")
        }
    }

    fun removeDocument(documentId: String) {
        _uiState.update { state ->
            state.copy(
                recentDocuments = state.recentDocuments.filter { it.id != documentId }
            )
        }
    }

    fun renameDocument(documentId: String, newName: String) {
        viewModelScope.launch {
            try {
                val isAppFile = !documentId.startsWith("content://")
                if (isAppFile) {
                    val file    = java.io.File(documentId)
                    val newFile = java.io.File(file.parent, newName)
                    if (!file.renameTo(newFile)) {
                        favoritesRepository.saveAlias(documentId, newName)
                    }
                } else {
                    favoritesRepository.saveAlias(documentId, newName)
                }
            } catch (e: Exception) {
                favoritesRepository.saveAlias(documentId, newName)
            }
            // Actualiza UI en memoria
            _uiState.update { state ->
                state.copy(
                    recentDocuments = state.recentDocuments.map { doc ->
                        if (doc.id == documentId) doc.copy(name = newName) else doc
                    }
                )
            }
        }
    }
}