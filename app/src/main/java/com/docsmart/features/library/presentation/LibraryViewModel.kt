package com.docsmart.features.library.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Tab de la biblioteca
enum class LibraryTab { DEVICE, APP_FILES }

data class LibraryUiState(
    val allDocuments: List<DocumentUiModel> = emptyList(),
    val filteredDocuments: List<DocumentUiModel> = emptyList(),
    // ← NUEVO
    val deviceDocuments: List<DocumentUiModel> = emptyList(),
    // ← NUEVO
    val appDocuments: List<DocumentUiModel> = emptyList(),
    val favorites: List<DocumentUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: DocumentType? = null,
    // ← NUEVO
    val selectedTab: LibraryTab = LibraryTab.DEVICE,
    val isLoading: Boolean = false,
    val deleteError: String? = null,
    val linkFolderError: String? = null,
    // RF-VIS-07
    val trashCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        val adManager: AdManager,
        private val repository: DocumentRepository,
        private val trashRepository: TrashRepository,
        private val favoritesRepository: FavoritesRepository,
        private val downloadsAccessManager: DownloadsAccessManager,
        private val soundEffectPlayer: SoundEffectPlayer,
        // Bug real encontrado 2026-09-14: deleteError estaba hardcodeado en
        // español, saltándose el sistema de 12 idiomas.
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LibraryUiState())
        val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

        // Fila 22 del backlog UX: si el usuario vinculó Descargas por SAF, la
        // Biblioteca ve todos los PDF/Word/Excel/PowerPoint/Texto de esa
        // carpeta, no solo los que la app misma generó (ver DownloadsAccessManager).
        val linkedDownloadsFolderUri: StateFlow<Uri?> = downloadsAccessManager.linkedFolderUri

        fun downloadsFolderPickerInitialUri(): Uri? = downloadsAccessManager.initialUriHint()

        fun linkedFolderDisplayName(uri: Uri): String? = downloadsAccessManager.folderDisplayName(uri)

        fun onDownloadsFolderPicked(uri: Uri) {
            val linked = downloadsAccessManager.onFolderPicked(uri)
            if (!linked) {
                _uiState.update { it.copy(linkFolderError = context.getString(R.string.library_link_folder_error)) }
            }
            loadDocuments()
        }

        fun dismissLinkFolderError() {
            _uiState.update { it.copy(linkFolderError = null) }
        }

        fun unlinkDownloadsFolder() {
            downloadsAccessManager.unlink()
            loadDocuments()
        }

        // Hallazgo real de la ronda 15: cada llamada a loadDocuments() lanzaba
        // una corrutina independiente -- al vincular/desvincular la carpeta o
        // refrescar dos veces seguidas, una carga VIEJA (mas lenta) podia
        // terminar despues de la nueva y pisar el estado con datos obsoletos.
        // Se cancela la carga anterior antes de lanzar la siguiente.
        private var loadJob: Job? = null

        init {
            loadDocuments()
            loadTrashCount()
        }

        fun loadDocuments() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true) }
                    try {
                        val docs = repository.loadAllDocuments()
                        _uiState.update { state -> state.withDocuments(docs).copy(isLoading = false) }
                        Timber.d("LibraryViewModel: ${docs.size} docs cargados")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Solo el tipo: CrashlyticsTree reenvia todo Timber.e a Firebase.
                        Timber.e("LibraryViewModel: error cargando documentos: ${e.javaClass.simpleName}")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }

        // Recalcula TODAS las listas derivadas a partir de la lista completa y
        // del estado ACTUAL (filtros, pestana). Se usa siempre dentro de
        // `_uiState.update {}` para que una escritura concurrente no pise una
        // lista calculada a partir de un estado ya viejo.
        private fun LibraryUiState.withDocuments(docs: List<DocumentUiModel>): LibraryUiState {
            val deviceDocs = docs.filter { isDeviceDocument(it) }
            val appDocs = docs.filterNot { isDeviceDocument(it) }
            return copy(
                allDocuments = docs,
                deviceDocuments = deviceDocs,
                appDocuments = appDocs,
                filteredDocuments =
                    applyCurrentFilters(
                        if (selectedTab == LibraryTab.DEVICE) deviceDocs else appDocs,
                        this,
                    ),
                favorites = docs.filter { it.isFavorite },
            )
        }

        // Ampliado 2026-09-03 (fila 22 backlog UX): un documento del historial
        // puede venir de CUALQUIER proveedor de contenido externo (WhatsApp,
        // Gmail, otro gestor de archivos), no solo MediaStore/SAF de Android --
        // la lista fija anterior (content://media, content://com.android,
        // content://downloads) clasificaba esos casos como "Mis archivos" por
        // defecto, lo cual es incorrecto: no los creó la app. Los documentos que
        // sí genera la app (loadAppGeneratedFiles()) siempre usan una ruta
        // absoluta como id, nunca un content:// -- por eso "cualquier content://"
        // es del dispositivo es una regla más simple y más correcta que una
        // lista de prefijos conocidos.
        private fun isDeviceDocument(doc: DocumentUiModel): Boolean = doc.id.startsWith("content://")

        // ── Tab seleccionado ──────────────────────────────────────────────────────
        fun onTabSelected(tab: LibraryTab) {
            _uiState.update { state ->
                val sourceDocs =
                    if (tab == LibraryTab.DEVICE) {
                        state.deviceDocuments
                    } else {
                        state.appDocuments
                    }
                state.copy(
                    selectedTab = tab,
                    // reset filtro al cambiar tab
                    selectedCategory = null,
                    // reset búsqueda al cambiar tab
                    searchQuery = "",
                    filteredDocuments = sourceDocs,
                )
            }
        }

        fun toggleFavorite(documentId: String) {
            viewModelScope.launch {
                val isNowFavorite = favoritesRepository.toggleFavorite(documentId)
                // Hallazgo real de la ronda 15: la lista se calculaba FUERA de
                // update{} a partir de un snapshot -- si una recarga terminaba
                // mientras tanto, esta escritura pisaba la lista nueva con una
                // vieja. Ahora se recalcula dentro del update, sobre el estado
                // vigente.
                _uiState.update { state ->
                    state.withDocuments(
                        state.allDocuments.map { doc ->
                            if (doc.id == documentId) doc.copy(isFavorite = isNowFavorite) else doc
                        },
                    )
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { state ->
                val sourceDocs =
                    if (state.selectedTab == LibraryTab.DEVICE) {
                        state.deviceDocuments
                    } else {
                        state.appDocuments
                    }
                val filtered = applyCurrentFilters(sourceDocs, state.copy(searchQuery = query))
                state.copy(searchQuery = query, filteredDocuments = filtered)
            }
        }

        fun onCategorySelected(type: DocumentType?) {
            _uiState.update { state ->
                val newCategory = if (state.selectedCategory == type) null else type
                val sourceDocs =
                    if (state.selectedTab == LibraryTab.DEVICE) {
                        state.deviceDocuments
                    } else {
                        state.appDocuments
                    }
                val filtered = applyCurrentFilters(sourceDocs, state.copy(selectedCategory = newCategory))
                state.copy(selectedCategory = newCategory, filteredDocuments = filtered)
            }
        }

        fun clearSearch() {
            onSearchQueryChange("")
        }

        fun refresh() {
            loadDocuments()
        }

        private fun applyCurrentFilters(
            docs: List<DocumentUiModel>,
            state: LibraryUiState,
        ): List<DocumentUiModel> =
            docs.filter { doc ->
                val matchesQuery =
                    state.searchQuery.isBlank() ||
                        doc.name.contains(state.searchQuery, ignoreCase = true)
                val matchesCategory =
                    state.selectedCategory == null ||
                        doc.type == state.selectedCategory
                matchesQuery && matchesCategory
            }

        fun renameDocument(
            documentId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                val newId = repository.renameDocument(documentId, newName)
                if (newId != documentId) {
                    loadDocuments()
                } else {
                    updateNameInState(documentId, newName)
                }
            }
        }

        private fun updateNameInState(
            documentId: String,
            newName: String,
        ) {
            _uiState.update { state ->
                state.withDocuments(
                    state.allDocuments.map { doc ->
                        if (doc.id == documentId) doc.copy(name = newName) else doc
                    },
                )
            }
        }

        // RF-VIS-07: "eliminar" mueve a la papelera, no borra de inmediato -- ver
        // DocumentRepository.moveToTrash().
        fun removeDocument(documentId: String) {
            viewModelScope.launch {
                val movedToTrash = trashRepository.moveToTrash(documentId)
                if (!movedToTrash) {
                    _uiState.update { it.copy(deleteError = context.getString(R.string.general_delete_error)) }
                    return@launch
                }
                soundEffectPlayer.playDelete()

                _uiState.update { state ->
                    state.withDocuments(state.allDocuments.filter { it.id != documentId })
                }
                // Hallazgo real de la ronda 15: una carga iniciada ANTES de mover
                // a la papelera pudo haber leido trash_entries sin este id y
                // "resucitar" el documento al terminar. Se reinicia esa carga
                // en vuelo (loadDocuments() cancela la anterior).
                if (loadJob?.isActive == true) loadDocuments()
                loadTrashCount()
            }
        }

        fun dismissDeleteError() {
            _uiState.update { it.copy(deleteError = null) }
        }

        fun loadTrashCount() {
            viewModelScope.launch {
                // Hallazgo real de la ronda 15: una excepcion aqui (BD, purga)
                // escapaba de la corrutina del ViewModelScope y tumbaba la app
                // solo por no poder mostrar el contador de Papelera.
                try {
                    val count = trashRepository.loadTrashedDocuments().size
                    _uiState.update { it.copy(trashCount = count) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("LibraryViewModel: no se pudo contar la papelera: ${e.javaClass.simpleName}")
                }
            }
        }
    }
