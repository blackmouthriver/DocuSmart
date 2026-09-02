package com.docsmart.features.converter.presentation

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.features.converter.domain.model.BatchConversionItem
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
    val selectedCategory  : String?          = null,
    val selectedType      : ConversionType?  = null,
    val filteredTypes     : List<ConversionType> = emptyList(),
    val selectedFiles     : List<Uri>        = emptyList(),
    val selectedImages    : List<Uri>        = emptyList(),
    val fileName          : String           = "",
    val isConverting      : Boolean          = false,
    val conversionResult  : ConversionResult? = null,
    val outputFile        : File?            = null,
    val savedToDownloads  : Boolean          = false,
    val errorMessage      : String?          = null,
    // ── RF-CONV-08: conversión por lotes ──────────────
    val batchResults      : List<BatchConversionItem> = emptyList(),
    val batchSavedToDownloads: Boolean       = false,
    // ── Límites diarios ───────────────────────────────
    val showLimitDialog   : Boolean          = false,
    val conversionCount   : Int              = 0,
    val conversionLimit   : Int              = DailyLimitManager.LIMIT_CONVERSIONS
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
    private val wordToText       : WordToTextUseCase,
    private val wordToHtml       : WordToHtmlUseCase,
    private val excelToPdf       : ExcelToPdfUseCase,
    private val excelToCsv       : ExcelToCsvUseCase,
    private val excelToHtml      : ExcelToHtmlUseCase,
    private val pptToPdf         : PptToPdfUseCase,
    private val pptToText        : PptToTextUseCase,
    val adManager                : AdManager,
    private val dailyLimitManager: DailyLimitManager   // ← NUEVO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    // Atajo "Convertir" desde el menú "⋮" de un archivo ya elegido (backlog
    // UX 2026-08-30, HU-UX-02). No va directo a `selectedFiles` porque
    // todavía no se sabe qué ConversionType exacto va a elegir el usuario
    // (un mismo origen, p.ej. PDF, tiene varios destinos posibles) -- queda
    // en espera hasta que `onTypeSelected` reciba un tipo de la misma
    // categoría, momento en el que se adjunta solo una vez (consumo único).
    private var pendingPreloadUri     : Uri?    = null
    private var pendingPreloadCategory: String? = null

    init { refreshLimitState() }

    fun preloadFile(uri: Uri, category: String) {
        pendingPreloadUri      = uri
        pendingPreloadCategory = category
    }

    private fun refreshLimitState() {
        _uiState.update { it.copy(
            conversionCount = dailyLimitManager.getConversionCount(),
            conversionLimit = dailyLimitManager.getConversionLimit()
        )}
    }

    fun onCategorySelected(category: String) {
        val types = ConversionType.entries.filter { type ->
            val catLabel = type.getCategoryLabel()
            catLabel == category || when (catLabel) {
                "Imagen"      -> category == "Image" || category == "Imagen"
                "PDF"         -> category == "PDF"
                "Word"        -> category == "Word"
                "Excel"       -> category == "Excel"
                "PowerPoint"  -> category == "PowerPoint"
                else          -> false
            }
        }
        _uiState.update { state ->
            val isDeselecting = state.selectedCategory == category
            state.copy(
                selectedCategory = if (isDeselecting) null else category,
                filteredTypes    = if (isDeselecting) emptyList() else types,
                selectedType     = null,
                selectedFiles    = emptyList()
            )
        }
    }

    fun onTypeSelected(type: ConversionType) {
        val preloaded = pendingPreloadUri.takeIf { pendingPreloadCategory == type.fromFormat }
        pendingPreloadUri      = null
        pendingPreloadCategory = null
        _uiState.update { state ->
            state.copy(
                selectedType    = type,
                selectedFiles   = preloaded?.let { listOf(it) } ?: emptyList(),
                conversionResult = null,
                errorMessage    = null
            )
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                selectedFiles   = uris,
                selectedImages  = uris,
                conversionResult = null,
                errorMessage    = null,
                savedToDownloads = false
            )
        }
    }

    fun onImagesSelected(uris: List<Uri>) = onFilesSelected(uris)

    fun onFileNameChange(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(selectedFiles = state.selectedFiles.filter { it != uri })
        }
    }

    fun clearAll() {
        _uiState.update { ConverterUiState() }
        refreshLimitState()
    }

    fun dismissLimitDialog() {
        _uiState.update { it.copy(showLimitDialog = false) }
    }

    // ── Ver anuncio para desbloquear conversión ───────────────────────────────
    fun watchAdForConversion(activity: Activity) {
        _uiState.update { it.copy(showLimitDialog = false) }
        adManager.showRewardedAd(
            activity   = activity,
            onRewarded = {
                dailyLimitManager.addRewardedConversion()
                refreshLimitState()
                Timber.d("ConverterViewModel: +1 conversión por Rewarded Ad")
            },
            onFailed   = {
                _uiState.update { it.copy(
                    errorMessage = "El anuncio no está disponible. Intenta de nuevo en unos segundos."
                )}
            }
        )
    }

    // ── Ejecutar conversión ───────────────────────────────────────────────────
    // RF-CONV-08: IMAGE_TO_PDF con varios archivos sigue siendo "fusionar N
    // imágenes en UN PDF" (comportamiento ya existente) -- el modo lote
    // ("N archivos → N salidas") aplica al resto de tipos cuando hay más de
    // un archivo elegido.
    fun convert(context: Context) {
        // Diagnóstico temporal 2026-09-02 (ver deployment.md §3, "Décimo
        // tercer intento"): confirmar si el click de "Convertir a WebP"
        // realmente invoca este método en el emulador de CI, o si el
        // problema es que la inyección de touch nunca llega. Quitar una
        // vez confirmada o descartada esa hipótesis.
        Timber.d("CI_HANG_DIAG: convert() invocado")
        val state = _uiState.value
        val type  = state.selectedType ?: return
        val files = state.selectedFiles
        if (files.isEmpty()) return

        if (!adManager.isPremium.value && !dailyLimitManager.canConvert()) {
            _uiState.update { it.copy(showLimitDialog = true) }
            Timber.d("ConverterViewModel: límite diario alcanzado")
            return
        }

        val customName = state.fileName.trim().ifBlank { generateDefaultName() }
        val isBatch    = type != ConversionType.IMAGE_TO_PDF && files.size > 1

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }

            if (isBatch) {
                val items = runBatchConversion(context, type, files)
                _uiState.update { it.copy(
                    isConverting    = false,
                    batchResults    = items,
                    conversionCount = dailyLimitManager.getConversionCount(),
                    conversionLimit = dailyLimitManager.getConversionLimit()
                )}
                return@launch
            }

            val result = if (type == ConversionType.IMAGE_TO_PDF)
                convertImageToPdf(imageUris = files, fileName = customName)
            else
                runConversionForUri(type, files.first(), customName)

            Timber.d("ConverterViewModel: resultado $type → $result")

            _uiState.update { state ->
                when (result) {
                    is ConversionResult.Success -> {
                        dailyLimitManager.registerConversion()
                        state.copy(
                            isConverting     = false,
                            conversionResult = result,
                            outputFile       = result.outputFile,
                            conversionCount  = dailyLimitManager.getConversionCount(),
                            conversionLimit  = dailyLimitManager.getConversionLimit()
                        )
                    }
                    is ConversionResult.Error -> state.copy(
                        isConverting = false,
                        errorMessage = result.message
                    )
                    else -> state.copy(isConverting = false)
                }
            }
        }
    }

    private suspend fun runConversionForUri(type: ConversionType, uri: Uri, fileName: String): ConversionResult =
        when (type) {
            ConversionType.IMAGE_TO_PDF -> convertImageToPdf(imageUris = listOf(uri), fileName = fileName)
            ConversionType.IMAGE_TO_JPG,
            ConversionType.IMAGE_TO_PNG,
            ConversionType.IMAGE_TO_WEBP,
            ConversionType.IMAGE_TO_BMP -> imageFormat(uri, type, fileName)
            ConversionType.PDF_TO_IMAGE -> pdfToImage(uri, fileName)
            ConversionType.PDF_TO_TXT   -> pdfToText(uri, fileName)
            ConversionType.PDF_TO_WORD  -> pdfToWord(uri, fileName)
            ConversionType.PDF_TO_HTML  -> pdfToHtml(uri, fileName)
            ConversionType.WORD_TO_PDF  -> wordToPdf(uri, fileName)
            ConversionType.WORD_TO_TXT  -> wordToText(uri, fileName)
            ConversionType.WORD_TO_HTML -> wordToHtml(uri, fileName)
            ConversionType.EXCEL_TO_PDF -> excelToPdf(uri, fileName)
            ConversionType.EXCEL_TO_CSV -> excelToCsv(uri, fileName)
            ConversionType.EXCEL_TO_HTML -> excelToHtml(uri, fileName)
            ConversionType.PPT_TO_PDF   -> pptToPdf(uri, fileName)
            ConversionType.PPT_TO_TXT   -> pptToText(uri, fileName)
        }

    // RF-CONV-08: cada archivo del lote se registra individualmente contra el
    // límite diario -- si se alcanza a mitad del lote, los archivos restantes
    // quedan marcados como Error sin ejecutar la conversión (evita que "un
    // lote" sea una forma de saltarse el límite de conversiones/día).
    private suspend fun runBatchConversion(
        context: Context,
        type   : ConversionType,
        files  : List<Uri>
    ): List<BatchConversionItem> {
        val usedNames = mutableSetOf<String>()
        return files.map { uri ->
            val originalName = resolveDisplayName(context, uri)
            val nameNoExt    = originalName.substringBeforeLast('.').ifBlank { generateDefaultName() }
            val baseName     = uniqueBaseName(nameNoExt, usedNames)

            val result = if (!adManager.isPremium.value && !dailyLimitManager.canConvert()) {
                ConversionResult.Error("Límite diario de conversiones alcanzado")
            } else {
                runConversionForUri(type, uri, baseName).also {
                    if (it is ConversionResult.Success) dailyLimitManager.registerConversion()
                }
            }
            BatchConversionItem(originalFileName = originalName, result = result)
        }
    }

    private fun uniqueBaseName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = baseName
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "$baseName ($suffix)"
            suffix++
        }
        return candidate
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String = try {
        var name: String? = null
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0) }
        name ?: uri.lastPathSegment ?: generateDefaultName()
    } catch (e: Exception) {
        Timber.w(e, "ConverterViewModel: no se pudo resolver el nombre original para el lote")
        uri.lastPathSegment ?: generateDefaultName()
    }

    // ── Guardar todos los resultados exitosos del lote en Descargas ──────────
    fun saveAllToDownloads(context: Context) {
        val successFiles = _uiState.value.batchResults
            .mapNotNull { (it.result as? ConversionResult.Success)?.outputFile }
        if (successFiles.isEmpty()) return

        viewModelScope.launch {
            val allSaved = successFiles.all { copyToDownloads(context, it) }
            _uiState.update { state ->
                if (allSaved) state.copy(batchSavedToDownloads = true)
                else state.copy(errorMessage = "No se pudieron guardar todos los archivos en Descargas")
            }
        }
    }

    fun convertToPdf(context: Context) {
        if (_uiState.value.selectedType == null) {
            _uiState.update { it.copy(selectedType = ConversionType.IMAGE_TO_PDF) }
        }
        convert(context)
    }

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
                    "pdf"         -> "application/pdf"
                    "txt"         -> "text/plain"
                    "csv"         -> "text/csv"
                    "jpg","jpeg"  -> "image/jpeg"
                    "png"         -> "image/png"
                    else          -> "application/octet-stream"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

    // Atajo "Capturar con cámara" (backlog UX 2026-08-30, HU-UX-03) --
    // reusa el mismo mecanismo de Snackbar que ya tienen los demás errores
    // de esta pantalla en vez de agregar uno nuevo.
    fun onScanError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun generateDefaultName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "DocuSmart_$timestamp"
    }
}