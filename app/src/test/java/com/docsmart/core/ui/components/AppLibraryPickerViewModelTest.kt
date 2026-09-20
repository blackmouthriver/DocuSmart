package com.docsmart.core.ui.components

import com.docsmart.features.library.data.DocumentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Ronda 18: cubre la carga inicial del selector "desde mi biblioteca" y el
 * hallazgo real de que una excepción en `loadAllDocuments()` tumbaba la app
 * y dejaba `isLoading` en true para siempre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLibraryPickerViewModelTest {
    private lateinit var repository: DocumentRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun document(id: String) =
        DocumentUiModel(
            id = id,
            name = "doc-$id.pdf",
            type = DocumentType.PDF,
            size = "1 MB",
            date = "Hoy",
        )

    @Test
    fun `al crearse carga los documentos y apaga el indicador de carga`() {
        val docs = listOf(document("1"), document("2"))
        coEvery { repository.loadAllDocuments() } returns docs

        val viewModel = AppLibraryPickerViewModel(repository)

        assertEquals(docs, viewModel.documents.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `mientras la carga esta pendiente isLoading es true y la lista vacia`() {
        val pending = CompletableDeferred<List<DocumentUiModel>>()
        coEvery { repository.loadAllDocuments() } coAnswers { pending.await() }

        val viewModel = AppLibraryPickerViewModel(repository)

        assertTrue(viewModel.isLoading.value)
        assertTrue(viewModel.documents.value.isEmpty())

        val docs = listOf(document("9"))
        pending.complete(docs)

        assertEquals(docs, viewModel.documents.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `si la carga falla no propaga la excepcion y apaga isLoading`() {
        coEvery { repository.loadAllDocuments() } throws IllegalStateException("boom")

        val viewModel = AppLibraryPickerViewModel(repository)

        assertTrue(viewModel.documents.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `biblioteca vacia deja la lista vacia sin error`() {
        coEvery { repository.loadAllDocuments() } returns emptyList()

        val viewModel = AppLibraryPickerViewModel(repository)

        assertTrue(viewModel.documents.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }
}
