package com.docsmart.features.library.presentation

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.DownloadsAccessManager
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
import kotlinx.coroutines.flow.MutableStateFlow
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
 * LibraryViewModel: particion dispositivo/app, filtros, favoritos, papelera
 * y las carreras entre cargas (ronda 15). El repositorio se mockea completo:
 * la lectura real de MediaStore no es testeable sin Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private lateinit var repository: DocumentRepository
    private lateinit var trashRepository: TrashRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var downloadsAccessManager: DownloadsAccessManager
    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var context: Context

    private val deviceDoc = doc("content://media/external/downloads/1", "Contrato", DocumentType.PDF)
    private val deviceImage = doc("content://media/external/images/2", "Foto playa", DocumentType.IMAGE)
    private val appDoc = doc("/data/files/converted/resumen.docx", "Resumen", DocumentType.WORD, favorite = true)

    private fun doc(
        id: String,
        name: String,
        type: DocumentType,
        favorite: Boolean = false,
    ) = DocumentUiModel(id = id, name = name, type = type, size = "1 KB", date = "01/01/2026", isFavorite = favorite)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk()
        trashRepository = mockk()
        favoritesRepository = mockk()
        downloadsAccessManager = mockk(relaxed = true)
        every { downloadsAccessManager.linkedFolderUri } returns MutableStateFlow<Uri?>(null)
        soundEffectPlayer = mockk(relaxed = true)
        context = mockk()
        every { context.getString(R.string.general_delete_error) } returns "delete-error"
        every { context.getString(R.string.library_link_folder_error) } returns "link-error"
        coEvery { repository.loadAllDocuments() } returns listOf(deviceDoc, deviceImage, appDoc)
        coEvery { trashRepository.loadTrashedDocuments() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        LibraryViewModel(
            adManager = mockk<AdManager>(relaxed = true),
            repository = repository,
            trashRepository = trashRepository,
            favoritesRepository = favoritesRepository,
            downloadsAccessManager = downloadsAccessManager,
            soundEffectPlayer = soundEffectPlayer,
            context = context,
        )

    private fun trashed(count: Int) = List(count) { TrashedDocumentUiModel(deviceDoc.copy(id = "t$it"), 1L) }

    @Test
    fun `al iniciar carga y separa documentos del dispositivo y de la app`() {
        val state = buildViewModel().uiState.value

        assertEquals(listOf(deviceDoc, deviceImage, appDoc), state.allDocuments)
        assertEquals(listOf(deviceDoc, deviceImage), state.deviceDocuments)
        assertEquals(listOf(appDoc), state.appDocuments)
        assertEquals(listOf(appDoc), state.favorites)
        assertEquals(LibraryTab.DEVICE, state.selectedTab)
        assertEquals(listOf(deviceDoc, deviceImage), state.filteredDocuments)
        assertFalse(state.isLoading)
    }

    @Test
    fun `un content Uri de cualquier proveedor cuenta como documento del dispositivo`() {
        val whatsapp = doc("content://com.whatsapp.provider.media/item/9", "Recibo", DocumentType.PDF)
        coEvery { repository.loadAllDocuments() } returns listOf(whatsapp, appDoc)

        val state = buildViewModel().uiState.value

        assertEquals(listOf(whatsapp), state.deviceDocuments)
        assertEquals(listOf(appDoc), state.appDocuments)
    }

    @Test
    fun `el contador de papelera se carga al iniciar`() {
        coEvery { trashRepository.loadTrashedDocuments() } returns trashed(3)

        assertEquals(3, buildViewModel().uiState.value.trashCount)
    }

    @Test
    fun `si la carga falla no crashea y quita el estado de carga`() {
        coEvery { repository.loadAllDocuments() } throws IOException("disco")

        val state = buildViewModel().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.allDocuments.isEmpty())
    }

    @Test
    fun `si contar la papelera falla no crashea y el resto del estado queda bien`() {
        // Antes la excepcion escapaba de la corrutina del viewModelScope y
        // tumbaba la app solo por no poder mostrar el contador.
        coEvery { trashRepository.loadTrashedDocuments() } throws IllegalStateException("room")

        val state = buildViewModel().uiState.value

        assertEquals(0, state.trashCount)
        assertEquals(3, state.allDocuments.size)
    }

    @Test
    fun `la busqueda filtra por nombre sin distinguir mayusculas dentro de la pestana actual`() {
        val viewModel = buildViewModel()

        viewModel.onSearchQueryChange("CONTR")

        assertEquals(listOf(deviceDoc), viewModel.uiState.value.filteredDocuments)
        // "Resumen" es de la pestana APP_FILES: no aparece en la de dispositivo.
        viewModel.onSearchQueryChange("resumen")
        assertTrue(viewModel.uiState.value.filteredDocuments.isEmpty())
    }

    @Test
    fun `clearSearch restaura la lista completa de la pestana`() {
        val viewModel = buildViewModel()
        viewModel.onSearchQueryChange("foto")

        viewModel.clearSearch()

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(listOf(deviceDoc, deviceImage), viewModel.uiState.value.filteredDocuments)
    }

    @Test
    fun `seleccionar dos veces la misma categoria la quita`() {
        val viewModel = buildViewModel()

        viewModel.onCategorySelected(DocumentType.IMAGE)
        assertEquals(listOf(deviceImage), viewModel.uiState.value.filteredDocuments)

        viewModel.onCategorySelected(DocumentType.IMAGE)
        assertNull(viewModel.uiState.value.selectedCategory)
        assertEquals(listOf(deviceDoc, deviceImage), viewModel.uiState.value.filteredDocuments)
    }

    @Test
    fun `busqueda y categoria se combinan con AND`() {
        val viewModel = buildViewModel()

        viewModel.onCategorySelected(DocumentType.PDF)
        viewModel.onSearchQueryChange("foto")

        assertTrue(viewModel.uiState.value.filteredDocuments.isEmpty())
    }

    @Test
    fun `cambiar de pestana reinicia filtros y muestra los documentos de esa pestana`() {
        val viewModel = buildViewModel()
        viewModel.onSearchQueryChange("foto")
        viewModel.onCategorySelected(DocumentType.IMAGE)

        viewModel.onTabSelected(LibraryTab.APP_FILES)

        val state = viewModel.uiState.value
        assertEquals(LibraryTab.APP_FILES, state.selectedTab)
        assertEquals("", state.searchQuery)
        assertNull(state.selectedCategory)
        assertEquals(listOf(appDoc), state.filteredDocuments)
    }

    @Test
    fun `los filtros activos se conservan al recargar`() {
        val viewModel = buildViewModel()
        viewModel.onSearchQueryChange("foto")

        viewModel.refresh()

        assertEquals(listOf(deviceImage), viewModel.uiState.value.filteredDocuments)
    }

    @Test
    fun `toggleFavorite actualiza el flag y la lista de favoritos sin recargar`() {
        coEvery { favoritesRepository.toggleFavorite(deviceDoc.id) } returns true
        val viewModel = buildViewModel()

        viewModel.toggleFavorite(deviceDoc.id)

        val state = viewModel.uiState.value
        assertTrue(state.allDocuments.first { it.id == deviceDoc.id }.isFavorite)
        assertEquals(setOf(deviceDoc.id, appDoc.id), state.favorites.map { it.id }.toSet())
        assertTrue(state.filteredDocuments.first { it.id == deviceDoc.id }.isFavorite)
        coVerify(exactly = 1) { repository.loadAllDocuments() }
    }

    @Test
    fun `toggleFavorite puede quitar un favorito`() {
        coEvery { favoritesRepository.toggleFavorite(appDoc.id) } returns false
        val viewModel = buildViewModel()

        viewModel.toggleFavorite(appDoc.id)

        assertTrue(viewModel.uiState.value.favorites.isEmpty())
    }

    @Test
    fun `removeDocument exitoso lo saca de las listas, suena y actualiza el contador`() {
        coEvery { trashRepository.moveToTrash(deviceDoc.id) } returns true
        val viewModel = buildViewModel()
        coEvery { trashRepository.loadTrashedDocuments() } returns trashed(1)

        viewModel.removeDocument(deviceDoc.id)

        val state = viewModel.uiState.value
        assertEquals(listOf(deviceImage, appDoc), state.allDocuments)
        assertEquals(listOf(deviceImage), state.filteredDocuments)
        assertEquals(1, state.trashCount)
        assertNull(state.deleteError)
        verify { soundEffectPlayer.playDelete() }
    }

    @Test
    fun `removeDocument fallido muestra el error y no toca la lista`() {
        coEvery { trashRepository.moveToTrash(deviceDoc.id) } returns false
        val viewModel = buildViewModel()

        viewModel.removeDocument(deviceDoc.id)

        val state = viewModel.uiState.value
        assertEquals("delete-error", state.deleteError)
        assertEquals(3, state.allDocuments.size)
        verify(exactly = 0) { soundEffectPlayer.playDelete() }

        viewModel.dismissDeleteError()
        assertNull(viewModel.uiState.value.deleteError)
    }

    @Test
    fun `renameDocument con el mismo id solo actualiza el nombre en memoria`() {
        coEvery { repository.renameDocument(deviceDoc.id, "Nuevo") } returns deviceDoc.id
        val viewModel = buildViewModel()

        viewModel.renameDocument(deviceDoc.id, "Nuevo")

        assertEquals("Nuevo", viewModel.uiState.value.allDocuments.first { it.id == deviceDoc.id }.name)
        assertEquals("Nuevo", viewModel.uiState.value.filteredDocuments.first { it.id == deviceDoc.id }.name)
        coVerify(exactly = 1) { repository.loadAllDocuments() }
    }

    @Test
    fun `renameDocument con id nuevo recarga desde el repositorio`() {
        coEvery { repository.renameDocument(appDoc.id, "otro.docx") } returns "/data/files/converted/otro.docx"
        val viewModel = buildViewModel()

        viewModel.renameDocument(appDoc.id, "otro.docx")

        coVerify(exactly = 2) { repository.loadAllDocuments() }
    }

    @Test
    fun `una carga vieja y lenta no pisa el resultado de una carga mas nueva`() =
        runTest {
            // Escenario real: refresh() dos veces (o vincular carpeta mientras
            // la primera carga sigue en curso). Antes la primera, al terminar
            // ultima, sobrescribia el estado con datos obsoletos.
            val slowGate = CompletableDeferred<List<DocumentUiModel>>()
            var calls = 0
            coEvery { repository.loadAllDocuments() } coAnswers {
                calls++
                if (calls == 1) slowGate.await() else listOf(appDoc)
            }

            val viewModel = buildViewModel()
            viewModel.refresh()
            slowGate.complete(listOf(deviceDoc, deviceImage))

            assertEquals(listOf(appDoc), viewModel.uiState.value.allDocuments)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `eliminar mientras hay una carga en vuelo reinicia esa carga para no resucitar el documento`() =
        runTest {
            val gate = CompletableDeferred<List<DocumentUiModel>>()
            var calls = 0
            coEvery { repository.loadAllDocuments() } coAnswers {
                calls++
                // La carga inicial (en vuelo) todavia incluye el documento.
                if (calls == 1) gate.await() else listOf(deviceImage, appDoc)
            }
            coEvery { trashRepository.moveToTrash(deviceDoc.id) } returns true
            val viewModel = buildViewModel()

            viewModel.removeDocument(deviceDoc.id)
            gate.complete(listOf(deviceDoc, deviceImage, appDoc))

            assertEquals(listOf(deviceImage, appDoc), viewModel.uiState.value.allDocuments)
        }

    @Test
    fun `onDownloadsFolderPicked con exito no marca error y recarga`() {
        every { downloadsAccessManager.onFolderPicked(any()) } returns true
        val viewModel = buildViewModel()

        viewModel.onDownloadsFolderPicked(mockk())

        assertNull(viewModel.uiState.value.linkFolderError)
        coVerify(exactly = 2) { repository.loadAllDocuments() }
    }

    @Test
    fun `onDownloadsFolderPicked fallido expone el error y se puede descartar`() {
        every { downloadsAccessManager.onFolderPicked(any()) } returns false
        val viewModel = buildViewModel()

        viewModel.onDownloadsFolderPicked(mockk())
        assertEquals("link-error", viewModel.uiState.value.linkFolderError)

        viewModel.dismissLinkFolderError()
        assertNull(viewModel.uiState.value.linkFolderError)
    }

    @Test
    fun `unlinkDownloadsFolder desvincula y recarga`() {
        val viewModel = buildViewModel()

        viewModel.unlinkDownloadsFolder()

        verify { downloadsAccessManager.unlink() }
        coVerify(exactly = 2) { repository.loadAllDocuments() }
    }
}
