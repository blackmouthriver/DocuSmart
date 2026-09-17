package com.docsmart.features.agenda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.agenda.data.AgendaRepository
import com.docsmart.features.agenda.domain.ReminderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// `id`/`createdAt` nulos == evento nuevo, todavía no guardado. Editar un
// evento existente los carga desde el AgendaEventEntity real -- `createdAt`
// se preserva tal cual para no perder el orden de creación original al
// guardar la edición.
data class AgendaEventDraft(
    val id: String? = null,
    val createdAt: Long? = null,
    val title: String = "",
    val description: String = "",
    val dateTimeMillis: Long = System.currentTimeMillis() + DEFAULT_OFFSET_MILLIS,
    val documentId: String? = null,
    val reminderMinutesBefore: Int? = ReminderPreset.AT_TIME
) {
    private companion object {
        const val DEFAULT_OFFSET_MILLIS = 60 * 60 * 1_000L // +1 hora, mismo criterio que "hora actual" de QrEventForm
    }
}

enum class AgendaViewMode { LIST, CALENDAR }

// Mes mostrado + día seleccionado del grid van juntos en un solo StateFlow
// para no superar el límite de 5 flujos del overload de `combine` usado
// abajo (eventos, draft, diálogo de vínculo, modo de vista, selección).
data class AgendaCalendarSelection(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now()
)

data class AgendaUiState(
    val events: List<AgendaEventEntity> = emptyList(),
    val draft: AgendaEventDraft? = null,
    val showLinkDocumentDialog: Boolean = false,
    val viewMode: AgendaViewMode = AgendaViewMode.LIST,
    val calendarMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now()
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    val adManager: AdManager,
    private val repository: AgendaRepository
) : ViewModel() {

    private val _draft = MutableStateFlow<AgendaEventDraft?>(null)
    private val _showLinkDocumentDialog = MutableStateFlow(false)
    private val _viewMode = MutableStateFlow(AgendaViewMode.LIST)
    private val _calendarSelection = MutableStateFlow(AgendaCalendarSelection())

    val uiState: StateFlow<AgendaUiState> = combine(
        repository.observeAll(), _draft, _showLinkDocumentDialog, _viewMode, _calendarSelection
    ) { events, draft, showLink, viewMode, calendarSelection ->
        AgendaUiState(
            events = events,
            draft = draft,
            showLinkDocumentDialog = showLink,
            viewMode = viewMode,
            calendarMonth = calendarSelection.month,
            selectedDate = calendarSelection.selectedDate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AgendaUiState())

    fun setViewMode(mode: AgendaViewMode) {
        _viewMode.value = mode
    }

    // Hallazgo real de la auditoría general 2026-09-17 (B18): antes solo se
    // cambiaba `month`, dejando `selectedDate` intacto -- el detalle del día
    // seguía mostrando los eventos de una fecha que ya no es visible en la
    // grilla del mes nuevo (ningún día quedaba resaltado como seleccionado,
    // pero el detalle de abajo no reflejaba eso). Se conserva el mismo
    // día-del-mes si existe en el mes nuevo (mismo criterio que la mayoría
    // de apps de calendario), recortado al último día si el mes nuevo tiene
    // menos días (ej. 31 de enero -> febrero).
    fun goToPreviousMonth() = navigateMonth { it.minusMonths(1) }

    fun goToNextMonth() = navigateMonth { it.plusMonths(1) }

    private fun navigateMonth(step: (YearMonth) -> YearMonth) {
        _calendarSelection.update { current ->
            val newMonth = step(current.month)
            val newDay = current.selectedDate.dayOfMonth.coerceAtMost(newMonth.lengthOfMonth())
            current.copy(month = newMonth, selectedDate = newMonth.atDay(newDay))
        }
    }

    fun selectDate(date: LocalDate) {
        _calendarSelection.update { it.copy(selectedDate = date) }
    }

    fun goToToday() {
        _calendarSelection.value = AgendaCalendarSelection()
    }

    fun startCreating() {
        _draft.value = AgendaEventDraft()
    }

    fun startEditing(event: AgendaEventEntity) {
        _draft.value = AgendaEventDraft(
            id = event.id,
            createdAt = event.createdAt,
            title = event.title,
            description = event.description.orEmpty(),
            dateTimeMillis = event.dateTimeMillis,
            documentId = event.documentId,
            reminderMinutesBefore = event.reminderMinutesBefore
        )
    }

    // Notificación tocada (HU-65, AC3): abre el editor de ESE evento
    // puntual en cuanto la Agenda termina de cargar. Si el evento ya no
    // existe (se borró mientras sonaba el recordatorio), no hace nada --
    // no tiene sentido abrir un editor vacío para algo que ya no está.
    fun openEventFromNotification(eventId: String) {
        viewModelScope.launch {
            repository.getById(eventId)?.let { startEditing(it) }
        }
    }

    fun dismissEditor() {
        _draft.value = null
    }

    fun updateDraft(transform: (AgendaEventDraft) -> AgendaEventDraft) {
        _draft.value = _draft.value?.let(transform)
    }

    fun showLinkDocumentDialog() {
        _showLinkDocumentDialog.value = true
    }

    fun dismissLinkDocumentDialog() {
        _showLinkDocumentDialog.value = false
    }

    fun linkDocument(document: DocumentUiModel) {
        updateDraft { it.copy(documentId = document.id) }
        dismissLinkDocumentDialog()
    }

    fun unlinkDocument() {
        updateDraft { it.copy(documentId = null) }
        dismissLinkDocumentDialog()
    }

    // RF1: el título es el único campo obligatorio -- guardar con el título
    // vacío/en blanco no hace nada (el botón "Guardar" de la UI también
    // queda deshabilitado en ese caso, esto es la segunda barrera).
    fun saveDraft() {
        val draft = _draft.value ?: return
        val title = draft.title.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            val description = draft.description.trim().ifBlank { null }
            if (draft.id == null) {
                repository.createEvent(
                    title = title,
                    description = description,
                    dateTimeMillis = draft.dateTimeMillis,
                    documentId = draft.documentId,
                    reminderMinutesBefore = draft.reminderMinutesBefore
                )
            } else {
                repository.updateEvent(
                    AgendaEventEntity(
                        id = draft.id,
                        title = title,
                        description = description,
                        dateTimeMillis = draft.dateTimeMillis,
                        documentId = draft.documentId,
                        reminderMinutesBefore = draft.reminderMinutesBefore,
                        createdAt = draft.createdAt ?: System.currentTimeMillis()
                    )
                )
            }
            _draft.value = null
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            if (_draft.value?.id == id) _draft.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
