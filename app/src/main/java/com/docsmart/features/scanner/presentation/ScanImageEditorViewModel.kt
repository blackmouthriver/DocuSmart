package com.docsmart.features.scanner.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.features.scanner.domain.ScanColorMode
import com.docsmart.features.scanner.domain.ScanImageEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * RF-SCAN-06/RF-SCAN-07: envuelve [ScanImageEditor] (una clase de dominio
 * simple, sin estado) en un ViewModel solo para poder obtenerlo con
 * `hiltViewModel()` desde `ScanResultScreen` -- mismo patrón que el resto
 * de la app usa para inyectar dependencias en Composables.
 */
@HiltViewModel
class ScanImageEditorViewModel @Inject constructor(
    private val editor: ScanImageEditor
) : ViewModel() {

    fun applyAdjustments(
        uri: Uri,
        brightness: Int,
        contrast: Int,
        scalePercent: Int,
        onResult: (Uri?) -> Unit
    ) {
        viewModelScope.launch {
            onResult(editor.applyAdjustments(uri, brightness, contrast, scalePercent))
        }
    }

    // HU-41: suspend directo (no callback) -- se llama desde un
    // LaunchedEffect de ScanResultScreen que ya corre en su propio
    // coroutine scope, a diferencia de applyAdjustments() arriba (disparado
    // desde el onClick no-suspend del botón "Aplicar" del editor).
    suspend fun applyColorMode(uri: Uri, mode: ScanColorMode): Uri? =
        editor.applyColorMode(uri, mode)
}
