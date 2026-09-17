package com.docsmart.features.study.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.features.study.data.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<NoteWithImages> = emptyList(),
    // Backlog UX #50: id de la nota que está eligiendo documento a vincular
    // -- null cuando el diálogo está cerrado.
    val linkDocumentDialogForNoteId: String? = null,
    // B22 (auditoría general 2026-09-17): id de la nota que se está editando
    // -- null cuando el editor está cerrado. Mismo criterio que
    // linkDocumentDialogForNoteId.
    val editingNoteId: String? = null
)

/**
 * Backlog UX #49-#52: reemplaza el `remember { StudyNotesStorage.loadNotes() }`
 * que antes vivía dentro de `NotesTab` -- la lista ahora es reactiva (Room
 * `Flow`, ver `NoteRepository`), así que se refleja sola al vincular/
 * desvincular un documento o adjuntar una imagen, sin recargar la pantalla.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepository.migrateLegacyNotesIfNeeded()
            noteRepository.observeAll().collect { notes ->
                _uiState.update { it.copy(notes = notes) }
            }
        }
    }

    fun createNote(
        title: String,
        text: String,
        imageUris: List<Uri> = emptyList(),
        reminderAt: Long? = null
    ) {
        viewModelScope.launch {
            noteRepository.createNote(title, text, imageUris = imageUris, reminderAt = reminderAt)
            DocuSmartAnalytics.logNoteCreated()
        }
    }

    fun deleteNote(note: NoteWithImages) {
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }

    fun deleteAllNotes() {
        viewModelScope.launch { noteRepository.deleteAll(_uiState.value.notes) }
    }

    fun showLinkDialog(noteId: String) {
        _uiState.update { it.copy(linkDocumentDialogForNoteId = noteId) }
    }

    fun dismissLinkDialog() {
        _uiState.update { it.copy(linkDocumentDialogForNoteId = null) }
    }

    fun linkDocument(noteId: String, documentId: String?) {
        viewModelScope.launch {
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
        newImageUris: List<Uri>
    ) {
        viewModelScope.launch {
            noteRepository.updateNote(noteId, title, text, reminderAt, keptImages, removedImages, newImageUris)
            _uiState.update { it.copy(editingNoteId = null) }
        }
    }
}
