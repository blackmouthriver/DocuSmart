package com.docsmart.features.home.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
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

data class HomeUiState(
    val recentDocuments: List<DocumentUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val userName: String = "Usuario",
    val deleteError: String? = null,
    // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
    // Baja -- G9): antes un fallo real de lectura (BD/almacenamiento) se
    // veía exactamente igual que "no tienes documentos recientes" -- sin
    // ningún aviso, a diferencia de removeDocument() que sí expone
    // deleteError.
    val loadError: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        val adManager: AdManager,
        private val repository: DocumentRepository,
        private val trashRepository: TrashRepository,
        // ← NUEVO
        private val favoritesRepository: FavoritesRepository,
        // Bug real encontrado 2026-09-14: deleteError estaba hardcodeado en
        // español, saltándose el sistema de 12 idiomas.
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private companion object {
            const val RECENT_LIMIT = 5
        }

        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
        // Baja -- G10, corregido de otra forma tras la revisión adversarial
        // de la misma ronda): el primer intento de este fix ponía la carga
        // inicial acá, en `init{}`, y quitaba el `LaunchedEffect(Unit)` de
        // HomeScreen.kt por considerarlo redundante -- pero `init{}` solo
        // corre la primera vez que Hilt construye este ViewModel, y el
        // ViewModel SOBREVIVE un cambio de configuración (rotación), a
        // diferencia del `NavBackStackEntry`/Lifecycle que usa
        // `ReloadOnScreenResume` (que sí se recrea, y deliberadamente
        // descarta su primer ON_RESUME asumiendo que YA existe una carga
        // inicial propia del llamador). Sin `LaunchedEffect(Unit)`, ese hueco
        // quedaba sin ningún disparador -- "Recientes" podía quedar
        // desactualizado en silencio tras rotar el dispositivo. Se revierte:
        // la carga inicial vuelve a vivir en el `LaunchedEffect(Unit)` de
        // HomeScreen.kt (que sí se re-ejecuta en cada composición nueva,
        // incluida la que sigue a una rotación), y este ViewModel ya NO
        // carga en `init{}` -- así se evita la duplicación original (G10)
        // sin reabrir el hueco de refresco que encontró la revisión
        // adversarial.

        // Hallazgo real de la ronda 15: cada llamada a loadRecentDocuments()
        // (LaunchedEffect + ReloadOnScreenResume + rename) lanzaba una
        // corrutina independiente -- una carga vieja y lenta podia terminar
        // DESPUES de la nueva y pisar "Recientes" con datos obsoletos
        // (p. ej. un documento ya enviado a la papelera). Se cancela la
        // anterior al lanzar la siguiente.
        private var loadJob: Job? = null

        fun loadRecentDocuments() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, loadError = null) }
                    try {
                        // DocumentRepository ya aplica isFavorite desde FavoritesRepository.
                        // loadRecentlyOpened refleja uso real (RF-VIS/HOME), no solo la
                        // fecha de modificación del archivo.
                        val docs = repository.loadRecentlyOpened(limit = RECENT_LIMIT)
                        _uiState.update { state ->
                            state.copy(
                                recentDocuments = docs,
                                isLoading = false,
                            )
                        }
                        Timber.d("HomeViewModel: ${docs.size} documentos recientes")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Solo el tipo: CrashlyticsTree reenvia todo Timber.e a Firebase.
                        Timber.e("HomeViewModel: error cargando recientes: ${e.javaClass.simpleName}")
                        _uiState.update {
                            it.copy(isLoading = false, loadError = context.getString(R.string.home_load_recent_error))
                        }
                    }
                }
        }

        fun dismissLoadError() {
            _uiState.update { it.copy(loadError = null) }
        }

        fun toggleFavorite(documentId: String) {
            viewModelScope.launch {
                // Persiste en disco
                val isNowFavorite = favoritesRepository.toggleFavorite(documentId)

                // Actualiza UI en memoria inmediatamente
                _uiState.update { state ->
                    state.copy(
                        recentDocuments =
                            state.recentDocuments.map { doc ->
                                if (doc.id == documentId) doc.copy(isFavorite = isNowFavorite) else doc
                            },
                    )
                }
                Timber.d("HomeViewModel: toggleFavorite -> $isNowFavorite")
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
                _uiState.update { state ->
                    state.copy(
                        recentDocuments = state.recentDocuments.filter { it.id != documentId },
                    )
                }
                // Hallazgo real de la ronda 15: una carga en vuelo iniciada antes
                // de mover a la papelera puede traer el documento de vuelta al
                // terminar; se reinicia (loadRecentDocuments() cancela la anterior).
                if (loadJob?.isActive == true) loadRecentDocuments()
            }
        }

        fun dismissDeleteError() {
            _uiState.update { it.copy(deleteError = null) }
        }

        // Hallazgo real de la revisión general 2026-09-16: se descartaba el id
        // nuevo que devuelve repository.renameDocument() (la ruta cambia de
        // verdad para un archivo generado por la app) -- la tarjeta de
        // Recientes quedaba mostrando el nombre nuevo pero con el id VIEJO, que
        // ya no existe en disco; tocarla llevaba a un documento roto. Mismo
        // criterio ya usado por LibraryViewModel.renameDocument().
        fun renameDocument(
            documentId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                val newId = repository.renameDocument(documentId, newName)
                if (newId != documentId) {
                    loadRecentDocuments()
                } else {
                    _uiState.update { state ->
                        state.copy(
                            recentDocuments =
                                state.recentDocuments.map { doc ->
                                    if (doc.id == documentId) doc.copy(name = newName) else doc
                                },
                        )
                    }
                }
            }
        }
    }
