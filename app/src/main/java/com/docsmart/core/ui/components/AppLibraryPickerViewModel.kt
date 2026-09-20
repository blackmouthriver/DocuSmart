package com.docsmart.core.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.features.library.data.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel mínimo compartido por el selector de archivo "desde mi
 * biblioteca" (item #15 del backlog UX, `backlog-mejoras-ux-2026-08-30.md`
 * §2) -- carga el mismo inventario que usa la pantalla Biblioteca
 * (`DocumentRepository.loadAllDocuments()`, ya excluye lo que está en la
 * Papelera) para que Seguridad y Herramientas PDF puedan ofrecer elegir
 * un archivo ya indexado por la app en vez de solo el selector del
 * sistema operativo.
 */
@HiltViewModel
class AppLibraryPickerViewModel
    @Inject
    constructor(
        private val repository: DocumentRepository,
    ) : ViewModel() {
        private val _documents = MutableStateFlow<List<DocumentUiModel>>(emptyList())
        val documents: StateFlow<List<DocumentUiModel>> = _documents.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        init {
            viewModelScope.launch {
                _isLoading.value = true
                // Ronda 18 (Media): una excepción de Room/MediaStore en
                // loadAllDocuments() (trashDao.getAll() corre fuera de su
                // try interno) tumbaba la app por la corrutina sin manejador,
                // y aun capturada dejaba el spinner girando para siempre. Se
                // registra solo el tipo (CrashlyticsTree reenvía >= WARN) y
                // el `finally` garantiza apagar el indicador de carga.
                try {
                    _documents.value = repository.loadAllDocuments()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e("AppLibraryPickerViewModel: no se pudo cargar la biblioteca (${e.javaClass.simpleName})")
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
