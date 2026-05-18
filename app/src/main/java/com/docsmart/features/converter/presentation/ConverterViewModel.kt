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
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.model.getCategoryLabel
import com.docsmart.features.converter.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ConverterUiState(
    val selectedCategory: String? = null,
    val selectedType: ConversionType? = null,
    val filteredTypes: List<ConversionType> = emptyList(),
    val selectedFiles: List<Uri> = emptyList(),
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
    private val pdfToImage       : PdfToImageUseCase,
    private val pdfToText        : PdfToTextUseCase,
    private val pdfToWord        : PdfToWordUseCase,
    private val pdfToHtml        : PdfToHtmlUseCase,
    private val imageFormat      : ImageFormatUseCase,
    private val wordToPdf        : WordToPdfUseCase,
    private val wordToHtml       : WordToHtmlUseCase,
    private val excelToPdf       : ExcelToPdfUseCase,
    private val excelToHtml      : ExcelToHtmlUseCase,
    private val pptToText        : PptToTextUseCase,
    val adManager                : AdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    // ── Seleccionar categoría ─────────────────────────
    fun onCategorySelected(category: String) {
        // ── Normalizar a clave en inglés para filtrar ──────
        // El label mostrado puede estar en cualquier idioma
        // pero el filtro interno siempre usa el enum
        val types = ConversionType.entries.filter { type ->
            val catLabel = type.getCategoryLabel()
            // Comparar tanto con español como inglés
            catLabel == category ||
                    when (catLabel) {
                        "Imagen" -> category == "Image" || category == "Imagen"
                        "PDF" -> category == "PDF"
                        "Word" -> category == "Word"
                        "Excel" -> category == "Excel"
                        "PowerPoint" -> category == "PowerPoint"
                        else -> false
                    }
        }
        _uiState.update { state ->
            val isDeselecting = state.selectedCategory == category
            state.copy(
                selectedCategory = if (isDeselecting) null else category,
                filteredTypes = if (isDeselecting) emptyList() else types,
                selectedType = null,
                selectedFiles = emptyList()
            )
        }
    }

    // ── Seleccionar tipo de conversión ────────────────
    fun onTypeSelected(type: ConversionType) {
        _uiState.update { state ->
            state.copy(
                selectedType = type,
                selectedFiles = emptyList(),
                conversionResult = null,
                errorMessage = null
            )
        }
    }

    // ── Archivos seleccionados ────────────────────────
    fun onFilesSelected(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                selectedFiles = uris,
                selectedImages = uris,
                conversionResult = null,
                errorMessage = null,
                savedToDownloads = false
            )
        }
    }

    // ── Compatibilidad con ScanResultScreen ───────────
    fun onImagesSelected(uris: List<Uri>) = onFilesSelected(uris)

    fun onFileNameChange(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                selectedFiles = state.selectedFiles.filter { it != uri }
            )
        }
    }

    fun clearAll() {
        _uiState.update { ConverterUiState() }
    }

    // ── Ejecutar conversión ───────────────────────────
    fun convert() {
        val state = _uiState.value
        val type = state.selectedType ?: return
        val files = state.selectedFiles
        if (files.isEmpty()) return

        val customName = state.fileName.trim().ifBlank { generateDefaultName() }

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }

            val result = when (type) {
                ConversionType.IMAGE_TO_PDF ->
                    convertImageToPdf(imageUris = files, fileName = customName)

                ConversionType.IMAGE_TO_JPG,
                ConversionType.IMAGE_TO_PNG,
                ConversionType.IMAGE_TO_WEBP,
                ConversionType.IMAGE_TO_BMP ->
                    imageFormat(files.first(), type, customName)

                ConversionType.PDF_TO_IMAGE ->
                    pdfToImage(files.first(), customName)

                ConversionType.PDF_TO_TXT ->
                    pdfToText(files.first(), customName)

                ConversionType.PDF_TO_WORD ->
                    pdfToWord(files.first(), customName)

                ConversionType.PDF_TO_HTML ->
                    pdfToHtml(files.first(), customName)

                ConversionType.WORD_TO_PDF,
                ConversionType.WORD_TO_TXT ->
                    wordToPdf(files.first(), customName)

                ConversionType.WORD_TO_HTML ->
                    wordToHtml(files.first(), customName)

                ConversionType.EXCEL_TO_PDF,
                ConversionType.EXCEL_TO_CSV ->
                    excelToPdf(files.first(), customName)

                ConversionType.EXCEL_TO_HTML ->
                    excelToHtml(files.first(), customName)

                ConversionType.PPT_TO_PDF ->
                    wordToPdf(files.first(), customName)

                ConversionType.PPT_TO_TXT ->
                    pptToText(files.first(), customName)
            }

            Timber.d("Resultado conversión $type: $result")

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

    // ── Compatibilidad con flujo anterior ─────────────
    fun convertToPdf() {
        if (_uiState.value.selectedType == null) {
            _uiState.update { it.copy(selectedType = ConversionType.IMAGE_TO_PDF) }
        }
        convert()
    }

    // ── Guardar en Descargas ──────────────────────────
    fun saveToDownloads(context: Context) {
        val file = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            try {
                val saved = copyToDownloads(context, file)
                _uiState.update { state ->
                    if (saved) state.copy(savedToDownloads = true)
                    else state.copy(errorMessage = "No se pudo guardar en Descargas")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
            }
        }
    }

    private fun copyToDownloads(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mime = when (file.extension.lowercase()) {
                    "pdf" -> "application/pdf"
                    "txt" -> "text/plain"
                    "csv" -> "text/csv"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    else -> "application/octet-stream"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
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
            Timber.e(e, "Error guardando en Descargas")
            false
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun generateDefaultName(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(Date())
        return "DocuSmart_$timestamp"
    }
}