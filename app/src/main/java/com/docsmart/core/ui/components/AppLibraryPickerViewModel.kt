package com.docsmart.core.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.features.library.data.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
class AppLibraryPickerViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _documents = MutableStateFlow<List<DocumentUiModel>>(emptyList())
    val documents: StateFlow<List<DocumentUiModel>> = _documents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _documents.value = repository.loadAllDocuments()
            _isLoading.value = false
        }
    }
}
