package com.docsmart.features.library.presentation

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** Un borrado real que Android no pudo hacer sin confirmación explícita del
 *  usuario -- la Screen debe lanzar [intentSender] y, si vuelve OK, avisar
 *  de vuelta al ViewModel para limpiar la papelera. */
sealed interface PendingDeleteRequest {
    val intentSender: IntentSender
    data class Single(override val intentSender: IntentSender, val documentId: String) : PendingDeleteRequest
    data class Bulk(override val intentSender: IntentSender, val documentIds: List<String>) : PendingDeleteRequest
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: TrashRepository
) : ViewModel() {

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private val _pendingDeleteRequest = MutableSharedFlow<PendingDeleteRequest>(extraBufferCapacity = 1)
    val pendingDeleteRequest: SharedFlow<PendingDeleteRequest> = _pendingDeleteRequest.asSharedFlow()

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
            when (val outcome = repository.deleteForever(documentId)) {
                DocumentRepository.DeleteOutcome.Deleted -> load()
                DocumentRepository.DeleteOutcome.Failed ->
                    _uiState.update { it.copy(actionError = "No se pudo eliminar el archivo") }
                is DocumentRepository.DeleteOutcome.NeedsPermission ->
                    _pendingDeleteRequest.emit(PendingDeleteRequest.Single(outcome.intentSender, documentId))
            }
        }
    }

    /** La Screen llama a esto tras lanzar el `IntentSender` de un [PendingDeleteRequest.Single]
     *  y recibir RESULT_OK -- Android ya borró la fila, solo falta limpiar la papelera. */
    fun onSingleDeleteConfirmed(documentId: String) {
        viewModelScope.launch {
            repository.finalizeDeleteForever(documentId)
            load()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val ids = _uiState.value.items.map { it.document.id }
            if (ids.isEmpty()) return@launch
            when (val outcome = repository.deleteAllForever(ids)) {
                TrashRepository.BulkDeleteOutcome.Done -> load()
                is TrashRepository.BulkDeleteOutcome.NeedsPermission ->
                    _pendingDeleteRequest.emit(
                        PendingDeleteRequest.Bulk(outcome.intentSender, outcome.documentIds)
                    )
                TrashRepository.BulkDeleteOutcome.PartialNeedsPermission -> {
                    _uiState.update {
                        it.copy(actionError = "Algunos archivos requieren eliminarse uno por uno")
                    }
                    load()
                }
            }
        }
    }

    /** La Screen llama a esto tras lanzar el `IntentSender` de un [PendingDeleteRequest.Bulk]
     *  y recibir RESULT_OK. */
    fun onBulkDeleteConfirmed(documentIds: List<String>) {
        viewModelScope.launch {
            repository.finalizeDeleteForever(documentIds)
            load()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(actionError = null) }
    }
}
