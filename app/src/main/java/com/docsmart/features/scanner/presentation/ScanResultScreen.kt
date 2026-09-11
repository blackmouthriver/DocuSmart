package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DailyLimitDialog
import com.docsmart.core.ui.components.DocuSmartDocumentItem
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.RenameDocumentDialog
import com.docsmart.core.ui.components.buttons.DocuSmartPrimaryButton
import com.docsmart.core.ui.components.buttons.DocuSmartSecondaryButton
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentFilterChipColors
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.presentation.ConverterUiState
import com.docsmart.features.converter.presentation.ConverterViewModel
import com.docsmart.features.converter.presentation.components.BatchConversionSuccess
import com.docsmart.features.scanner.domain.buildColorMatrix
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MIME_PDF = "application/pdf"

// Backlog UX #33 (pedido explícito del usuario 2026-09-06): antes el
// Escáner solo podía terminar en PDF -- ahora también puede exportar las
// páginas como imágenes. "Alta resolución" es exclusivo de PDF (JPG/WebP
// ya exportan siempre a la resolución nativa de la cámara, sin reducir
// nada, así que no hay nada que mejorar ahí).
private enum class ScanExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", MIME_PDF),
    JPG("JPG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp")
}

private fun ScanExportFormat.toImageConversionType(): ConversionType? = when (this) {
    ScanExportFormat.PDF  -> null
    ScanExportFormat.JPG  -> ConversionType.IMAGE_TO_JPG
    ScanExportFormat.WEBP -> ConversionType.IMAGE_TO_WEBP
}

private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
    "pdf"         -> MIME_PDF
    "jpg", "jpeg" -> "image/jpeg"
    "webp"        -> "image/webp"
    "png"         -> "image/png"
    else          -> "application/octet-stream"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    scannedUris: List<Uri>,
    isPdf: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPremiumClick: () -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    onConvertDocument: (DocumentUiModel) -> Unit = {},
    onCreateQrFromDocument: (DocumentUiModel) -> Unit = {},
    converterViewModel: ConverterViewModel = hiltViewModel(),
    editorViewModel: ScanImageEditorViewModel = hiltViewModel(),
    scanSessionViewModel: ScanSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by converterViewModel.uiState.collectAsState()
    val isPremium by converterViewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val isRewardedReady by converterViewModel.adManager.isRewardedReady.collectAsStateWithLifecycle()
    val scannedSessionFiles by scanSessionViewModel.scannedFiles.collectAsStateWithLifecycle()

    var fileName by remember { mutableStateOf("") }
    var savedToDownloads by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var isPreparingShare by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(ScanExportFormat.PDF) }
    var highResEnabled by remember { mutableStateOf(false) }
    // Backlog UX #35: tras guardar/compartir, cierra la vista de "un solo
    // documento" y muestra la lista de la sesión -- ver ScanSessionManager.
    var sessionFinalized by remember { mutableStateOf(false) }

    // RF-SCAN-06/07: lista editable -- empieza igual al resultado del
    // escáner, y cada página editada reemplaza su URI original por la del
    // archivo ya ajustado (brillo/contraste/escala), sin tocar las demás.
    var editableUris by remember(scannedUris) { mutableStateOf(scannedUris) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val defaultNameTemplate = stringResource(R.string.scan_result_default_name_prefix)
    val shareChooserTitle   = stringResource(R.string.scan_result_share_format, selectedFormat.label)

    // ── Inicializar según tipo de resultado ───────────
    LaunchedEffect(editableUris, isPdf) {
        if (!isPdf) {
            converterViewModel.onImagesSelected(editableUris)
        }
    }

    // Diálogos y sincronizaciones de un solo golpe (edición de página,
    // referencia al PDF generado, unificación del lote a la sesión, y los
    // dos límites diarios) -- extraídos aparte para no volver a superar el
    // límite de líneas de detekt en este Composable.
    val conversionResult = uiState.conversionResult
    ScanResultSideEffects(
        editingIndex = editingIndex,
        editableUris = editableUris,
        uiState = uiState,
        isRewardedReady = isRewardedReady,
        activity = activity,
        viewModels = ScanResultViewModels(editorViewModel, converterViewModel, scanSessionViewModel),
        callbacks = ScanResultEffectCallbacks(
            onEditingIndexChange = { editingIndex = it },
            onEditableUrisChange = { editableUris = it },
            onSavedFileChange = { savedFile = it },
            onSessionFinalized = { sessionFinalized = true }
        )
    )

    // Bug real reportado por el usuario 2026-08-30 (backlog UX §9): esta
    // pantalla usaba un Scaffold+TopAppBar con su propio título y flecha
    // de volver, duplicando el título que ya muestra el banner azul justo
    // debajo -- se reemplaza por el mismo patrón de "Volver" integrado en
    // el banner que ya usan el resto de sub-pantallas.
    // Fondo animado global (backlog UX 2026-09-06): transparente para dejar
    // ver la capa pintada una sola vez en MainActivity.
    val hasBatchResult = uiState.batchResults.isNotEmpty()
    val hasSingleResult = isPdf || conversionResult is ConversionResult.Success
    val hasAnyResult = hasSingleResult || hasBatchResult
    val scanAgainAction: () -> Unit = { converterViewModel.clearAll(); onBack() }
    // Bug real reportado por el usuario 2026-09-06: "Volver al inicio" desde
    // el resultado de un solo documento (antes de guardarlo/compartirlo) no
    // limpiaba `ScanSessionManager` -- como es un `@Singleton`, no se
    // destruye al salir de esta pantalla, así que la lista de archivos ya
    // guardados en esta sesión seguía viva y "se colaba" en la próxima vez
    // que el usuario entrara al Escáner. Ahora las 3 rutas de "Volver al
    // inicio" de esta pantalla limpian la sesión de la misma forma.
    val goHomeAction: () -> Unit = {
        scanSessionViewModel.clearSession()
        converterViewModel.clearAll()
        onDone()
    }

    // "Agregar página" (backlog UX 2026-09-06) -- ver ScanAddPageSection.
    val onAddPage = rememberAddPageLauncher(activity) { editableUris = editableUris + it }

    ScanResultBody(
        headerArgs = ScanResultHeaderArgs(
            isPremium = isPremium,
            adManager = converterViewModel.adManager,
            scannedUris = scannedUris,
            onBack = onBack
        ),
        previewArgs = ScanPreviewArgs(
            isPdf = isPdf,
            editableUris = editableUris,
            onEditPage = { index -> editingIndex = index },
            sessionFinalized = sessionFinalized
        ),
        addPageArgs = buildScanAddPageArgs(
            pageCount = editableUris.size,
            isPremium = isPremium,
            isRewardedReady = isRewardedReady,
            activity = activity,
            adManager = converterViewModel.adManager,
            onAddPage = onAddPage,
            onPremiumClick = onPremiumClick
        ),
        batchArgs = ScanBatchDisplayArgs(
            hasBatchResult = hasBatchResult,
            items = uiState.batchResults,
            savedToDownloads = uiState.batchSavedToDownloads,
            onConvertAnother = scanAgainAction,
            onSaveAllToDownloads = {
                if (scanSessionViewModel.requestScanSaveSlot()) {
                    converterViewModel.saveAllToDownloads(context)
                }
            },
            onDone = goHomeAction
        ),
        sessionArgs = ScanSessionDisplayArgs(
            sessionFinalized = sessionFinalized,
            scannedFiles = scannedSessionFiles,
            shareChooserTitle = shareChooserTitle,
            context = context,
            onScanAnother = scanAgainAction,
            onDone = goHomeAction,
            rowActions = ScanSessionRowActions(
                onOpen = onOpenDocument,
                onToggleFavorite = { id -> scanSessionViewModel.toggleFavorite(id) },
                onRename = { id, newName -> scanSessionViewModel.renameDocument(id, newName) },
                onDelete = { id -> scanSessionViewModel.deleteDocument(id) },
                onConvert = onConvertDocument,
                onCreateQr = onCreateQrFromDocument
            )
        ),
        defaultFlowArgs = ScanDefaultFlowArgs(
            hasAnyResult = hasAnyResult,
            formatArgs = ScanFormatSectionArgs(
                selectedFormat = selectedFormat,
                onFormatSelected = { selectedFormat = it },
                highResEnabled = highResEnabled,
                onHighResToggle = { highResEnabled = it },
                isPremium = isPremium,
                onPremiumClick = onPremiumClick
            ),
            fileName = fileName,
            onFileNameChange = { fileName = it },
            actionsState = ScanResultActionsState(
                isPdf = isPdf,
                scannedUris = scannedUris,
                hasResult = hasSingleResult,
                isConverting = uiState.isConverting,
                fileName = fileName,
                defaultNameTemplate = defaultNameTemplate,
                shareChooserTitle = shareChooserTitle,
                savedFile = savedFile,
                savedToDownloads = savedToDownloads,
                isPreparingShare = isPreparingShare,
                format = selectedFormat
            ),
            callbacks = ScanResultActionsCallbacks(
                onSavedToDownloadsChange = { savedToDownloads = it },
                onPreparingShareChange = { isPreparingShare = it },
                onGenerate = {
                    converterViewModel.generateFromScan(
                        context = context,
                        imageType = selectedFormat.toImageConversionType(),
                        highResolution = highResEnabled,
                        fileName = fileName
                    )
                },
                onScanAgain = scanAgainAction,
                onDone = goHomeAction,
                onFinalized = { file ->
                    scanSessionViewModel.addFile(file)
                    scanSessionViewModel.registerScanSaved()
                    sessionFinalized = true
                },
                onRequestSaveSlot = { scanSessionViewModel.requestScanSaveSlot() }
            )
        )
    )
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- contenedor visual
// (fondo transparente + LazyColumn) de todo el contenido de la pantalla,
// separado de ScanResultScreen para no sumarle líneas de más al armado de
// los 5 grupos de argumentos (LongMethod de detekt).
@Composable
private fun ScanResultBody(
    headerArgs: ScanResultHeaderArgs,
    previewArgs: ScanPreviewArgs,
    addPageArgs: ScanAddPageArgs,
    batchArgs: ScanBatchDisplayArgs,
    sessionArgs: ScanSessionDisplayArgs,
    defaultFlowArgs: ScanDefaultFlowArgs
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Pedido explícito del usuario 2026-09-07: mismo margen
            // horizontal que el resto de las pantallas. Seguimiento mismo
            // día: 12dp arriba (no 0) para que no quede pegado al borde.
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            scanResultContent(
                headerArgs = headerArgs,
                previewArgs = previewArgs,
                addPageArgs = addPageArgs,
                batchArgs = batchArgs,
                sessionArgs = sessionArgs,
                defaultFlowArgs = defaultFlowArgs
            )
        }
    }
}

// Agrupa los 3 ViewModels de ScanResultScreen en un solo parámetro para
// ScanResultSideEffects (LongParameterList de detekt, límite de 8).
private data class ScanResultViewModels(
    val editorViewModel: ScanImageEditorViewModel,
    val converterViewModel: ConverterViewModel,
    val scanSessionViewModel: ScanSessionViewModel
)

// Callbacks para mutar el estado local de ScanResultScreen desde
// ScanResultSideEffects (mismo motivo que ScanResultViewModels).
private data class ScanResultEffectCallbacks(
    val onEditingIndexChange: (Int?) -> Unit,
    val onEditableUrisChange: (List<Uri>) -> Unit,
    val onSavedFileChange: (File?) -> Unit,
    val onSessionFinalized: () -> Unit
)

// Extraído de ScanResultScreen (LongMethod de detekt) -- todos los
// diálogos y sincronizaciones de un solo golpe de la pantalla: edición de
// página (RF-SCAN-06/07), referencia al PDF generado, unificación del
// lote de imágenes a la sesión (bug real 2026-09-06) y los dos límites
// diarios (conversiones y escaneos guardados).
@Composable
private fun ScanResultSideEffects(
    editingIndex: Int?,
    editableUris: List<Uri>,
    uiState: ConverterUiState,
    isRewardedReady: Boolean,
    activity: Activity?,
    viewModels: ScanResultViewModels,
    callbacks: ScanResultEffectCallbacks
) {
    ScanPageEditDialog(
        editingIndex = editingIndex,
        editableUris = editableUris,
        editorViewModel = viewModels.editorViewModel,
        onDismiss = { callbacks.onEditingIndexChange(null) },
        onApplied = { index, result ->
            callbacks.onEditableUrisChange(
                editableUris.toMutableList().apply { set(index, result) }
            )
        }
    )

    LaunchedEffect(uiState.conversionResult) {
        (uiState.conversionResult as? ConversionResult.Success)?.let {
            callbacks.onSavedFileChange(it.outputFile)
        }
    }

    // Bug real reportado por el usuario 2026-09-06: exportar 2+ páginas a
    // JPG/WebP arma un lote que nunca se conectaba a la sesión.
    ScanBatchSessionSync(
        batchSavedToDownloads = uiState.batchSavedToDownloads,
        batchResults = uiState.batchResults,
        scanSessionViewModel = viewModels.scanSessionViewModel,
        onFinalized = {
            viewModels.scanSessionViewModel.registerScanSaved()
            callbacks.onSessionFinalized()
        }
    )

    // Bug real reportado por el usuario 2026-09-06: "Generar" no dejaba
    // avanzar (guardar/compartir) al alcanzar el límite diario de
    // conversiones -- `convert()` sí revisaba el límite y ponía
    // `showLimitDialog = true`, pero esta pantalla nunca lo mostraba (el
    // Escáner reutiliza el mismo `ConverterViewModel.convert()` del
    // Convertidor, que sí tiene este diálogo cableado). El botón quedaba
    // sin hacer nada, sin ningún aviso al usuario.
    ScanDailyLimitDialog(
        uiState = uiState,
        isRewardedReady = isRewardedReady,
        onWatchAd = { activity?.let { viewModels.converterViewModel.watchAdForConversion(it) } },
        onDismiss = { viewModels.converterViewModel.dismissLimitDialog() }
    )

    // Backlog UX (pedido explícito del usuario 2026-09-06): límite diario
    // propio de "escaneos guardados" (8/día), independiente del de
    // conversiones de arriba -- se consulta al tocar "Guardar"/"Compartir"
    // (ver `onRequestSaveSlot` en ScanResultActionsCallbacks).
    ScanSaveLimitDialogHost(scanSessionViewModel = viewModels.scanSessionViewModel, activity = activity)
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- diálogo de edición
// de página (RF-SCAN-06/07): brillo/contraste/escala sobre una página del
// escaneo, antes de generar el resultado final.
@Composable
private fun ScanPageEditDialog(
    editingIndex: Int?,
    editableUris: List<Uri>,
    editorViewModel: ScanImageEditorViewModel,
    onDismiss: () -> Unit,
    onApplied: (index: Int, result: Uri) -> Unit
) {
    val index = editingIndex ?: return
    ScanImageEditorDialog(
        uri = editableUris[index],
        onDismiss = onDismiss,
        onApply = { brightness, contrast, scalePercent ->
            editorViewModel.applyAdjustments(
                uri = editableUris[index],
                brightness = brightness,
                contrast = contrast,
                scalePercent = scalePercent
            ) { result ->
                if (result != null) {
                    onApplied(index, result)
                } else {
                    Timber.e("No se pudo aplicar el ajuste a la página ${index + 1}")
                }
                onDismiss()
            }
        }
    )
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- bug real
// reportado por el usuario 2026-09-06: al exportar 2+ páginas como
// JPG/WebP (formato distinto de PDF con más de una página), `convert()`
// arma un lote (`isBatch`, ver ConverterViewModel) que terminaba en
// `BatchConversionSuccess` -- una UI totalmente aparte que nunca se
// conectó a `ScanSessionManager`, así que esos archivos no aparecían en
// "Archivos escaneados" aunque el PDF sí. Al confirmar "Guardar todas en
// Descargas" del lote, se agregan todos los archivos exitosos a la misma
// sesión y se finaliza igual que el flujo de un solo documento.
@Composable
private fun ScanBatchSessionSync(
    batchSavedToDownloads: Boolean,
    batchResults: List<BatchConversionItem>,
    scanSessionViewModel: ScanSessionViewModel,
    onFinalized: () -> Unit
) {
    LaunchedEffect(batchSavedToDownloads) {
        if (!batchSavedToDownloads) return@LaunchedEffect
        batchResults.forEach { item ->
            (item.result as? ConversionResult.Success)?.outputFile?.let { file ->
                scanSessionViewModel.addFile(file)
            }
        }
        onFinalized()
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- diálogo de límite
// diario de conversiones alcanzado (backlog UX §35, bug real 2026-09-06:
// ver el comentario en el sitio de la llamada).
@Composable
private fun ScanDailyLimitDialog(
    uiState: ConverterUiState,
    isRewardedReady: Boolean,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!uiState.showLimitDialog) return
    DailyLimitDialog(
        usedCount = uiState.conversionCount,
        limit = uiState.conversionLimit,
        itemLabelPlural = stringResource(R.string.converter_daily_limit_label),
        isRewardedReady = isRewardedReady,
        onWatchAd = onWatchAd,
        onDismiss = onDismiss,
        onGetPremium = { }
    )
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- diálogo de límite
// diario de "escaneos guardados" (backlog UX, pedido explícito del
// usuario 2026-09-06): contador propio de 8/día, independiente del de
// conversiones de arriba -- reutiliza el mismo `DailyLimitDialog`.
@Composable
private fun ScanSaveLimitDialog(
    state: ScanSaveLimitUiState,
    isRewardedReady: Boolean,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.showLimitDialog) return
    DailyLimitDialog(
        usedCount = state.savedCount,
        limit = state.savedLimit,
        itemLabelPlural = stringResource(R.string.scan_daily_save_limit_label),
        isRewardedReady = isRewardedReady,
        onWatchAd = onWatchAd,
        onDismiss = onDismiss,
        onGetPremium = { }
    )
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- observa el estado
// del límite de "escaneos guardados" y lo pasa a ScanSaveLimitDialog.
@Composable
private fun ScanSaveLimitDialogHost(
    scanSessionViewModel: ScanSessionViewModel,
    activity: Activity?
) {
    val state by scanSessionViewModel.saveLimitState.collectAsStateWithLifecycle()
    val isRewardedReady by scanSessionViewModel.adManager.isRewardedReady.collectAsStateWithLifecycle()
    ScanSaveLimitDialog(
        state = state,
        isRewardedReady = isRewardedReady,
        onWatchAd = { activity?.let { scanSessionViewModel.watchAdForScanSave(it) } },
        onDismiss = { scanSessionViewModel.dismissScanLimitDialog() }
    )
}

private data class ScanResultHeaderArgs(
    val isPremium: Boolean,
    val adManager: AdManager,
    val scannedUris: List<Uri>,
    val onBack: () -> Unit
)

private data class ScanPreviewArgs(
    val isPdf: Boolean,
    val editableUris: List<Uri>,
    val onEditPage: (Int) -> Unit,
    val sessionFinalized: Boolean
)

// Backlog UX (pedido explícito del usuario 2026-09-06): "Agregar página"
// -- gratis mientras el total de páginas quede bajo `pageLimit`, con
// anuncio/Premium a partir de ahí (ver ScanAddPageSection).
private data class ScanAddPageArgs(
    val pageCount: Int,
    val pageLimit: Int,
    val isPremium: Boolean,
    val isRewardedReady: Boolean,
    val onAddPageDirect: () -> Unit,
    val onWatchAdForPage: () -> Unit,
    val onPremiumClick: () -> Unit
)

// Extraído de ScanResultScreen (LongMethod de detekt) -- arma ScanAddPageArgs.
private fun buildScanAddPageArgs(
    pageCount: Int,
    isPremium: Boolean,
    isRewardedReady: Boolean,
    activity: Activity?,
    adManager: AdManager,
    onAddPage: () -> Unit,
    onPremiumClick: () -> Unit
): ScanAddPageArgs = ScanAddPageArgs(
    pageCount = pageCount,
    pageLimit = SCAN_DEFAULT_PAGE_LIMIT,
    isPremium = isPremium,
    isRewardedReady = isRewardedReady,
    onAddPageDirect = onAddPage,
    onWatchAdForPage = {
        activity?.let {
            adManager.showRewardedAd(
                activity = it,
                onRewarded = onAddPage,
                onFailed = { Timber.w("ScanResultScreen: anuncio para agregar página no disponible") }
            )
        }
    },
    onPremiumClick = onPremiumClick
)

private data class ScanBatchDisplayArgs(
    val hasBatchResult: Boolean,
    val items: List<BatchConversionItem>,
    val savedToDownloads: Boolean,
    val onConvertAnother: () -> Unit,
    val onSaveAllToDownloads: () -> Unit,
    val onDone: () -> Unit
)

// Extraído aparte de ScanSessionDisplayArgs (LongParameterList de detekt,
// límite de 8) -- las opciones del menú "⋮" de cada fila de la sesión,
// mismo criterio que ya usan Biblioteca/Recientes (backlog UX §35,
// feedback del usuario tras probar la primera versión: la fila solo tenía
// un botón de compartir).
private data class ScanSessionRowActions(
    val onOpen: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onDelete: (String) -> Unit,
    val onConvert: (DocumentUiModel) -> Unit,
    val onCreateQr: (DocumentUiModel) -> Unit
)

private data class ScanSessionDisplayArgs(
    val sessionFinalized: Boolean,
    val scannedFiles: List<DocumentUiModel>,
    val shareChooserTitle: String,
    val context: Context,
    val onScanAnother: () -> Unit,
    val onDone: () -> Unit,
    val rowActions: ScanSessionRowActions
)

private data class ScanFormatSectionArgs(
    val selectedFormat: ScanExportFormat,
    val onFormatSelected: (ScanExportFormat) -> Unit,
    val highResEnabled: Boolean,
    val onHighResToggle: (Boolean) -> Unit,
    val isPremium: Boolean,
    val onPremiumClick: () -> Unit
)

private data class ScanResultActionsCallbacks(
    val onSavedToDownloadsChange: (Boolean) -> Unit,
    val onPreparingShareChange: (Boolean) -> Unit,
    val onGenerate: () -> Unit,
    val onScanAgain: () -> Unit,
    val onDone: () -> Unit,
    val onFinalized: (File) -> Unit,
    val onRequestSaveSlot: () -> Boolean
)

private data class ScanDefaultFlowArgs(
    val hasAnyResult: Boolean,
    val formatArgs: ScanFormatSectionArgs,
    val fileName: String,
    val onFileNameChange: (String) -> Unit,
    val actionsState: ScanResultActionsState,
    val callbacks: ScanResultActionsCallbacks
)

// Extraído de ScanResultScreen (LongMethod de detekt) -- todo el contenido
// de la LazyColumn (AdMob, banner, vista previa, y la rama que corresponda
// según el resultado: lote de imágenes, sesión ya finalizada, o el flujo
// normal de un documento sin terminar).
private fun LazyListScope.scanResultContent(
    headerArgs: ScanResultHeaderArgs,
    previewArgs: ScanPreviewArgs,
    addPageArgs: ScanAddPageArgs,
    batchArgs: ScanBatchDisplayArgs,
    sessionArgs: ScanSessionDisplayArgs,
    defaultFlowArgs: ScanDefaultFlowArgs
) {
    // Pedido explícito del usuario 2026-09-07: mismo margen/espaciado de
    // banner que el resto de las pantallas -- ad+banner van en un solo
    // ítem con 8dp entre ambos (el `spacedBy(20.dp)` de la LazyColumn,
    // pensado para el resto del contenido, no debe aplicar acá).
    item {
        Column {
            if (!headerArgs.isPremium) {
                DocuSmartBannerAd(adUnitId = AdConstants.BANNER_SCAN_RESULT_ID, adManager = headerArgs.adManager)
                Spacer(Modifier.height(8.dp))
            }
            DocuSmartTopBanner(
                screenTitle = stringResource(R.string.scanner_result_title),
                screenSubtitle = stringResource(R.string.scan_result_subtitle_pages, headerArgs.scannedUris.size),
                onBack = headerArgs.onBack
            )
        }
    }

    // ── Vista previa ──────────────────────────
    // Se oculta una vez finalizada la sesión de este documento (guardado o
    // compartido) -- ver ScanSessionDisplayArgs/ScanSessionManager.
    if (!previewArgs.isPdf && previewArgs.editableUris.isNotEmpty() && !previewArgs.sessionFinalized) {
        item {
            ScanPreviewSection(uris = previewArgs.editableUris, onEditPage = previewArgs.onEditPage)
        }
        item {
            ScanAddPageSection(args = addPageArgs)
        }
    }

    // El orden importa: una vez finalizada la sesión (guardado/compartido,
    // sea PDF de un solo archivo o "Guardar todas" de un lote de
    // imágenes) la lista de la sesión reemplaza cualquier otra vista,
    // incluso si `batchResults` sigue teniendo datos (nunca se limpia).
    if (sessionArgs.sessionFinalized) {
        scanSessionFinalizedItem(
            scannedFiles = sessionArgs.scannedFiles,
            shareChooserTitle = sessionArgs.shareChooserTitle,
            context = sessionArgs.context,
            onScanAnother = sessionArgs.onScanAnother,
            onDone = sessionArgs.onDone,
            rowActions = sessionArgs.rowActions
        )
    } else if (batchArgs.hasBatchResult) {
        // ── Resultado de exportar varias páginas como imágenes ──
        item {
            BatchConversionSuccess(
                items = batchArgs.items,
                savedToDownloads = batchArgs.savedToDownloads,
                onConvertAnother = batchArgs.onConvertAnother,
                onSaveAllToDownloads = batchArgs.onSaveAllToDownloads
            )
        }
        // Bug real reportado por el usuario 2026-09-06: este resultado de
        // lote (componente compartido con el Convertidor, que sí tiene la
        // barra inferior como salida alternativa) no traía ninguna forma de
        // volver a Inicio -- acá el Escáner no tiene esa barra, así que era
        // un callejón sin salida.
        item {
            TextButton(onClick = batchArgs.onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_back))
            }
        }
    } else {
        scanConfigAndActionItems(
            hasAnyResult = defaultFlowArgs.hasAnyResult,
            formatArgs = defaultFlowArgs.formatArgs,
            fileName = defaultFlowArgs.fileName,
            onFileNameChange = defaultFlowArgs.onFileNameChange,
            actionsState = defaultFlowArgs.actionsState,
            callbacks = defaultFlowArgs.callbacks
        )
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- ítems de la
// LazyColumn para elegir formato/alta resolución (backlog UX #33), nombrar
// el archivo y generar/guardar/compartir el resultado, para cuando el
// escaneo NO terminó en un lote de imágenes (ver BatchConversionSuccess).
private fun LazyListScope.scanConfigAndActionItems(
    hasAnyResult: Boolean,
    formatArgs: ScanFormatSectionArgs,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    actionsState: ScanResultActionsState,
    callbacks: ScanResultActionsCallbacks
) {
    if (!hasAnyResult) {
        item {
            ScanFormatSection(
                selectedFormat = formatArgs.selectedFormat,
                onFormatSelected = formatArgs.onFormatSelected,
                highResEnabled = formatArgs.highResEnabled,
                onHighResToggle = formatArgs.onHighResToggle,
                isPremium = formatArgs.isPremium,
                onPremiumClick = formatArgs.onPremiumClick
            )
        }
    }

    item {
        ScanFilenameField(
            fileName = fileName,
            onFileNameChange = onFileNameChange,
            extension = actionsState.format.extension
        )
    }

    item {
        ScanResultActions(
            state = actionsState,
            onSavedToDownloadsChange = callbacks.onSavedToDownloadsChange,
            onPreparingShareChange = callbacks.onPreparingShareChange,
            onGenerate = callbacks.onGenerate,
            onScanAgain = callbacks.onScanAgain,
            onDone = callbacks.onDone,
            onFinalized = callbacks.onFinalized,
            onRequestSaveSlot = callbacks.onRequestSaveSlot
        )
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- ítem de la
// LazyColumn para la lista de archivos escaneados en esta sesión.
private fun LazyListScope.scanSessionFinalizedItem(
    scannedFiles: List<DocumentUiModel>,
    shareChooserTitle: String,
    context: Context,
    onScanAnother: () -> Unit,
    onDone: () -> Unit,
    rowActions: ScanSessionRowActions
) {
    item {
        ScanSessionFinalizedSection(
            scannedFiles = scannedFiles,
            onShareFile = { document -> shareFile(context, File(document.id), shareChooserTitle) },
            onScanAnother = onScanAnother,
            onDone = onDone,
            rowActions = rowActions
        )
    }
}

// Extraído de ScanResultScreen (backlog UX #35, pedido explícito del
// usuario 2026-09-06): una vez guardado o compartido el documento, se
// cierra su vista previa/formato/nombre y se muestra, en su lugar, la
// lista de archivos escaneados en esta sesión, más la opción de escanear
// otro documento. Ampliado (mismo día, feedback del usuario): cada fila
// reutiliza el mismo `DocuSmartDocumentItem` de Biblioteca/Recientes en
// vez de una fila propia con solo "compartir" -- así el menú "⋮" trae
// también abrir/favorito/renombrar/convertir/crear QR/eliminar, con el
// mismo comportamiento (p. ej. "eliminar" mueve a la papelera real, no
// borra directo).
@Composable
private fun ScanSessionFinalizedSection(
    scannedFiles: List<DocumentUiModel>,
    onShareFile: (DocumentUiModel) -> Unit,
    onScanAnother: () -> Unit,
    onDone: () -> Unit,
    rowActions: ScanSessionRowActions
) {
    var documentToRename by remember { mutableStateOf<DocumentUiModel?>(null) }

    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onConfirm = { newName ->
                rowActions.onRename(doc.id, newName)
                documentToRename = null
            },
            onDismiss = { documentToRename = null }
        )
    }

    val buttonShape = MaterialTheme.shapes.medium
    val listShape = MaterialTheme.shapes.large
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.scan_session_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .accentShadow(listShape)
                .clip(listShape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(listShape)
        ) {
            scannedFiles.forEachIndexed { index, document ->
                DocuSmartDocumentItem(
                    document = document,
                    onClick = { rowActions.onOpen(document.id) },
                    onFavoriteClick = { rowActions.onToggleFavorite(document.id) },
                    showDivider = index < scannedFiles.size - 1,
                    onOpenClick = { rowActions.onOpen(document.id) },
                    onRenameClick = { documentToRename = document },
                    onShareClick = { onShareFile(document) },
                    onConvertClick = { rowActions.onConvert(document) },
                    onCreateQrClick = { rowActions.onCreateQr(document) },
                    onDeleteClick = { rowActions.onDelete(document.id) }
                )
            }
        }
        DocuSmartPrimaryButton(
            text = stringResource(R.string.scan_session_scan_another),
            modifier = Modifier.accentShadow(buttonShape).accentBorder(buttonShape),
            onClick = onScanAnother,
            leadingIcon = Icons.Rounded.DocumentScanner
        )
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scanner_back))
        }
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- fila de miniaturas
// de las páginas escaneadas, cada una con acceso al editor RF-SCAN-06/07.
@Composable
private fun ScanPreviewSection(uris: List<Uri>, onEditPage: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.scan_result_preview),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(uris) { index, uri ->
            ScanPageThumbnail(
                uri = uri,
                pageNumber = index + 1,
                onEditClick = { onEditPage(index) }
            )
        }
    }
}

// Backlog UX (pedido explícito del usuario 2026-09-06): lanza una
// mini-sesión de ML Kit limitada a 1 página (`pageLimit = 1`) y la agrega
// al documento actual -- reutiliza `launchDocumentScanner()`, mismo
// mecanismo que el escaneo inicial de `ScannerScreen`.
@Composable
private fun rememberAddPageLauncher(
    activity: Activity?,
    onPageAdded: (Uri) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri?.let(onPageAdded)
        }
    }
    return {
        activity?.let {
            launchDocumentScanner(
                activity = it,
                mode = ScannerMode.DOCUMENT,
                pageLimit = 1,
                onLaunched = { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                },
                onError = { message -> Timber.e("Error agregando página: $message") }
            )
        }
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- contador de
// páginas del documento actual + botón "Agregar página" (backlog UX,
// pedido explícito del usuario 2026-09-06): gratis bajo el límite base,
// con anuncio/Premium para sumar más allá de `pageLimit`.
@Composable
private fun ScanAddPageSection(args: ScanAddPageArgs) {
    var showLimitDialog by remember { mutableStateOf(false) }
    val buttonShape = MaterialTheme.shapes.medium

    if (showLimitDialog) {
        ScanPageLimitDialog(
            pageCount = args.pageCount,
            pageLimit = args.pageLimit,
            isRewardedReady = args.isRewardedReady,
            onWatchAd = { showLimitDialog = false; args.onWatchAdForPage() },
            onDismiss = { showLimitDialog = false },
            onGetPremium = args.onPremiumClick
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.scan_page_count, args.pageCount, args.pageLimit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DocuSmartSecondaryButton(
            text = stringResource(R.string.scan_add_page),
            modifier = Modifier.accentShadow(buttonShape).accentBorder(buttonShape),
            onClick = {
                if (args.isPremium || args.pageCount < args.pageLimit) {
                    args.onAddPageDirect()
                } else {
                    showLimitDialog = true
                }
            },
            leadingIcon = Icons.Rounded.AddPhotoAlternate
        )
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- diálogo de
// límite de páginas por documento (backlog UX, pedido explícito del
// usuario 2026-09-06). No reutiliza `DailyLimitDialog` porque este límite
// es por documento, no diario -- "se reinicia mañana" sería incorrecto
// acá (se reinicia al escanear un documento nuevo).
@Composable
private fun ScanPageLimitDialog(
    pageCount: Int,
    pageLimit: Int,
    isRewardedReady: Boolean,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
    onGetPremium: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                Icons.Rounded.PostAdd, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.scan_page_limit_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                stringResource(R.string.scan_page_limit_body, pageCount, pageLimit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onWatchAd,
                    enabled = isRewardedReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (isRewardedReady) R.string.daily_limit_watch_ad
                            else R.string.daily_limit_ad_not_ready
                        )
                    )
                }
                OutlinedButton(
                    onClick = onGetPremium,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.daily_limit_get_premium))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.general_cancel)) }
            }
        },
        dismissButton = {}
    )
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- campo de nombre
// del archivo antes de guardar/generar el resultado.
@Composable
private fun ScanFilenameField(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    extension: String = "pdf"
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.scan_result_filename_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = fileName,
            onValueChange = onFileNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.scan_result_filename_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Text(
                    text = ".$extension",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        Text(
            text = stringResource(R.string.scan_result_filename_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Backlog UX #33: elegir el formato de salida (PDF/JPG/WebP) y, solo para
// PDF, activar "Alta resolución" (Premium) -- ver ConvertImageToPdfUseCase
// para el porqué el PDF es el único formato que se beneficia de esto (JPG/
// WebP ya exportan siempre a la resolución nativa de la cámara).
@Composable
private fun ScanFormatSection(
    selectedFormat: ScanExportFormat,
    onFormatSelected: (ScanExportFormat) -> Unit,
    highResEnabled: Boolean,
    onHighResToggle: (Boolean) -> Unit,
    isPremium: Boolean,
    onPremiumClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.scan_result_export_format_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanExportFormat.entries.forEach { format ->
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { onFormatSelected(format) },
                    label = { Text(format.label) },
                    colors = accentFilterChipColors()
                )
            }
        }

        if (selectedFormat == ScanExportFormat.PDF) {
            val highResClickable = if (isPremium) {
                Modifier
            } else {
                Modifier.clickable(onClick = onPremiumClick)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(highResClickable)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_result_high_res_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isPremium) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(
                                    R.string.scan_result_high_res_premium_content_desc
                                ),
                                tint = PremiumGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.scan_result_high_res_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = highResEnabled && isPremium,
                    onCheckedChange = { checked ->
                        if (isPremium) onHighResToggle(checked) else onPremiumClick()
                    },
                    enabled = isPremium
                )
            }
        }
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- los valores de
// solo lectura que la sección de botones necesita, agrupados en una clase
// en vez de 10 parámetros sueltos (superaría LongParameterList).
private data class ScanResultActionsState(
    val isPdf: Boolean,
    val scannedUris: List<Uri>,
    val hasResult: Boolean,
    val isConverting: Boolean,
    val fileName: String,
    val defaultNameTemplate: String,
    val shareChooserTitle: String,
    val savedFile: File?,
    val savedToDownloads: Boolean,
    val isPreparingShare: Boolean,
    val format: ScanExportFormat
)

// Extraído de ScanResultScreen (LongMethod de detekt) -- guardar/compartir/
// generar el resultado (PDF o imagen, backlog UX #33), según el estado
// actual del escaneo.
@Composable
private fun ScanResultActions(
    state: ScanResultActionsState,
    onSavedToDownloadsChange: (Boolean) -> Unit,
    onPreparingShareChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
    onFinalized: (File) -> Unit,
    onRequestSaveSlot: () -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Backlog UX #34: mismo criterio de "botón con borde/sombra de acento"
    // ya usado en las tarjetas de Inicio/Biblioteca -- acá en vez de sobre
    // un Card, envolviendo cada botón vía su `modifier` (los componentes
    // compartidos DocuSmartPrimaryButton/SecondaryButton no se tocan, para
    // no afectar el resto de la app con este cambio acotado al Escáner).
    val buttonShape = MaterialTheme.shapes.medium
    val accentButtonModifier = Modifier.accentShadow(buttonShape).accentBorder(buttonShape)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.hasResult) {
            if (state.savedToDownloads) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.general_saved_downloads),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                DocuSmartPrimaryButton(
                    text = stringResource(R.string.converter_save),
                    modifier = accentButtonModifier,
                    onClick = {
                        if (onRequestSaveSlot()) {
                            scope.launch {
                                val name = state.fileName.ifBlank {
                                    String.format(state.defaultNameTemplate, generateTimestamp())
                                }
                                val success = when {
                                    state.savedFile != null -> DownloadsSaver.saveFile(
                                        context, state.savedFile, mimeTypeForExtension(state.savedFile.extension)
                                    )
                                    state.isPdf -> DownloadsSaver.saveUri(
                                        context, state.scannedUris.first(), MIME_PDF, "$name.pdf"
                                    )
                                    else -> false
                                }
                                onSavedToDownloadsChange(success)
                                if (success && state.savedFile != null) onFinalized(state.savedFile)
                            }
                        }
                    },
                    leadingIcon = Icons.Rounded.Download
                )
            }

            if (state.isPreparingShare) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                DocuSmartSecondaryButton(
                    text = stringResource(R.string.scan_result_share_format, state.format.label),
                    modifier = accentButtonModifier,
                    onClick = {
                        if (onRequestSaveSlot()) {
                            scope.launch {
                                onPreparingShareChange(true)
                                try {
                                    shareScanResult(context, state)
                                    if (state.savedFile != null) onFinalized(state.savedFile)
                                } finally {
                                    onPreparingShareChange(false)
                                }
                            }
                        }
                    },
                    leadingIcon = Icons.Rounded.Share
                )
            }

            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_back))
            }
        } else if (state.isConverting) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.scan_result_generating_format, state.format.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            DocuSmartPrimaryButton(
                text = stringResource(R.string.scan_result_generate_format, state.format.label),
                modifier = accentButtonModifier,
                onClick = onGenerate,
                leadingIcon = if (state.format == ScanExportFormat.PDF) {
                    Icons.Rounded.PictureAsPdf
                } else {
                    Icons.Rounded.Image
                }
            )
            DocuSmartSecondaryButton(
                text = stringResource(R.string.scanner_again),
                modifier = accentButtonModifier,
                onClick = onScanAgain,
                leadingIcon = Icons.Rounded.DocumentScanner
            )
            // Bug real reportado por el usuario 2026-09-06: antes de generar,
            // la única forma de salir era el "Volver" del banner (que
            // reabre la cámara al volver al Escáner) -- sin una salida
            // directa a Inicio, igual que ya tienen las demás ramas de esta
            // pantalla.
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_back))
            }
        }
    }
}

private suspend fun shareScanResult(context: Context, state: ScanResultActionsState) {
    when {
        state.savedFile != null -> shareFile(context, state.savedFile, state.shareChooserTitle)
        state.isPdf -> {
            val cacheFile = copyUriToCache(
                context,
                state.scannedUris.first(),
                state.fileName.ifBlank { String.format(state.defaultNameTemplate, generateTimestamp()) }
            )
            if (cacheFile != null) {
                shareFile(context, cacheFile, state.shareChooserTitle)
            } else {
                Timber.e("No se pudo copiar PDF al cache")
            }
        }
    }
}

// Extraído de ScanResultScreen (LongMethod de detekt) -- una página
// escaneada en la fila de vista previa, con su botón de edición
// (RF-SCAN-06/07) superpuesto en la esquina.
@Composable
private fun ScanPageThumbnail(
    uri: Uri,
    pageNumber: Int,
    onEditClick: () -> Unit
) {
    Box(modifier = Modifier.size(120.dp, 160.dp)) {
        val shape = MaterialTheme.shapes.medium
        Box(
            modifier = Modifier
                .fillMaxSize()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
        ) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.scan_result_page_content_desc, pageNumber),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
            )
        }
        IconButton(
            onClick = onEditClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.scan_edit_page_content_desc, pageNumber),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── RF-SCAN-06/07: editor de brillo/contraste/escala ──
// de una página escaneada. Vista previa en vivo con el mismo arreglo de
// matriz de color (buildColorMatrix) que después se usa para el bake real
// sobre el bitmap -- lo que se ve en el diálogo es exactamente lo que
// queda guardado al tocar "Aplicar".
private val SCAN_EDIT_SCALE_OPTIONS = listOf(100, 75, 50, 25)

// Backlog UX #35 (pedido explícito del usuario 2026-09-06): brillo y
// contraste se ajustan arrastrando un slider de 0 a 100 (50 = sin cambios),
// en vez de chips de valores fijos -- el usuario probó los chips en
// dispositivo real y pidió volver a un control continuo tipo "scroll".
// `buildColorMatrix()` espera un rango simétrico -100..100 (0 = sin
// cambios), así que el valor mostrado en pantalla (0..100) se convierte
// a ese rango interno antes de usarlo -- ver `displayToInternal()`.
private const val SCAN_EDIT_DISPLAY_NEUTRAL = 50f
private fun displayToInternal(display: Float): Int = ((display - SCAN_EDIT_DISPLAY_NEUTRAL) * 2f).roundToInt()

// Extraído de ScanImageEditorDialog -- fila de chips de porcentaje para
// Escala (Brillo/Contraste usan un Slider, ver arriba). Son porcentajes
// absolutos de tamaño, no un offset desde un punto neutro -- a diferencia
// de un eventual "+50%", se muestran tal cual ("50%").
@Composable
private fun PercentChipRow(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { percent ->
            FilterChip(
                selected = selected == percent,
                onClick = { onSelect(percent) },
                label = { Text("$percent%") },
                colors = accentFilterChipColors()
            )
        }
    }
}

@Composable
private fun ScanImageEditorDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onApply: (brightness: Int, contrast: Int, scalePercent: Int) -> Unit
) {
    var brightnessDisplay by remember { mutableFloatStateOf(SCAN_EDIT_DISPLAY_NEUTRAL) }
    var contrastDisplay by remember { mutableFloatStateOf(SCAN_EDIT_DISPLAY_NEUTRAL) }
    var scalePercent by remember { mutableIntStateOf(100) }
    val brightness = displayToInternal(brightnessDisplay)
    val contrast = displayToInternal(contrastDisplay)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.scan_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Bug real reportado por el usuario 2026-09-06: "Escala" no
                    // se veía hacer nada -- el bake final sí reducía la imagen
                    // (ver `ScanImageEditor.scaleBitmap()`), pero la vista previa
                    // en vivo solo aplicaba brillo/contraste, nunca el tamaño, así
                    // que mover los chips de Escala no mostraba ningún cambio.
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(scalePercent / 100f)
                            .fillMaxHeight(scalePercent / 100f),
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix(buildColorMatrix(brightness, contrast))
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.scan_edit_brightness, brightnessDisplay.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = brightnessDisplay,
                    onValueChange = { brightnessDisplay = it },
                    valueRange = 0f..100f
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.scan_edit_contrast, contrastDisplay.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = contrastDisplay,
                    onValueChange = { contrastDisplay = it },
                    valueRange = 0f..100f
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.scan_edit_scale),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                PercentChipRow(
                    options = SCAN_EDIT_SCALE_OPTIONS,
                    selected = scalePercent,
                    onSelect = { scalePercent = it }
                )

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.general_cancel))
                    }
                    Button(
                        onClick = { onApply(brightness, contrast, scalePercent) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.scan_edit_apply))
                    }
                }
            }
        }
    }
}

// ── Copiar URI al cache para compartir ────────────────
private suspend fun copyUriToCache(
    context: Context,
    uri: Uri,
    fileName: String
): File? = withContext(Dispatchers.IO) {
    try {
        val cacheDir = File(context.cacheDir, "scanner").apply { mkdirs() }
        val cacheFile = File(cacheDir, "$fileName.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    } catch (e: Exception) {
        Timber.e(e, "Error copiando URI al cache: ${e.message}")
        null
    }
}

// ── Compartir archivo via FileProvider ────────────────
private fun shareFile(context: Context, file: File, chooserTitle: String) {
    try {
        if (!file.exists()) {
            Timber.e("shareFile: archivo no existe — ${file.absolutePath}")
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeForExtension(file.extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        Timber.d("shareFile: compartiendo ${file.name}")
    } catch (e: Exception) {
        Timber.e(e, "Error compartiendo archivo: ${e.message}")
    }
}


private fun generateTimestamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())