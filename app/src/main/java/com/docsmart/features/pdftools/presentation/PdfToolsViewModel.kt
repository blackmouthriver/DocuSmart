package com.docsmart.features.pdftools.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.app.Activity
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.docsmart.features.pdftools.domain.usecase.ComparePdfMessages
import com.docsmart.features.pdftools.domain.usecase.ComparePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.CompressPdfMessages
import com.docsmart.features.pdftools.domain.usecase.CompressPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.CropPdfMessages
import com.docsmart.features.pdftools.domain.usecase.CropPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.DetectFormFieldsUseCase
import com.docsmart.features.pdftools.domain.usecase.EditTextPdfMessages
import com.docsmart.features.pdftools.domain.usecase.EditTextPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.FillFormMessages
import com.docsmart.features.pdftools.domain.usecase.FillFormUseCase
import com.docsmart.features.pdftools.domain.usecase.FormFieldInfo
import com.docsmart.features.pdftools.domain.usecase.SignPdfMessages
import com.docsmart.features.pdftools.domain.usecase.SignPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.MergePdfMessages
import com.docsmart.features.pdftools.domain.usecase.MergePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.NumberPagesMessages
import com.docsmart.features.pdftools.domain.usecase.NumberPagesUseCase
import com.docsmart.features.pdftools.domain.usecase.OcrPdfMessages
import com.docsmart.features.pdftools.domain.usecase.OcrPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.PageNumberFormat
import com.docsmart.features.pdftools.domain.usecase.RedactPdfMessages
import com.docsmart.features.pdftools.domain.usecase.RedactPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.RedactionRect
import com.docsmart.features.pdftools.domain.usecase.ReorderPagesMessages
import com.docsmart.features.pdftools.domain.usecase.ReorderPagesUseCase
import com.docsmart.features.pdftools.domain.usecase.RotatePdfMessages
import com.docsmart.features.pdftools.domain.usecase.RotatePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.SplitPdfMessages
import com.docsmart.features.pdftools.domain.usecase.SplitPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.WatermarkMessages
import com.docsmart.features.pdftools.domain.usecase.WatermarkPdfUseCase
import com.docsmart.core.analytics.DocuSmartAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

enum class PdfTool {
    NONE, MERGE, SPLIT, COMPRESS, ROTATE, NUMBER_PAGES, WATERMARK, REORDER_PAGES,
    COMPARE, REDACT, CROP, EDIT_TEXT, SIGN, FILL_FORM, OCR
}

data class PdfToolMessages(
    val merge        : MergePdfMessages,
    val split        : SplitPdfMessages,
    val compress     : CompressPdfMessages,
    val rotate       : RotatePdfMessages,
    val numberPages  : NumberPagesMessages,
    val watermark    : WatermarkMessages,
    val reorderPages : ReorderPagesMessages,
    val compare      : ComparePdfMessages,
    val redact       : RedactPdfMessages,
    val crop         : CropPdfMessages,
    val editText     : EditTextPdfMessages,
    val sign         : SignPdfMessages,
    val fillForm     : FillFormMessages,
    val ocr          : OcrPdfMessages
)

data class PdfToolsUiState(
    val selectedTool: PdfTool = PdfTool.NONE,
    val selectedPdfs: List<Uri> = emptyList(),
    val isProcessing: Boolean = false,
    val result: PdfToolResult? = null,
    val errorMessage: String? = null,
    val savedToDownloads: Boolean = false,
    val outputFileName: String = "",
    val splitFromPage: Int = 1,
    val splitToPage: Int = 2,
    val compressionQuality: Int = 60,
    val rotationDegrees: Int = 90,
    val pageNumberFormat: PageNumberFormat = PageNumberFormat.PAGE_OF_TOTAL,
    val watermarkText: String = "",
    val pageOrder: List<Int> = emptyList(),
    val comparePdfA: Uri? = null,
    val comparePdfB: Uri? = null,
    val redactionRects: List<RedactionRect> = emptyList(),
    val redactionCurrentPage: Int = 1,
    val redactionTotalPages: Int = 1,
    val cropMarginPercent: Int = 10,
    val editSearchText: String = "",
    val editReplaceText: String = "",
    val signaturePageNumber: Int = 1,
    val signatureTotalPages: Int = 1,
    val signatureImageBytes: ByteArray? = null,
    val formFields: List<FormFieldInfo> = emptyList(),
    val formFieldValues: Map<String, String> = emptyMap(),
    val formFieldsDetected: Boolean = false,
    // ── Límite diario ──────────────────────────────────
    val showLimitDialog: Boolean = false,
    val toolUseCount: Int = 0,
    val toolUseLimit: Int = DailyLimitManager.LIMIT_PDF_TOOLS
)

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    private val mergePdf: MergePdfUseCase,
    private val splitPdf: SplitPdfUseCase,
    private val compressPdf: CompressPdfUseCase,
    private val rotatePdf: RotatePdfUseCase,
    private val numberPagesPdf: NumberPagesUseCase,
    private val watermarkPdf: WatermarkPdfUseCase,
    private val reorderPagesPdf: ReorderPagesUseCase,
    private val comparePdf: ComparePdfUseCase,
    private val redactPdf: RedactPdfUseCase,
    private val cropPdf: CropPdfUseCase,
    private val editTextPdf: EditTextPdfUseCase,
    private val signPdf: SignPdfUseCase,
    private val detectFormFields: DetectFormFieldsUseCase,
    private val fillForm: FillFormUseCase,
    private val ocrPdf: OcrPdfUseCase,
    private val dailyLimitManager: DailyLimitManager,
    val adManager: AdManager
) : ViewModel() {

    companion object {
        private const val TAG = "PdfToolsViewModel"
    }

    private val _uiState = MutableStateFlow(PdfToolsUiState())
    val uiState: StateFlow<PdfToolsUiState> = _uiState.asStateFlow()

    fun selectTool(tool: PdfTool) {
        if (tool != PdfTool.NONE) DocuSmartAnalytics.logPdfTool(tool.name)
        _uiState.update {
            PdfToolsUiState(
                selectedTool = tool,
                toolUseCount = if (tool == PdfTool.NONE) 0 else dailyLimitManager.getPdfToolCount(tool.name),
                toolUseLimit = dailyLimitManager.getPdfToolLimit()
            )
        }
    }

    // ── Ver anuncio para desbloquear un uso extra de la herramienta ───────────
    fun watchAdForTool(activity: Activity, adNotAvailableMessage: String) {
        _uiState.update { it.copy(showLimitDialog = false) }
        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                dailyLimitManager.addRewardedPdfTool()
                _uiState.update { it.copy(toolUseLimit = dailyLimitManager.getPdfToolLimit()) }
                Timber.d("$TAG: +1 uso de herramienta por Rewarded Ad")
            },
            onFailed = {
                _uiState.update { it.copy(errorMessage = adNotAvailableMessage) }
            }
        )
    }

    fun dismissLimitDialog() {
        _uiState.update { it.copy(showLimitDialog = false) }
    }

    fun onPdfsSelected(uris: List<Uri>) {
        _uiState.update { state ->
            state.copy(
                selectedPdfs = uris,
                result = null,
                errorMessage = null,
                savedToDownloads = false,
                outputFileName = "",
                pageOrder = emptyList(),
                redactionRects = emptyList(),
                redactionCurrentPage = 1,
                redactionTotalPages = 1,
                signaturePageNumber = 1,
                signatureTotalPages = 1,
                signatureImageBytes = null,
                formFields = emptyList(),
                formFieldValues = emptyMap(),
                formFieldsDetected = false
            )
        }
    }

    fun addPdfsToMerge(uris: List<Uri>) {
        _uiState.update { state ->
            val current = state.selectedPdfs.toMutableList()
            uris.forEach { uri ->
                if (!current.contains(uri)) current.add(uri)
            }
            state.copy(
                selectedPdfs = current,
                result = null,
                errorMessage = null
            )
        }
    }

    fun removePdf(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                selectedPdfs = state.selectedPdfs.filter { it != uri }
            )
        }
    }

    fun onOutputFileNameChange(name: String) {
        _uiState.update { it.copy(outputFileName = name) }
    }

    fun onSplitFromPageChange(page: Int) {
        _uiState.update { it.copy(splitFromPage = page.coerceAtLeast(1)) }
    }

    fun onSplitToPageChange(page: Int) {
        _uiState.update { it.copy(splitToPage = page.coerceAtLeast(1)) }
    }

    fun onCompressionQualityChange(quality: Int) {
        _uiState.update { it.copy(compressionQuality = quality.coerceIn(20, 100)) }
    }

    fun onRotationDegreesChange(degrees: Int) {
        _uiState.update { it.copy(rotationDegrees = degrees) }
    }

    fun onPageNumberFormatChange(format: PageNumberFormat) {
        _uiState.update { it.copy(pageNumberFormat = format) }
    }

    fun onWatermarkTextChange(text: String) {
        _uiState.update { it.copy(watermarkText = text) }
    }

    fun onPagesLoaded(totalPages: Int) {
        _uiState.update { it.copy(pageOrder = (1..totalPages).toList()) }
    }

    fun onReorderPage(from: Int, to: Int) {
        _uiState.update { state ->
            val order = state.pageOrder
            if (from !in order.indices || to !in order.indices) return@update state
            val reordered = order.toMutableList()
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            state.copy(pageOrder = reordered)
        }
    }

    fun onRemovePage(pageNumber: Int) {
        _uiState.update { state ->
            if (state.pageOrder.size <= 1) state
            else state.copy(pageOrder = state.pageOrder.filter { it != pageNumber })
        }
    }

    fun onComparePdfASelected(uri: Uri) {
        _uiState.update {
            it.copy(
                comparePdfA = uri, result = null, errorMessage = null,
                savedToDownloads = false, outputFileName = ""
            )
        }
    }

    fun onComparePdfBSelected(uri: Uri) {
        _uiState.update {
            it.copy(
                comparePdfB = uri, result = null, errorMessage = null,
                savedToDownloads = false, outputFileName = ""
            )
        }
    }

    fun onRedactionTotalPagesLoaded(total: Int) {
        _uiState.update { it.copy(redactionTotalPages = total.coerceAtLeast(1)) }
    }

    fun onRedactionPageChange(page: Int) {
        _uiState.update { it.copy(redactionCurrentPage = page.coerceIn(1, it.redactionTotalPages)) }
    }

    fun onAddRedactionRect(rect: RedactionRect) {
        _uiState.update { it.copy(redactionRects = it.redactionRects + rect) }
    }

    fun onUndoLastRedactionRect() {
        _uiState.update { it.copy(redactionRects = it.redactionRects.dropLast(1)) }
    }

    fun onClearRedactionRects() {
        _uiState.update { it.copy(redactionRects = emptyList()) }
    }

    fun onCropMarginChange(percent: Int) {
        _uiState.update { it.copy(cropMarginPercent = percent.coerceIn(0, 40)) }
    }

    fun onEditSearchTextChange(text: String) {
        _uiState.update { it.copy(editSearchText = text) }
    }

    fun onEditReplaceTextChange(text: String) {
        _uiState.update { it.copy(editReplaceText = text) }
    }

    fun onSignatureTotalPagesLoaded(total: Int) {
        _uiState.update { it.copy(signatureTotalPages = total.coerceAtLeast(1)) }
    }

    fun onSignaturePageChange(page: Int) {
        _uiState.update { it.copy(signaturePageNumber = page.coerceIn(1, it.signatureTotalPages)) }
    }

    fun onSignatureCaptured(bytes: ByteArray) {
        _uiState.update { it.copy(signatureImageBytes = bytes) }
    }

    fun onClearSignature() {
        _uiState.update { it.copy(signatureImageBytes = null) }
    }

    fun onDetectFormFields(uri: Uri) {
        _uiState.update { it.copy(formFieldsDetected = false) }
        viewModelScope.launch {
            val fields = detectFormFields(uri)
            _uiState.update {
                it.copy(
                    formFields = fields,
                    formFieldValues = fields.associate { field -> field.name to field.currentValue },
                    formFieldsDetected = true
                )
            }
        }
    }

    fun onFormFieldValueChange(name: String, value: String) {
        _uiState.update { it.copy(formFieldValues = it.formFieldValues + (name to value)) }
    }

    fun execute(messages: PdfToolMessages) {
        val state = _uiState.value
        val hasSelection = if (state.selectedTool == PdfTool.COMPARE) {
            state.comparePdfA != null && state.comparePdfB != null
        } else {
            state.selectedPdfs.isNotEmpty()
        }
        if (!hasSelection || state.selectedTool == PdfTool.NONE) return

        if (!adManager.isPremium.value && !dailyLimitManager.canUsePdfTool(state.selectedTool.name)) {
            _uiState.update { it.copy(showLimitDialog = true) }
            Timber.d("$TAG: límite diario alcanzado para ${state.selectedTool}")
            return
        }

        val customName = state.outputFileName.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    result = null,
                    errorMessage = null
                )
            }

            val result = runTool(state, customName, messages) ?: return@launch

            Timber.d("Resultado: $result")

            if (result is PdfToolResult.Success) {
                dailyLimitManager.registerPdfTool(state.selectedTool.name)
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    result = result,
                    toolUseCount = dailyLimitManager.getPdfToolCount(state.selectedTool.name),
                    errorMessage = if (result is PdfToolResult.Error)
                        result.message
                    else null
                )
            }
        }
    }

    // Extraído de execute() para mantener su complejidad ciclomática bajo el
    // umbral de detekt (15) -- este dispatcher crece un caso por cada
    // herramienta nueva del backlog (RF-PDF-06/07/08...) y ya lo había
    // superado con la séptima. Con la décimoprimera (RF-PDF-10) volvió a
    // superarlo, así que se dividió en dos sub-dispatchers por categoría
    // (herramientas de un solo archivo con parámetros simples vs. las que
    // necesitan lógica propia) en vez de seguir baselineando el hallazgo.
    private suspend fun runTool(
        state: PdfToolsUiState,
        customName: String?,
        messages: PdfToolMessages
    ): PdfToolResult? = when (state.selectedTool) {
        PdfTool.MERGE, PdfTool.SPLIT, PdfTool.COMPRESS, PdfTool.ROTATE,
        PdfTool.NUMBER_PAGES, PdfTool.WATERMARK, PdfTool.REORDER_PAGES ->
            runBasicTool(state, customName, messages)
        PdfTool.COMPARE, PdfTool.REDACT, PdfTool.CROP, PdfTool.EDIT_TEXT, PdfTool.SIGN,
        PdfTool.FILL_FORM, PdfTool.OCR ->
            runAdvancedTool(state, customName, messages)
        PdfTool.NONE -> null
    }

    private suspend fun runBasicTool(
        state: PdfToolsUiState,
        customName: String?,
        messages: PdfToolMessages
    ): PdfToolResult? = when (state.selectedTool) {
        PdfTool.MERGE -> mergePdf(
            pdfUris = state.selectedPdfs,
            outputFileName = customName,
            messages = messages.merge
        )
        PdfTool.SPLIT -> splitPdf(
            pdfUri = state.selectedPdfs.first(),
            fromPage = state.splitFromPage,
            toPage = state.splitToPage,
            outputFileName = customName,
            messages = messages.split
        )
        PdfTool.COMPRESS -> compressPdf(
            pdfUri = state.selectedPdfs.first(),
            quality = state.compressionQuality,
            outputFileName = customName,
            messages = messages.compress
        )
        PdfTool.ROTATE -> rotatePdf(
            pdfUri = state.selectedPdfs.first(),
            degrees = state.rotationDegrees,
            outputFileName = customName,
            messages = messages.rotate
        )
        PdfTool.NUMBER_PAGES -> numberPagesPdf(
            pdfUri = state.selectedPdfs.first(),
            format = state.pageNumberFormat,
            outputFileName = customName,
            messages = messages.numberPages
        )
        PdfTool.WATERMARK -> watermarkPdf(
            pdfUri = state.selectedPdfs.first(),
            watermarkText = state.watermarkText,
            outputFileName = customName,
            messages = messages.watermark
        )
        PdfTool.REORDER_PAGES -> reorderPagesPdf(
            pdfUri = state.selectedPdfs.first(),
            pageOrder = state.pageOrder,
            outputFileName = customName,
            messages = messages.reorderPages
        )
        else -> null
    }

    private suspend fun runAdvancedTool(
        state: PdfToolsUiState,
        customName: String?,
        messages: PdfToolMessages
    ): PdfToolResult? = when (state.selectedTool) {
        PdfTool.COMPARE -> {
            val pdfA = state.comparePdfA
            val pdfB = state.comparePdfB
            if (pdfA != null && pdfB != null) {
                comparePdf(pdfUriA = pdfA, pdfUriB = pdfB, outputFileName = customName, messages = messages.compare)
            } else {
                null
            }
        }
        PdfTool.REDACT -> redactPdf(
            pdfUri = state.selectedPdfs.first(),
            rects = state.redactionRects,
            outputFileName = customName,
            messages = messages.redact
        )
        PdfTool.CROP -> cropPdf(
            pdfUri = state.selectedPdfs.first(),
            marginPercent = state.cropMarginPercent,
            outputFileName = customName,
            messages = messages.crop
        )
        PdfTool.EDIT_TEXT -> editTextPdf(
            pdfUri = state.selectedPdfs.first(),
            searchText = state.editSearchText,
            replaceText = state.editReplaceText,
            outputFileName = customName,
            messages = messages.editText
        )
        PdfTool.SIGN -> {
            val signature = state.signatureImageBytes
            if (signature != null) {
                signPdf(
                    pdfUri = state.selectedPdfs.first(),
                    signatureImageBytes = signature,
                    pageNumber = state.signaturePageNumber,
                    outputFileName = customName,
                    messages = messages.sign
                )
            } else {
                null
            }
        }
        PdfTool.FILL_FORM -> fillForm(
            pdfUri = state.selectedPdfs.first(),
            values = state.formFieldValues,
            outputFileName = customName,
            messages = messages.fillForm
        )
        PdfTool.OCR -> ocrPdf(
            pdfUri = state.selectedPdfs.first(),
            outputFileName = customName,
            messages = messages.ocr
        )
        else -> null
    }

    fun shareResult(context: Context, chooserTitle: String, errorMessage: String) {
        val result = _uiState.value.result as? PdfToolResult.Success ?: return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                result.outputFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, chooserTitle)
            )
        } catch (e: Exception) {
            Timber.e("Error compartiendo: ${e.message}")
            _uiState.update {
                it.copy(errorMessage = errorMessage)
            }
        }
    }

    fun saveToDownloads(context: Context, errorMessage: String) {
        val result = _uiState.value.result as? PdfToolResult.Success ?: return
        viewModelScope.launch {
            val saved = copyToDownloads(context, result.outputFile)
            _uiState.update { state ->
                if (saved) state.copy(savedToDownloads = true)
                else state.copy(errorMessage = errorMessage)
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
            Timber.e("Error guardando en Descargas: ${e.message}")
            false
        }
    }

    fun reset() {
        _uiState.update { PdfToolsUiState() }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}