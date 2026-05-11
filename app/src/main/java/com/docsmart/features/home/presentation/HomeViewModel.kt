package com.docsmart.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
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
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentDocuments()
    }

    fun loadRecentDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val docs = repository.loadAllDocuments()
                val recents = docs.take(5)
                _uiState.update { state ->
                    state.copy(
                        recentDocuments = recents,
                        isLoading = false
                    )
                }
                Timber.d("HomeViewModel: ${recents.size} documentos recientes")
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: error cargando recientes")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleFavorite(documentId: String) {
        _uiState.update { state ->
            state.copy(
                recentDocuments = state.recentDocuments.map { doc ->
                    if (doc.id == documentId)
                        doc.copy(isFavorite = !doc.isFavorite)
                    else doc
                }
            )
        }
    }
}
