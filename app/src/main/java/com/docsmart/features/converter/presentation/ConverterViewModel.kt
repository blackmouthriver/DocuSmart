package com.docsmart.features.converter.presentation

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.usecase.*
import com.docsmart.core.analytics.DocuSmartAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ConverterUiState(
    val selectedType      : ConversionType?  = null,
    val selectedFiles     : List<Uri>        = emptyList(),
    val selectedImages    : List<Uri>        = emptyList(),
    val fileName          : String           = "",
    val isConverting      : Boolean          = false,
    val conversionResult  : ConversionResult? = null,
    val outputFile        : File?            = null,
    val savedToDownloads  : Boolean          = false,
    val errorMessage      : String?          = null,
    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): el
    // botón "Guardar" no tenía guard de re-entrada -- a diferencia de
    // "Convertir" (ya corregido por el hallazgo #31), un doble-toque
    // rápido lanzaba saveToDownloads()/saveAllToDownloads() dos veces en
    // paralelo, duplicando el archivo en Descargas.
    val isSaving          : Boolean          = false,
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
    private val dailyLimitManager: DailyLimitManager,  // ← NUEVO
    private val premiumManager   : PremiumManager,
    private val soundEffectPlayer: SoundEffectPlayer
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
                // Bug real encontrado 2026-09-14: hardcodeado en español --
                // reusa pdf_tools_ad_not_available (mismo mensaje que
                // Herramientas PDF para este mismo escenario).
                _uiState.update { it.copy(
                    errorMessage = activity.getString(R.string.pdf_tools_ad_not_available)
                )}
            }
        )
    }

    // ── Ejecutar conversión ───────────────────────────────────────────────────
    // RF-CONV-08: IMAGE_TO_PDF con varios archivos sigue siendo "fusionar N
    // imágenes en UN PDF" (comportamiento ya existente) -- el modo lote
    // ("N archivos → N salidas") aplica al resto de tipos cuando hay más de
    // un archivo elegido.
    fun convert(context: Context, highResolutionPdf: Boolean = false) {
        val state = _uiState.value
        val type  = state.selectedType
        val files = state.selectedFiles
        // Hallazgo real de la revisión general 2026-09-16: un doble-toque
        // llamaba a convert() dos veces antes de que la primera corrutina
        // alcanzara a marcar isConverting = true (esa actualización pasaba
        // recién adentro de viewModelScope.launch, de forma asíncrona), así
        // que ambas pasaban canConvert() antes de que cualquiera registrara
        // el conteo -- se superaba el límite diario en 1. El guard debe
        // fijarse acá mismo, de forma sincrónica, antes de cualquier punto
        // de suspensión: dos taps se procesan uno tras otro en el hilo
        // principal, así que para cuando llega el segundo, este chequeo ya
        // ve el estado que dejó el primero.
        if (type == null || files.isEmpty() || state.isConverting) return

        if (!premiumManager.canPerform { dailyLimitManager.canConvert() }) {
            _uiState.update { it.copy(showLimitDialog = true) }
            Timber.d("ConverterViewModel: límite diario alcanzado")
            return
        }

        // batchResults se limpia acá (no solo al terminar) para que un
        // lote nuevo no arranque mezclado con los restos incrementales de
        // un intento anterior cancelado a mitad de camino (ver
        // runBatchConversion()).
        _uiState.update { it.copy(isConverting = true, errorMessage = null, batchResults = emptyList()) }

        // "Alta resolución" (backlog UX #33) es Premium -- se revalida acá,
        // no solo en la UI, para que el estado de la pantalla nunca pueda
        // saltarse el gate (defensa en profundidad, mismo criterio que
        // premiumManager ya se revalida en el límite diario arriba).
        val useHighRes = highResolutionPdf && premiumManager.isPremium.value

        // Hallazgo real de la revisión general 2026-09-16 (path traversal):
        // saneado en este único punto para los 14 use cases del Conversor,
        // ver sanitizeOutputFileName().
        val customName = com.docsmart.core.util.sanitizeOutputFileName(state.fileName).ifBlank { generateDefaultName() }
        val isBatch    = type != ConversionType.IMAGE_TO_PDF && files.size > 1

        DocuSmartAnalytics.logConversion(type.name)

        viewModelScope.launch {
            // Hallazgo real de la revisión general 2026-09-16 (#42): esta
            // corrutina no tenía ninguna protección propia -- si algo
            // escapaba sin atrapar de runBatchConversion()/
            // runConversionForUri() (un OutOfMemoryError, o cualquier otra
            // excepción no prevista por el use case individual), isConverting
            // quedaba en true para siempre, mismo criterio que ya se corrigió
            // en PdfToolsViewModel.runTool() (hallazgo #23).
            try {
                performConversion(context, type, files, customName, useHighRes, isBatch)
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "ConverterViewModel: sin memoria convirtiendo $type")
                val message = context.getString(R.string.converter_error_unknown)
                _uiState.update { it.copy(isConverting = false, errorMessage = message) }
            } catch (e: CancellationException) {
                // Hallazgo 1 (auditoría del Convertidor): CancellationException
                // hereda de Exception -- sin este catch específico antes del
                // genérico de abajo, salir de la pantalla a mitad de una
                // conversión se atrapaba como un error genérico en vez de
                // propagarse como cancelación real, y "Cancelar" no cancelaba
                // nada de verdad. No se toca `isConverting` acá: la corrutina
                // cancelada no debe seguir actualizando el estado de una
                // pantalla de la que el usuario ya se fue.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "ConverterViewModel: error inesperado convirtiendo $type")
                val message = context.getString(R.string.general_error_format, e.message ?: "")
                _uiState.update { it.copy(isConverting = false, errorMessage = message) }
            }
        }
    }

    // Extraído de convert() (detekt: CyclomaticComplexMethod, disparado al
    // agregar el catch de CancellationException del hallazgo 1 de la
    // auditoría del Convertidor) -- agrupa la ejecución real de la
    // conversión (lote o archivo único) y la actualización de estado
    // resultante, separado del try/catch que la envuelve en convert().
    private suspend fun performConversion(
        context: Context,
        type: ConversionType,
        files: List<Uri>,
        customName: String,
        useHighRes: Boolean,
        isBatch: Boolean
    ) {
        if (isBatch) {
            val items = runBatchConversion(context, type, files)
            if (items.any { it.result is ConversionResult.Success }) soundEffectPlayer.playConvert()
            _uiState.update { it.copy(
                isConverting    = false,
                batchResults    = items,
                conversionCount = dailyLimitManager.getConversionCount(),
                conversionLimit = dailyLimitManager.getConversionLimit()
            )}
            return
        }

        val result = if (type == ConversionType.IMAGE_TO_PDF)
            convertImageToPdf(imageUris = files, fileName = customName, highResolution = useHighRes)
        else
            runConversionForUri(type, files.first(), customName)

        Timber.d("ConverterViewModel: resultado $type → $result")
        logConversionOutcome(type, result)
        if (result is ConversionResult.Success) soundEffectPlayer.playConvert()

        _uiState.update { state -> applySingleConversionResult(state, result) }
    }

    // Extraído de convert() (detekt: CyclomaticComplexMethod) -- registrar
    // el resultado en analítica es una rama de lógica independiente de
    // cómo se actualiza el estado de la pantalla.
    private fun logConversionOutcome(type: ConversionType, result: ConversionResult) {
        when (result) {
            is ConversionResult.Success ->
                DocuSmartAnalytics.logConversionSuccess(type.name, result.fileSizeKb)
            is ConversionResult.Error ->
                DocuSmartAnalytics.logConversionError(type.name, result.message)
            else -> Unit
        }
    }

    // Extraído de convert() (detekt: CyclomaticComplexMethod).
    private fun applySingleConversionResult(
        state: ConverterUiState,
        result: ConversionResult
    ): ConverterUiState = when (result) {
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
        is ConversionResult.Error -> state.copy(isConverting = false, errorMessage = result.message)
        else -> state.copy(isConverting = false)
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
            // Hallazgo real de la auditoría general 2026-09-17 (sexta
            // ronda, Media-Alta): antes, `batchResults` solo se escribía
            // en `_uiState` cuando la función completa retornaba -- si la
            // corrutina se cancelaba a mitad del lote (ej. el usuario
            // navega hacia atrás), los archivos de los ítems que ya
            // habían terminado (con su cupo diario ya consumido vía
            // `registerConversion()` más abajo) quedaban huérfanos en
            // disco, sin ninguna referencia visible en la UI. Se acumula
            // acá mismo, ítem por ítem, para que lo ya convertido quede
            // siempre visible/recuperable sin importar cómo termine el
            // resto del lote.
            // Hallazgo real de la revisión general 2026-09-16 (#35): el
            // saneo de path traversal en ConverterViewModel solo cubría el
            // nombre TIPEADO por el usuario en modo archivo único --
            // originalName viene de un ContentProvider ajeno (DISPLAY_NAME)
            // o de uri.lastPathSegment, ninguno de los dos confiable, y
            // llegaba sin sanear a File(outputDir, "..._$baseName_...")
            // en cada use case en modo lote.
            val nameNoExt = com.docsmart.core.util.sanitizeOutputFileName(
                originalName.substringBeforeLast('.')
            ).ifBlank { generateDefaultName() }
            val baseName     = uniqueBaseName(nameNoExt, usedNames)

            // Bug real encontrado 2026-09-14: hardcodeado en español,
            // saltándose el sistema de 12 idiomas.
            val result = if (!premiumManager.canPerform { dailyLimitManager.canConvert() }) {
                ConversionResult.Error(context.getString(R.string.converter_daily_limit_reached_error))
            } else {
                runConversionForUri(type, uri, baseName).also {
                    if (it is ConversionResult.Success) dailyLimitManager.registerConversion()
                }
            }
            when (result) {
                is ConversionResult.Success ->
                    DocuSmartAnalytics.logConversionSuccess(type.name, result.fileSizeKb)
                is ConversionResult.Error ->
                    DocuSmartAnalytics.logConversionError(type.name, result.message)
                else -> Unit
            }
            BatchConversionItem(originalFileName = originalName, result = result).also { item ->
                _uiState.update { it.copy(batchResults = it.batchResults + item) }
            }
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
        if (successFiles.isEmpty() || _uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Hallazgo real de la auditoría general 2026-09-17 (sexta
            // ronda, Media -- C2): a diferencia de su par saveToDownloads()
            // (que sí tiene try/catch), este guardado por lote no atrapaba
            // absolutamente nada -- un OutOfMemoryError copiando un
            // archivo grande a MediaStore escapaba sin atrapar y
            // crasheaba la app, justo el escenario con más presión de
            // memoria de todo el Convertidor (varios archivos grandes en
            // un solo lote).
            try {
                // Hallazgo real de la revisión general 2026-09-16 (cuarta
                // pasada): Iterable.all{} corta en cortocircuito en el
                // primer `false` -- si el primer archivo del lote fallaba
                // al guardarse, el resto (que hubieran funcionado) ni se
                // intentaba. .map{} sí procesa todos antes de evaluar el
                // resultado.
                val allSaved = successFiles.map {
                    DownloadsSaver.saveFile(context, it, DownloadsSaver.mimeTypeForExtension(it.extension))
                }.all { it }
                // Bug real encontrado 2026-09-14: hardcodeado en español,
                // saltándose el sistema de 12 idiomas.
                _uiState.update { state ->
                    if (allSaved) {
                        state.copy(batchSavedToDownloads = true, isSaving = false)
                    } else {
                        state.copy(
                            errorMessage = context.getString(R.string.converter_batch_save_error),
                            isSaving = false
                        )
                    }
                }
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "ConverterViewModel: sin memoria guardando el lote en Descargas")
                _uiState.update {
                    it.copy(errorMessage = context.getString(R.string.converter_error_unknown), isSaving = false)
                }
            } catch (e: CancellationException) {
                // Hallazgo 1 (auditoría del Convertidor): ver el mismo
                // hallazgo en convert() más arriba.
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = context.getString(R.string.general_error_format, e.message ?: ""),
                        isSaving = false
                    )
                }
            }
        }
    }

    fun convertToPdf(context: Context, highResolution: Boolean = false) {
        if (_uiState.value.selectedType == null) {
            _uiState.update { it.copy(selectedType = ConversionType.IMAGE_TO_PDF) }
        }
        convert(context, highResolutionPdf = highResolution)
    }

    // Atajo del Escáner (backlog UX #33) para exportar las páginas
    // escaneadas como imágenes en vez de PDF -- reutiliza el mismo
    // `convert()` que ya arma lotes N archivos → N salidas para estos tipos
    // (ver `isBatch` arriba), así una página exporta un solo archivo y
    // varias exportan una por una con `runBatchConversion`.
    fun convertToImageFormat(context: Context, type: ConversionType) {
        _uiState.update { it.copy(selectedType = type) }
        convert(context)
    }

    // Atajo del Escáner (backlog UX #35) para el botón único "Generar" de
    // ScanResultScreen: según el formato elegido dispara PDF (con la opción
    // "Alta resolución") o una imagen suelta (JPG/WebP), fijando antes el
    // nombre elegido por el usuario en el estado (`convert()` lee
    // `state.fileName`).
    fun generateFromScan(
        context: Context,
        imageType: ConversionType?,
        highResolution: Boolean,
        fileName: String
    ) {
        onFileNameChange(fileName)
        if (imageType == null) {
            convertToPdf(context, highResolution)
        } else {
            convertToImageFormat(context, imageType)
        }
    }

    fun saveToDownloads(context: Context) {
        val file = _uiState.value.outputFile ?: return
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // sin este guard, un doble-toque rápido en "Guardar" lanzaba esta
        // función dos veces en paralelo -- mismo patrón ya corregido para
        // "Convertir" (hallazgo #31).
        if (_uiState.value.isSaving) return
        // Hallazgo real de la revisión general 2026-09-16 (#45): PDF→Imagen
        // con varias páginas genera un extraFiles con el resto de las
        // páginas (ver ConversionResult.Success) que nunca se guardaba --
        // el usuario solo podía recuperar la primera. Se guardan todas.
        val extraFiles = (_uiState.value.conversionResult as? ConversionResult.Success)?.extraFiles.orEmpty()
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                // Ver el comentario equivalente en saveAllToDownloads():
                // .map{} en vez de .all{} para que un fallo en el primer
                // archivo no impida intentar el resto.
                val allSaved = (listOf(file) + extraFiles).map {
                    DownloadsSaver.saveFile(context, it, DownloadsSaver.mimeTypeForExtension(it.extension))
                }.all { it }
                // Bug real encontrado 2026-09-14: ambos mensajes estaban
                // hardcodeados en español, saltándose el sistema de 12
                // idiomas -- el primero reusa pdf_tools_save_error (mismo
                // mensaje que Herramientas PDF para este mismo escenario).
                _uiState.update { state ->
                    if (allSaved) state.copy(savedToDownloads = true, isSaving = false)
                    else state.copy(errorMessage = context.getString(R.string.pdf_tools_save_error), isSaving = false)
                }
            } catch (e: OutOfMemoryError) {
                // Hallazgo real de la auditoría general 2026-09-17 (sexta
                // ronda, Media -- C2): mismo hueco que su par
                // saveAllToDownloads(), ver el comentario ahí.
                Timber.e(e, "ConverterViewModel: sin memoria guardando en Descargas")
                _uiState.update {
                    it.copy(errorMessage = context.getString(R.string.converter_error_unknown), isSaving = false)
                }
            } catch (e: CancellationException) {
                // Hallazgo 1 (auditoría del Convertidor): ver el mismo
                // hallazgo en convert() más arriba.
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = context.getString(R.string.general_error_format, e.message ?: ""),
                        isSaving = false
                    )
                }
            }
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