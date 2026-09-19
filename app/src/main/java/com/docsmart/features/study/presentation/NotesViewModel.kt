package com.docsmart.features.study.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.features.study.data.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda, Baja
// -- N5): a diferencia del campo "Nota" del Visor (anotaciones sobre un
// PDF, `ViewerViewModel.MAX_NOTE_LENGTH`), el texto de Notas de Estudio no
// tenía ningún límite -- mismo valor y mismo motivo (acotar el tamaño real
// en Room/al exportar), aplicado acá también para consistencia.
private const val MAX_NOTE_LENGTH = 2000

data class NotesUiState(
    val notes: List<NoteWithImages> = emptyList(),
    // Backlog UX #50: id de la nota que está eligiendo documento a vincular
    // -- null cuando el diálogo está cerrado.
    val linkDocumentDialogForNoteId: String? = null,
    // B22 (auditoría general 2026-09-17): id de la nota que se está editando
    // -- null cuando el editor está cerrado. Mismo criterio que
    // linkDocumentDialogForNoteId.
    val editingNoteId: String? = null,
)

/**
 * Backlog UX #49-#52: reemplaza el `remember { StudyNotesStorage.loadNotes() }`
 * que antes vivía dentro de `NotesTab` -- la lista ahora es reactiva (Room
 * `Flow`, ver `NoteRepository`), así que se refleja sola al vincular/
 * desvincular un documento o adjuntar una imagen, sin recargar la pantalla.
 */
@HiltViewModel
class NotesViewModel
    @Inject
    constructor(
        private val noteRepository: NoteRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NotesUiState())
        val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                // Bug real: si la migración de notas legadas lanzaba (JSON corrupto,
                // Room ocupado), la excepción cancelaba esta corrutina ANTES de
                // observeAll() -- la pantalla de notas quedaba vacía para siempre
                // (o tumbaba la app). La migración es opcional: se sigue observando.
                try {
                    noteRepository.migrateLegacyNotesIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e("NotesViewModel: falló la migración de notas legadas (${e.javaClass.simpleName})")
                }
                noteRepository.observeAll().collect { notes ->
                    _uiState.update { it.copy(notes = notes) }
                }
            }
        }

        fun createNote(
            title: String,
            text: String,
            imageUris: List<Uri> = emptyList(),
            reminderAt: Long? = null,
        ) {
            launchLogged("crear nota") {
                noteRepository.createNote(title, text.take(MAX_NOTE_LENGTH), imageUris = imageUris, reminderAt = reminderAt)
                // Solo si la nota realmente se creó.
                DocuSmartAnalytics.logNoteCreated()
            }
        }

        fun deleteNote(note: NoteWithImages) {
            launchLogged("eliminar nota") { noteRepository.deleteNote(note) }
        }

        fun deleteAllNotes() {
            launchLogged("eliminar todas las notas") { noteRepository.deleteAll(_uiState.value.notes) }
        }

        fun showLinkDialog(noteId: String) {
            _uiState.update { it.copy(linkDocumentDialogForNoteId = noteId) }
        }

        fun dismissLinkDialog() {
            _uiState.update { it.copy(linkDocumentDialogForNoteId = null) }
        }

        fun linkDocument(
            noteId: String,
            documentId: String?,
        ) {
            launchLogged("vincular documento") {
                noteRepository.linkDocument(noteId, documentId)
                _uiState.update { it.copy(linkDocumentDialogForNoteId = null) }
            }
        }

        // B22 (auditoría general 2026-09-17): editar título/texto/imágenes/
        // recordatorio de una nota ya guardada -- antes no existía.
        fun startEditingNote(noteId: String) {
            _uiState.update { it.copy(editingNoteId = noteId) }
        }

        fun cancelEditingNote() {
            _uiState.update { it.copy(editingNoteId = null) }
        }

        fun updateNote(
            noteId: String,
            title: String,
            text: String,
            reminderAt: Long?,
            keptImages: List<NoteImageEntity>,
            removedImages: List<NoteImageEntity>,
            newImageUris: List<Uri>,
        ) {
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Media -- N2): `editingNoteId = null` (que cierra el
            // diálogo) recién se asignaba DESPUÉS de que
            // noteRepository.updateNote() terminara -- con imágenes nuevas
            // adjuntas, un doble-toque real en "Guardar" las copiaba e
            // insertaba dos veces. Mismo criterio ya usado para
            // AgendaViewModel.saveDraft() (sexta ronda): se limpia el estado
            // de inmediato, ANTES de lanzar la corrutina, así un segundo
            // toque encuentra `editingNoteId` ya distinto y no vuelve a
            // llamar a esta función (ver NoteEditDialog).
            if (_uiState.value.editingNoteId != noteId) return
            _uiState.update { it.copy(editingNoteId = null) }
            launchLogged("actualizar nota") {
                noteRepository.updateNote(
                    noteId,
                    title,
                    text.take(MAX_NOTE_LENGTH),
                    reminderAt,
                    keptImages,
                    removedImages,
                    newImageUris,
                )
            }
        }

        // Una excepción de Room/E-S dentro de viewModelScope.launch sin manejador
        // tumba el proceso entero. Se registra solo el tipo de excepción
        // (CrashlyticsTree reenvía todo >= WARN a Firebase: sin contenido de notas).
        private fun launchLogged(
            action: String,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e("NotesViewModel: falló $action (${e.javaClass.simpleName})")
                }
            }
        }
    }
