package com.docsmart.features.converter.presentation

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.usecase.ConvertImageToPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ConverterUiState(
    val selectedImages: List<Uri> = emptyList(),
    val fileName: String = "",
    val isConverting: Boolean = false,
    val conversionResult: ConversionResult? = null,
    val outputFile: File? = null,
    val savedToDownloads: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val convertImageToPdf: ConvertImageToPdfUseCase,
    val adManager: AdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    // ── Selección de imágenes ─────────────────────────
    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                selectedImages = uris,
                conversionResult = null,
                errorMessage = null,
                savedToDownloads = false
            )
        }
    }

    // ── Nombre del archivo ────────────────────────────
    fun onFileNameChange(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    // ── Eliminar imagen ───────────────────────────────
    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.filter { it != uri }
            )
        }
    }

    // ── Limpiar todo ──────────────────────────────────
    fun clearAll() {
        _uiState.update { ConverterUiState() }
    }

    // ── Convertir a PDF ───────────────────────────────
    fun convertToPdf() {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) return

        val customName = _uiState.value.fileName
            .trim()
            .ifBlank { null }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isConverting = true, errorMessage = null)
            }

            val result = convertImageToPdf(
                imageUris = images,
                fileName = customName ?: generateDefaultName()
            )

            _uiState.update { state ->
                when (result) {
                    is ConversionResult.Success -> state.copy(
                        isConverting = false,
                        conversionResult = result,
                        outputFile = result.outputFile
                    )
                    is ConversionResult.Error -> state.copy(
                        isConverting = false,
                        errorMessage = result.message
                    )
                    else -> state.copy(isConverting = false)
                }
            }
        }
    }

    // ── Guardar en Descargas ──────────────────────────
    fun saveToDownloads(context: Context) {
        val file = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            try {
                val saved = copyToDownloads(context, file)
                _uiState.update { state ->
                    if (saved) state.copy(savedToDownloads = true)
                    else state.copy(
                        errorMessage = "No se pudo guardar en Descargas"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Error: ${e.message}")
                }
            }
        }
    }

    private fun copyToDownloads(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false

                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                file.copyTo(File(downloadsDir, file.name), overwrite = true)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ── Dismiss error ─────────────────────────────────
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun generateDefaultName(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())
        return "DocuSmart_$timestamp"
    }
}