package com.docsmart.features.agenda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.agenda.data.AgendaRepository
import com.docsmart.features.agenda.domain.ReminderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    val reminderMinutesBefore: Int? = ReminderPreset.AT_TIME,
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
    val selectedDate: LocalDate = LocalDate.now(),
)

data class AgendaUiState(
    val events: List<AgendaEventEntity> = emptyList(),
    val draft: AgendaEventDraft? = null,
    val showLinkDocumentDialog: Boolean = false,
    val viewMode: AgendaViewMode = AgendaViewMode.LIST,
    val calendarMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
)

@HiltViewModel
class AgendaViewModel
    @Inject
    constructor(
        val adManager: AdManager,
        private val repository: AgendaRepository,
    ) : ViewModel() {
        private val draftFlow = MutableStateFlow<AgendaEventDraft?>(null)
        private val showLinkDocumentDialogFlow = MutableStateFlow(false)
        private val viewModeFlow = MutableStateFlow(AgendaViewMode.LIST)
        private val calendarSelectionFlow = MutableStateFlow(AgendaCalendarSelection())

        val uiState: StateFlow<AgendaUiState> =
            combine(
                repository.observeAll(),
                draftFlow,
                showLinkDocumentDialogFlow,
                viewModeFlow,
                calendarSelectionFlow,
            ) { events, draft, showLink, viewMode, calendarSelection ->
                AgendaUiState(
                    events = events,
                    draft = draft,
                    showLinkDocumentDialog = showLink,
                    viewMode = viewMode,
                    calendarMonth = calendarSelection.month,
                    selectedDate = calendarSelection.selectedDate,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AgendaUiState())

        // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
        // Alta): ver el comentario de AgendaRepository.rescheduleAllReminders().
        fun rescheduleAllReminders() {
            viewModelScope.launch { repository.rescheduleAllReminders() }
        }

        fun setViewMode(mode: AgendaViewMode) {
            viewModeFlow.value = mode
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
            calendarSelectionFlow.update { current ->
                val newMonth = step(current.month)
                val newDay = current.selectedDate.dayOfMonth.coerceAtMost(newMonth.lengthOfMonth())
                current.copy(month = newMonth, selectedDate = newMonth.atDay(newDay))
            }
        }

        fun selectDate(date: LocalDate) {
            calendarSelectionFlow.update { it.copy(selectedDate = date) }
        }

        fun goToToday() {
            calendarSelectionFlow.value = AgendaCalendarSelection()
        }

        fun startCreating() {
            draftFlow.value = AgendaEventDraft()
        }

        fun startEditing(event: AgendaEventEntity) {
            draftFlow.value =
                AgendaEventDraft(
                    id = event.id,
                    createdAt = event.createdAt,
                    title = event.title,
                    description = event.description.orEmpty(),
                    dateTimeMillis = event.dateTimeMillis,
                    documentId = event.documentId,
                    reminderMinutesBefore = event.reminderMinutesBefore,
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
            draftFlow.value = null
        }

        fun updateDraft(transform: (AgendaEventDraft) -> AgendaEventDraft) {
            // update{} atómico: get+set separados perdían una edición si dos
            // cambios (ej. título y fecha) llegaban casi a la vez.
            draftFlow.update { it?.let(transform) }
        }

        fun showLinkDocumentDialog() {
            showLinkDocumentDialogFlow.value = true
        }

        fun dismissLinkDocumentDialog() {
            showLinkDocumentDialogFlow.value = false
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
            val draft = draftFlow.value ?: return
            val title = draft.title.trim()
            if (title.isBlank()) return
            // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
            // Media): `draftFlow.value = null` (que cierra el diálogo) recién se
            // asignaba DESPUÉS de que repository.createEvent()/updateEvent()
            // terminaran (I/O + AlarmManager) -- el botón "Guardar" no se
            // deshabilitaba durante ese lapso, así que un doble-toque rápido
            // pasaba la guarda de arriba las dos veces y creaba dos eventos
            // idénticos (cada uno con su propia alarma). Se cierra el diálogo
            // de inmediato: un segundo toque ve `draftFlow.value == null` y la
            // guarda de arriba lo descarta, mismo criterio que el resto de la
            // app usa para estos guards síncronos.
            draftFlow.value = null
            viewModelScope.launch {
                val description = draft.description.trim().ifBlank { null }
                try {
                    persistDraft(draft, title, description)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Bug real: una excepción de Room/AlarmManager en esta corrutina
                    // (sin manejador) tumbaba la app, y como el diálogo ya se había
                    // cerrado el usuario perdía lo escrito. Se registra solo el tipo
                    // (CrashlyticsTree reenvía todo >= WARN) y se reabre el borrador,
                    // salvo que el usuario ya haya empezado otro.
                    Timber.e("AgendaViewModel: no se pudo guardar el evento (${e.javaClass.simpleName})")
                    draftFlow.compareAndSet(null, draft)
                }
            }
        }

        private suspend fun persistDraft(
            draft: AgendaEventDraft,
            title: String,
            description: String?,
        ) {
            if (draft.id == null) {
                repository.createEvent(
                    title = title,
                    description = description,
                    dateTimeMillis = draft.dateTimeMillis,
                    documentId = draft.documentId,
                    reminderMinutesBefore = draft.reminderMinutesBefore,
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
                        createdAt = draft.createdAt ?: System.currentTimeMillis(),
                    ),
                )
            }
        }

        fun deleteEvent(id: String) {
            viewModelScope.launch {
                try {
                    repository.deleteEvent(id)
                    if (draftFlow.value?.id == id) draftFlow.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin este catch, una falla de Room/AlarmManager tumbaba la app.
                    Timber.e("AgendaViewModel: no se pudo eliminar el evento (${e.javaClass.simpleName})")
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
