package com.docsmart.features.study.presentation

import android.net.Uri
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.features.study.data.NoteRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `NotesViewModel` estaba en 0% de cobertura. Se testea con `NoteRepository`
 * mockeado; `DocuSmartAnalytics` es un `object` que atrapa cualquier fallo de
 * Firebase (no inicializado en un test JVM), así que no interfiere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private lateinit var repository: NoteRepository
    private lateinit var notesFlow: MutableStateFlow<List<NoteWithImages>>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        notesFlow = MutableStateFlow(emptyList())
        repository = mockk()
        coJustRun { repository.migrateLegacyNotesIfNeeded() }
        every { repository.observeAll() } returns notesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = NotesViewModel(repository)

    private fun noteOf(id: String) = NoteWithImages(NoteEntity(id = id, title = "t$id", text = "x", createdAt = 1L), emptyList())

    // ── Carga inicial ─────────────────────────────────────────────────────────

    @Test
    fun `las notas del repositorio se reflejan en el estado y se actualizan solas`() =
        runTest {
            notesFlow.value = listOf(noteOf("1"))
            val viewModel = buildViewModel()
            assertEquals(listOf(noteOf("1")), viewModel.uiState.value.notes)

            notesFlow.value = listOf(noteOf("1"), noteOf("2"))

            assertEquals(2, viewModel.uiState.value.notes.size)
        }

    @Test
    fun `se migran las notas legadas antes de observar`() =
        runTest {
            buildViewModel()

            coVerify(exactly = 1) { repository.migrateLegacyNotesIfNeeded() }
        }

    // Bug real corregido: si la migracion lanzaba, la excepcion cancelaba la
    // corrutina ANTES de observeAll() -- la pantalla de notas quedaba vacia.
    @Test
    fun `si la migracion de notas legadas falla igual se siguen observando las notas`() =
        runTest {
            coEvery { repository.migrateLegacyNotesIfNeeded() } throws IllegalStateException("json corrupto")
            notesFlow.value = listOf(noteOf("1"))

            val viewModel = buildViewModel()

            assertEquals(listOf(noteOf("1")), viewModel.uiState.value.notes)
        }

    // ── createNote ────────────────────────────────────────────────────────────

    @Test
    fun `createNote recorta el texto a 2000 caracteres y pasa recordatorio e imagenes`() =
        runTest {
            val textSlot = slot<String>()
            val uris = listOf(mockk<Uri>())
            coEvery {
                repository.createNote(any(), capture(textSlot), any(), any(), any())
            } returns Unit
            val viewModel = buildViewModel()

            viewModel.createNote("Titulo", "a".repeat(2_500), imageUris = uris, reminderAt = 42L)

            assertEquals(2_000, textSlot.captured.length)
            coVerify { repository.createNote("Titulo", any(), null, uris, 42L) }
        }

    @Test
    fun `createNote con texto corto no lo modifica`() =
        runTest {
            val textSlot = slot<String>()
            coEvery { repository.createNote(any(), capture(textSlot), any(), any(), any()) } returns Unit
            val viewModel = buildViewModel()

            viewModel.createNote("T", "hola")

            assertEquals("hola", textSlot.captured)
        }

    // Bug real corregido: una excepcion de Room/E-S en viewModelScope.launch sin
    // manejador tumbaba el proceso entero.
    @Test
    fun `createNote que falla no propaga la excepcion`() =
        runTest {
            coEvery { repository.createNote(any(), any(), any(), any(), any()) } throws IllegalStateException("db")
            val viewModel = buildViewModel()

            viewModel.createNote("T", "hola")

            coVerify(exactly = 1) { repository.createNote(any(), any(), any(), any(), any()) }
        }

    // ── deleteNote / deleteAllNotes ───────────────────────────────────────────

    @Test
    fun `deleteNote delega en el repositorio`() =
        runTest {
            val note = noteOf("1")
            coJustRun { repository.deleteNote(note) }
            val viewModel = buildViewModel()

            viewModel.deleteNote(note)

            coVerify { repository.deleteNote(note) }
        }

    @Test
    fun `deleteAllNotes borra las notas actualmente cargadas`() =
        runTest {
            val notes = listOf(noteOf("1"), noteOf("2"))
            notesFlow.value = notes
            coJustRun { repository.deleteAll(notes) }
            val viewModel = buildViewModel()

            viewModel.deleteAllNotes()

            coVerify { repository.deleteAll(notes) }
        }

    @Test
    fun `deleteNote que falla no propaga la excepcion`() =
        runTest {
            val note = noteOf("1")
            coEvery { repository.deleteNote(note) } throws IllegalStateException("db")
            val viewModel = buildViewModel()

            viewModel.deleteNote(note)

            coVerify(exactly = 1) { repository.deleteNote(note) }
        }

    // ── Vinculo de documento ──────────────────────────────────────────────────

    @Test
    fun `showLinkDialog y dismissLinkDialog abren y cierran el dialogo de la nota`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.showLinkDialog("n1")
            assertEquals("n1", viewModel.uiState.value.linkDocumentDialogForNoteId)

            viewModel.dismissLinkDialog()
            assertNull(viewModel.uiState.value.linkDocumentDialogForNoteId)
        }

    @Test
    fun `linkDocument vincula y cierra el dialogo`() =
        runTest {
            coJustRun { repository.linkDocument("n1", "doc") }
            val viewModel = buildViewModel()
            viewModel.showLinkDialog("n1")

            viewModel.linkDocument("n1", "doc")

            coVerify { repository.linkDocument("n1", "doc") }
            assertNull(viewModel.uiState.value.linkDocumentDialogForNoteId)
        }

    @Test
    fun `linkDocument con null desvincula`() =
        runTest {
            coJustRun { repository.linkDocument("n1", null) }
            val viewModel = buildViewModel()

            viewModel.linkDocument("n1", null)

            coVerify { repository.linkDocument("n1", null) }
        }

    @Test
    fun `si vincular falla el dialogo sigue abierto para reintentar`() =
        runTest {
            coEvery { repository.linkDocument("n1", "doc") } throws IllegalStateException("db")
            val viewModel = buildViewModel()
            viewModel.showLinkDialog("n1")

            viewModel.linkDocument("n1", "doc")

            assertEquals("n1", viewModel.uiState.value.linkDocumentDialogForNoteId)
        }

    // ── Edicion ───────────────────────────────────────────────────────────────

    private fun NotesViewModel.saveEdit(
        noteId: String,
        text: String = "texto",
        newImages: List<Uri> = emptyList(),
    ) = updateNote(
        noteId = noteId,
        title = "titulo",
        text = text,
        reminderAt = null,
        keptImages = emptyList(),
        removedImages = emptyList<NoteImageEntity>(),
        newImageUris = newImages,
    )

    @Test
    fun `startEditingNote y cancelEditingNote controlan el editor`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.startEditingNote("n1")
            assertEquals("n1", viewModel.uiState.value.editingNoteId)

            viewModel.cancelEditingNote()
            assertNull(viewModel.uiState.value.editingNoteId)
        }

    @Test
    fun `updateNote cierra el editor y recorta el texto a 2000 caracteres`() =
        runTest {
            val textSlot = slot<String>()
            coEvery {
                repository.updateNote(any(), any(), capture(textSlot), any(), any(), any(), any())
            } returns Unit
            val viewModel = buildViewModel()
            viewModel.startEditingNote("n1")

            viewModel.saveEdit("n1", text = "b".repeat(3_000))

            assertEquals(2_000, textSlot.captured.length)
            assertNull(viewModel.uiState.value.editingNoteId)
        }

    // Hallazgo N2: un doble-toque en "Guardar" con imagenes nuevas las copiaba dos veces.
    @Test
    fun `updateNote repetido guarda una sola vez`() =
        runTest {
            coEvery { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) } returns Unit
            val viewModel = buildViewModel()
            viewModel.startEditingNote("n1")

            viewModel.saveEdit("n1")
            viewModel.saveEdit("n1")

            coVerify(exactly = 1) { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `updateNote de una nota distinta a la que se edita se ignora`() =
        runTest {
            coEvery { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) } returns Unit
            val viewModel = buildViewModel()
            viewModel.startEditingNote("n1")

            viewModel.saveEdit("otra")

            coVerify(exactly = 0) { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) }
            assertEquals("n1", viewModel.uiState.value.editingNoteId)
        }

    @Test
    fun `updateNote sin editor abierto se ignora`() =
        runTest {
            coEvery { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) } returns Unit
            val viewModel = buildViewModel()

            viewModel.saveEdit("n1")

            coVerify(exactly = 0) { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `updateNote que falla no propaga la excepcion`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                gate.await()
                throw IllegalStateException("db")
            }
            val viewModel = buildViewModel()
            viewModel.startEditingNote("n1")
            viewModel.saveEdit("n1")

            gate.complete(Unit)

            assertNull(viewModel.uiState.value.editingNoteId)
        }
}
