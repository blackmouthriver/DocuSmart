package com.docsmart.features.home.presentation

import android.content.Context
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * HomeViewModel: "Recientes" (carga, errores, carreras entre cargas),
 * favoritos, eliminar (papelera) y renombrar con migracion de id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private lateinit var repository: DocumentRepository
    private lateinit var trashRepository: TrashRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var context: Context

    private val first = doc("/files/a.pdf", "A")
    private val second = doc("content://media/external/downloads/2", "B")

    private fun doc(
        id: String,
        name: String,
    ) = DocumentUiModel(id = id, name = name, type = DocumentType.PDF, size = "1 KB", date = "01/01/2026")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk()
        trashRepository = mockk()
        favoritesRepository = mockk()
        context = mockk()
        every { context.getString(R.string.general_delete_error) } returns "delete-error"
        every { context.getString(R.string.home_load_recent_error) } returns "load-error"
        coEvery { repository.loadRecentlyOpened(5) } returns listOf(first, second)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        HomeViewModel(
            adManager = mockk<AdManager>(relaxed = true),
            repository = repository,
            trashRepository = trashRepository,
            favoritesRepository = favoritesRepository,
            context = context,
        )

    @Test
    fun `no carga nada hasta que la pantalla lo pide`() {
        val viewModel = buildViewModel()

        assertTrue(viewModel.uiState.value.recentDocuments.isEmpty())
        coVerify(exactly = 0) { repository.loadRecentlyOpened(any()) }
    }

    @Test
    fun `loadRecentDocuments publica los recientes con limite 5`() {
        val viewModel = buildViewModel()

        viewModel.loadRecentDocuments()

        val state = viewModel.uiState.value
        assertEquals(listOf(first, second), state.recentDocuments)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `un fallo de carga expone el error sin vaciar lo ya mostrado y se limpia al reintentar`() {
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()
        coEvery { repository.loadRecentlyOpened(5) } throws IOException("disco")

        viewModel.loadRecentDocuments()

        assertEquals("load-error", viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(first, second), viewModel.uiState.value.recentDocuments)

        coEvery { repository.loadRecentlyOpened(5) } returns listOf(first)
        viewModel.loadRecentDocuments()
        assertNull(viewModel.uiState.value.loadError)
        assertEquals(listOf(first), viewModel.uiState.value.recentDocuments)
    }

    @Test
    fun `dismissLoadError limpia el error`() {
        coEvery { repository.loadRecentlyOpened(5) } throws IOException("disco")
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()

        viewModel.dismissLoadError()

        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun `una carga vieja y lenta no pisa a una carga mas nueva`() =
        runTest {
            val slowGate = CompletableDeferred<List<DocumentUiModel>>()
            var calls = 0
            coEvery { repository.loadRecentlyOpened(5) } coAnswers {
                calls++
                if (calls == 1) slowGate.await() else listOf(second)
            }
            val viewModel = buildViewModel()

            viewModel.loadRecentDocuments()
            viewModel.loadRecentDocuments()
            slowGate.complete(listOf(first))

            assertEquals(listOf(second), viewModel.uiState.value.recentDocuments)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `toggleFavorite persiste y actualiza solo ese documento en memoria`() {
        coEvery { favoritesRepository.toggleFavorite(first.id) } returns true
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()

        viewModel.toggleFavorite(first.id)

        val docs = viewModel.uiState.value.recentDocuments
        assertTrue(docs.first { it.id == first.id }.isFavorite)
        assertFalse(docs.first { it.id == second.id }.isFavorite)
    }

    @Test
    fun `removeDocument exitoso lo quita de recientes`() {
        coEvery { trashRepository.moveToTrash(first.id) } returns true
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()

        viewModel.removeDocument(first.id)

        assertEquals(listOf(second), viewModel.uiState.value.recentDocuments)
        assertNull(viewModel.uiState.value.deleteError)
    }

    @Test
    fun `removeDocument fallido muestra el error y conserva la lista`() {
        coEvery { trashRepository.moveToTrash(first.id) } returns false
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()

        viewModel.removeDocument(first.id)

        assertEquals("delete-error", viewModel.uiState.value.deleteError)
        assertEquals(2, viewModel.uiState.value.recentDocuments.size)

        viewModel.dismissDeleteError()
        assertNull(viewModel.uiState.value.deleteError)
    }

    @Test
    fun `eliminar mientras hay una carga en vuelo reinicia esa carga para no resucitar el documento`() =
        runTest {
            val gate = CompletableDeferred<List<DocumentUiModel>>()
            var calls = 0
            coEvery { repository.loadRecentlyOpened(5) } coAnswers {
                calls++
                if (calls == 1) gate.await() else listOf(second)
            }
            coEvery { trashRepository.moveToTrash(first.id) } returns true
            val viewModel = buildViewModel()
            viewModel.loadRecentDocuments()

            viewModel.removeDocument(first.id)
            gate.complete(listOf(first, second))

            assertEquals(listOf(second), viewModel.uiState.value.recentDocuments)
        }

    @Test
    fun `renameDocument con el mismo id solo cambia el nombre en memoria`() {
        coEvery { repository.renameDocument(second.id, "Nuevo") } returns second.id
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()

        viewModel.renameDocument(second.id, "Nuevo")

        val docs = viewModel.uiState.value.recentDocuments
        assertEquals("Nuevo", docs.first { it.id == second.id }.name)
        assertEquals("A", docs.first { it.id == first.id }.name)
        coVerify(exactly = 1) { repository.loadRecentlyOpened(5) }
    }

    @Test
    fun `renameDocument con id nuevo recarga recientes para no dejar el id viejo`() {
        // Bug real ya corregido (2026-09-16): la tarjeta quedaba con el id
        // viejo, inexistente en disco, y tocarla llevaba a un documento roto.
        coEvery { repository.renameDocument(first.id, "b.pdf") } returns "/files/b.pdf"
        val viewModel = buildViewModel()
        viewModel.loadRecentDocuments()
        coEvery { repository.loadRecentlyOpened(5) } returns listOf(doc("/files/b.pdf", "b.pdf"), second)

        viewModel.renameDocument(first.id, "b.pdf")

        assertEquals(listOf("/files/b.pdf", second.id), viewModel.uiState.value.recentDocuments.map { it.id })
    }
}
