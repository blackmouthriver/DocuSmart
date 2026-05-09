package com.docsmart.features.scanner.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

enum class ScannerMode(val label: String) {
    DOCUMENT("Documento"),
    PHOTO("Foto")
}

data class ScannerUiState(
    val selectedMode: ScannerMode = ScannerMode.DOCUMENT,
    val scannedPages: List<Uri> = emptyList(),
    val isPdf: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun setMode(mode: ScannerMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onScanComplete(pages: List<Uri>, isPdf: Boolean = false) {
        Timber.d("ScannerViewModel: ${pages.size} páginas, isPdf=$isPdf")
        _uiState.update {
            it.copy(
                scannedPages = pages,
                isPdf = isPdf,
                isProcessing = false
            )
        }
    }

    fun onError(error: String) {
        _uiState.update { it.copy(error = error, isProcessing = false) }
    }

    fun reset() {
        _uiState.update { ScannerUiState() }
    }
}