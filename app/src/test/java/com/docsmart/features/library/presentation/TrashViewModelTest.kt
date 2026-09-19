package com.docsmart.features.library.presentation

import android.content.Context
import android.content.IntentSender
import app.cash.turbine.test
import com.docsmart.R
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.library.data.TrashedDocumentUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * TrashViewModel: dias restantes, restaurar, borrado definitivo individual y
 * en lote (con y sin permiso de sistema), guard de doble toque y caminos de
 * error (ronda 15: antes un fallo del repositorio dejaba el spinner infinito
 * o tumbaba la app).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    private lateinit var repository: TrashRepository
    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var context: Context

    private val dayMillis = 24L * 60 * 60 * 1000

    private fun doc(id: String) = DocumentUiModel(id = id, name = id, type = DocumentType.PDF, size = "1 KB", date = "01/01/2026")

    private fun trashed(
        id: String,
        deletedAt: Long = System.currentTimeMillis(),
    ) = TrashedDocumentUiModel(doc(id), deletedAt)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk()
        soundEffectPlayer = mockk(relaxed = true)
        context = mockk()
        every { context.getString(R.string.general_delete_error) } returns "delete-error"
        every { context.getString(R.string.trash_restore_error) } returns "restore-error"
        every { context.getString(R.string.trash_bulk_delete_partial_error) } returns "partial-error"
        coEvery { repository.loadTrashedDocuments() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = TrashViewModel(repository, soundEffectPlayer, context)

    @Test
    fun `load calcula los dias restantes y los acota entre 0 y el plazo de retencion`() {
        val now = System.currentTimeMillis()
        coEvery { repository.loadTrashedDocuments() } returns
            listOf(
                trashed("hace10dias", now - 10 * dayMillis - 5_000L),
                // Reloj atrasado: deletedAt en el futuro -> elapsedDays negativo.
                trashed("futuro", now + 5 * dayMillis),
                trashed("vencido", now - 100 * dayMillis),
            )

        val items = buildViewModel().uiState.value.items

        assertEquals(listOf(20, TrashRepository.TRASH_RETENTION_DAYS, 0), items.map { it.daysRemaining })
        assertEquals(listOf("hace10dias", "futuro", "vencido"), items.map { it.document.id })
    }

    @Test
    fun `si la carga falla no deja el spinner infinito y avisa el error`() {
        coEvery { repository.loadTrashedDocuments() } throws IOException("disco")

        val state = buildViewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("delete-error", state.actionError)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `restore exitoso no muestra error y recarga la lista`() {
        coEvery { repository.restoreFromTrash("a") } returns true
        val viewModel = buildViewModel()

        viewModel.restore("a")

        assertNull(viewModel.uiState.value.actionError)
        coVerify(exactly = 2) { repository.loadTrashedDocuments() }
    }

    @Test
    fun `restore de un archivo que ya no existe avisa el error pero igual recarga`() {
        coEvery { repository.restoreFromTrash("a") } returns false
        val viewModel = buildViewModel()

        viewModel.restore("a")

        assertEquals("restore-error", viewModel.uiState.value.actionError)
        coVerify(exactly = 2) { repository.loadTrashedDocuments() }
    }

    @Test
    fun `restore con excepcion del repositorio avisa el error en vez de crashear`() {
        coEvery { repository.restoreFromTrash("a") } throws IllegalStateException("room")
        val viewModel = buildViewModel()

        viewModel.restore("a")

        assertEquals("restore-error", viewModel.uiState.value.actionError)
    }

    @Test
    fun `dismissError limpia el error`() {
        coEvery { repository.restoreFromTrash("a") } returns false
        val viewModel = buildViewModel()
        viewModel.restore("a")

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `deleteForever Deleted suena y recarga`() {
        coEvery { repository.deleteForever("a") } returns DocumentRepository.DeleteOutcome.Deleted
        val viewModel = buildViewModel()

        viewModel.deleteForever("a")

        verify { soundEffectPlayer.playDelete() }
        coVerify(exactly = 2) { repository.loadTrashedDocuments() }
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `deleteForever Failed muestra el error y no suena`() {
        coEvery { repository.deleteForever("a") } returns DocumentRepository.DeleteOutcome.Failed
        val viewModel = buildViewModel()

        viewModel.deleteForever("a")

        assertEquals("delete-error", viewModel.uiState.value.actionError)
        verify(exactly = 0) { soundEffectPlayer.playDelete() }
    }

    @Test
    fun `deleteForever NeedsPermission emite el pedido de borrado individual`() =
        runTest {
            val sender = mockk<IntentSender>()
            coEvery { repository.deleteForever("a") } returns DocumentRepository.DeleteOutcome.NeedsPermission(sender)
            val viewModel = buildViewModel()

            viewModel.pendingDeleteRequest.test {
                viewModel.deleteForever("a")

                val request = awaitItem()
                assertTrue(request is PendingDeleteRequest.Single)
                assertEquals("a", (request as PendingDeleteRequest.Single).documentId)
                assertEquals(sender, request.intentSender)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteForever ignora un segundo toque mientras el primero sigue en curso`() =
        runTest {
            val gate = CompletableDeferred<DocumentRepository.DeleteOutcome>()
            coEvery { repository.deleteForever("a") } coAnswers { gate.await() }
            val viewModel = buildViewModel()

            viewModel.deleteForever("a")
            viewModel.deleteForever("a")
            gate.complete(DocumentRepository.DeleteOutcome.Deleted)

            coVerify(exactly = 1) { repository.deleteForever("a") }
        }

    @Test
    fun `deleteForever con excepcion avisa el error y libera el guard de doble toque`() {
        // Antes: la excepcion escapaba de la corrutina (crash) y, si el finally
        // no la hubiera cubierto, dejaba isBusy trabado.
        coEvery { repository.deleteForever("a") } throws IllegalStateException("room") andThen
            DocumentRepository.DeleteOutcome.Deleted
        val viewModel = buildViewModel()

        viewModel.deleteForever("a")
        assertEquals("delete-error", viewModel.uiState.value.actionError)

        viewModel.deleteForever("a")
        coVerify(exactly = 2) { repository.deleteForever("a") }
        verify { soundEffectPlayer.playDelete() }
    }

    @Test
    fun `onSingleDeleteConfirmed limpia la papelera, suena y recarga`() {
        coEvery { repository.finalizeDeleteForever("a") } returns Unit
        val viewModel = buildViewModel()

        viewModel.onSingleDeleteConfirmed("a")

        coVerify { repository.finalizeDeleteForever("a") }
        verify { soundEffectPlayer.playDelete() }
        coVerify(exactly = 2) { repository.loadTrashedDocuments() }
    }

    @Test
    fun `onBulkDeleteConfirmed limpia todos los ids confirmados`() {
        coEvery { repository.finalizeDeleteForever(listOf("a", "b")) } returns Unit
        val viewModel = buildViewModel()

        viewModel.onBulkDeleteConfirmed(listOf("a", "b"))

        coVerify { repository.finalizeDeleteForever(listOf("a", "b")) }
        verify { soundEffectPlayer.playDelete() }
    }

    @Test
    fun `deleteAll con la papelera vacia no llama al repositorio`() {
        val viewModel = buildViewModel()

        viewModel.deleteAll()

        coVerify(exactly = 0) { repository.deleteAllForever(any()) }
    }

    @Test
    fun `deleteAll Done suena y recarga usando los ids visibles`() {
        coEvery { repository.loadTrashedDocuments() } returns listOf(trashed("a"), trashed("b"))
        coEvery { repository.deleteAllForever(listOf("a", "b")) } returns TrashRepository.BulkDeleteOutcome.Done
        val viewModel = buildViewModel()

        viewModel.deleteAll()

        coVerify { repository.deleteAllForever(listOf("a", "b")) }
        verify { soundEffectPlayer.playDelete() }
        coVerify(exactly = 2) { repository.loadTrashedDocuments() }
    }

    @Test
    fun `deleteAll PartialNeedsPermission avisa que algunos hay que borrarlos uno por uno`() {
        coEvery { repository.loadTrashedDocuments() } returns listOf(trashed("a"))
        coEvery { repository.deleteAllForever(listOf("a")) } returns
            TrashRepository.BulkDeleteOutcome.PartialNeedsPermission
        val viewModel = buildViewModel()

        viewModel.deleteAll()

        assertEquals("partial-error", viewModel.uiState.value.actionError)
        verify(exactly = 0) { soundEffectPlayer.playDelete() }
    }

    @Test
    fun `deleteAll NeedsPermission emite el pedido en lote con los ids`() =
        runTest {
            val sender = mockk<IntentSender>()
            coEvery { repository.loadTrashedDocuments() } returns listOf(trashed("a"), trashed("b"))
            coEvery { repository.deleteAllForever(listOf("a", "b")) } returns
                TrashRepository.BulkDeleteOutcome.NeedsPermission(sender, listOf("a", "b"))
            val viewModel = buildViewModel()

            viewModel.pendingDeleteRequest.test {
                viewModel.deleteAll()

                val request = awaitItem()
                assertTrue(request is PendingDeleteRequest.Bulk)
                assertEquals(listOf("a", "b"), (request as PendingDeleteRequest.Bulk).documentIds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteAll con excepcion avisa el error, recarga y libera el guard`() {
        coEvery { repository.loadTrashedDocuments() } returns listOf(trashed("a"))
        coEvery { repository.deleteAllForever(listOf("a")) } throws IllegalStateException("room") andThen
            TrashRepository.BulkDeleteOutcome.Done
        val viewModel = buildViewModel()

        viewModel.deleteAll()
        assertEquals("delete-error", viewModel.uiState.value.actionError)

        viewModel.deleteAll()
        coVerify(exactly = 2) { repository.deleteAllForever(listOf("a")) }
    }
}
