package com.docsmart.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashedItemUi(
    val document     : DocumentUiModel,
    val daysRemaining: Int
)

data class TrashUiState(
    val items      : List<TrashedItemUi> = emptyList(),
    val isLoading  : Boolean = false,
    val actionError: String? = null
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: TrashRepository
) : ViewModel() {

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now     = System.currentTimeMillis()
            val trashed = repository.loadTrashedDocuments()
            val items = trashed.map { entry ->
                val elapsedDays   = (now - entry.deletedAt) / DAY_MILLIS
                val daysRemaining = (TrashRepository.TRASH_RETENTION_DAYS - elapsedDays)
                    .toInt().coerceAtLeast(0)
                TrashedItemUi(entry.document, daysRemaining)
            }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restore(documentId: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(documentId)
            load()
        }
    }

    fun deleteForever(documentId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteForever(documentId)
            if (!deleted) {
                _uiState.update { it.copy(actionError = "No se pudo eliminar el archivo") }
            }
            load()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(actionError = null) }
    }
}
