package com.docsmart.features.library.presentation

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val repository: TrashRepository,
    private val soundEffectPlayer: SoundEffectPlayer,
    // Bug real encontrado 2026-09-14: actionError estaba hardcodeado en
    // español, saltándose el sistema de 12 idiomas.
    @ApplicationContext private val context: Context
) : ViewModel() {

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private val _pendingDeleteRequest = MutableSharedFlow<PendingDeleteRequest>(extraBufferCapacity = 1)
    val pendingDeleteRequest: SharedFlow<PendingDeleteRequest> = _pendingDeleteRequest.asSharedFlow()

    // Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda,
    // Media -- P2): "Eliminar definitivamente"/"Vaciar todo" no tenían
    // guard de doble-toque -- un segundo toque rápido en "Eliminar"
    // encontraba el archivo ya borrado y mostraba "No se pudo eliminar"
    // DESPUÉS de un borrado exitoso (mensaje engañoso), y en "Vaciar
    // todo" podía invocarse `permissionLauncher.launch()` dos veces casi
    // simultáneas. Guard síncrono (se fija ANTES de lanzar la corrutina,
    // mismo patrón ya usado para AgendaViewModel.saveDraft()).
    private var isBusy = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now     = System.currentTimeMillis()
            val trashed = repository.loadTrashedDocuments()
            val items = trashed.map { entry ->
                val elapsedDays   = (now - entry.deletedAt) / DAY_MILLIS
                val daysRemaining = (TrashRepository.TRASH_RETENTION_DAYS - elapsedDays)
                    // Hallazgo real de la auditoría general 2026-09-17/18
                    // (décima ronda, Baja -- P4): sin coerceAtMost(30), un
                    // reloj atrasado producía elapsedDays negativo y
                    // mostraba p. ej. "35 días restantes".
                    .toInt().coerceIn(0, TrashRepository.TRASH_RETENTION_DAYS)
                TrashedItemUi(entry.document, daysRemaining)
            }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restore(documentId: String) {
        viewModelScope.launch {
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Media -- P3): antes se ignoraba el resultado de
            // restoreFromTrash() -- si el archivo real ya no existía
            // (borrado por fuera de la app), el documento desaparecía sin
            // ningún aviso.
            val restored = repository.restoreFromTrash(documentId)
            if (!restored) {
                _uiState.update { it.copy(actionError = context.getString(R.string.trash_restore_error)) }
            }
            load()
        }
    }

    fun deleteForever(documentId: String) {
        if (isBusy) return
        isBusy = true
        viewModelScope.launch {
            try {
                when (val outcome = repository.deleteForever(documentId)) {
                    DocumentRepository.DeleteOutcome.Deleted -> {
                        soundEffectPlayer.playDelete()
                        load()
                    }
                    DocumentRepository.DeleteOutcome.Failed ->
                        _uiState.update { it.copy(actionError = context.getString(R.string.general_delete_error)) }
                    is DocumentRepository.DeleteOutcome.NeedsPermission ->
                        _pendingDeleteRequest.emit(PendingDeleteRequest.Single(outcome.intentSender, documentId))
                }
            } finally {
                isBusy = false
            }
        }
    }

    /** La Screen llama a esto tras lanzar el `IntentSender` de un [PendingDeleteRequest.Single]
     *  y recibir RESULT_OK -- Android ya borró la fila, solo falta limpiar la papelera. */
    fun onSingleDeleteConfirmed(documentId: String) {
        viewModelScope.launch {
            repository.finalizeDeleteForever(documentId)
            soundEffectPlayer.playDelete()
            load()
        }
    }

    fun deleteAll() {
        if (isBusy) return
        val ids = _uiState.value.items.map { it.document.id }
        if (ids.isEmpty()) return
        isBusy = true
        viewModelScope.launch {
            try {
                when (val outcome = repository.deleteAllForever(ids)) {
                    TrashRepository.BulkDeleteOutcome.Done -> {
                        soundEffectPlayer.playDelete()
                        load()
                    }
                    is TrashRepository.BulkDeleteOutcome.NeedsPermission ->
                        _pendingDeleteRequest.emit(
                            PendingDeleteRequest.Bulk(outcome.intentSender, outcome.documentIds)
                        )
                    TrashRepository.BulkDeleteOutcome.PartialNeedsPermission -> {
                        _uiState.update {
                            it.copy(actionError = context.getString(R.string.trash_bulk_delete_partial_error))
                        }
                        load()
                    }
                }
            } finally {
                isBusy = false
            }
        }
    }

    /** La Screen llama a esto tras lanzar el `IntentSender` de un [PendingDeleteRequest.Bulk]
     *  y recibir RESULT_OK. */
    fun onBulkDeleteConfirmed(documentIds: List<String>) {
        viewModelScope.launch {
            repository.finalizeDeleteForever(documentIds)
            soundEffectPlayer.playDelete()
            load()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(actionError = null) }
    }
}
