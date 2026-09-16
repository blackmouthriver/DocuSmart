package com.docsmart.features.converter.presentation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DailyLimitDialog
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.TooltipIconButton
import com.docsmart.core.ui.theme.*
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.model.HIDDEN_FROM_UI
import com.docsmart.features.converter.presentation.components.BatchConversionSuccess
import com.docsmart.features.converter.presentation.components.ConversionProgress
import com.docsmart.features.converter.presentation.components.ConversionSuccess
import com.docsmart.features.scanner.presentation.ScannerMode
import com.docsmart.features.scanner.presentation.launchDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

// Extraído de ConverterScreen (detekt: LongMethod) -- las 5 secciones por
// categoría (Imagen/PDF/Word/Excel/PowerPoint) eran código casi idéntico
// repetido 5 veces, solo cambiaba el título/ícono/color.
private data class ConversionCategory(
    val title: String,
    val icon : ImageVector,
    val color: Color
)

private val CONVERSION_CATEGORIES = listOf(
    ConversionCategory("Imagen",     Icons.Rounded.Image,        ColorImage),
    ConversionCategory("PDF",        Icons.Rounded.PictureAsPdf, ColorPdf),
    ConversionCategory("Word",       Icons.Rounded.Description,  ColorWord),
    ConversionCategory("Excel",      Icons.Rounded.TableChart,   ColorExcel),
    ConversionCategory("PowerPoint", Icons.Rounded.Slideshow,    ColorPowerPoint)
)

@Composable
fun ConverterScreen(
    initialType        : String? = null,
    initialFileUri      : String? = null,
    initialFileCategory: String? = null,
    // Pedido explícito del usuario 2026-09-12 (feedback de testers): abre
    // el visor ya existente para el archivo recién convertido, en vez de
    // dejar la pantalla de éxito como destino final del flujo.
    onOpenDocument      : (String) -> Unit = {},
    viewModel  : ConverterViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium       by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val isRewardedReady by viewModel.adManager.isRewardedReady.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context           = LocalContext.current
    val activity          = context as? Activity

    // Acceso rápido "Img→PDF" de Home: llega con el tipo ya elegido, sin
    // pasar por la grilla de selección manual. `LaunchedEffect(initialType)`
    // en vez de leerlo directo en el init del ViewModel -- así una
    // recomposición por rotación no vuelve a pisar una selección que el
    // usuario ya cambió manualmente dentro de la misma sesión de pantalla.
    LaunchedEffect(initialType) {
        applyInitialType(initialType, uiState.selectedType, viewModel::onTypeSelected)
    }

    // Atajo "Convertir" desde el menú "⋮" de un archivo ya elegido (backlog
    // UX 2026-08-30, HU-UX-02) -- precarga el archivo en el ViewModel para
    // que quede adjunto en cuanto el usuario elija un tipo de conversión de
    // la misma categoría (ver `ConverterViewModel.preloadFile`).
    LaunchedEffect(initialFileUri, initialFileCategory) {
        if (initialFileUri != null && initialFileCategory != null) {
            viewModel.preloadFile(Uri.parse(initialFileUri), initialFileCategory)
        }
    }

    // RF-CONV-08: selector multi-archivo para todos los tipos, no solo
    // IMAGE_TO_PDF -- habilita elegir varios archivos para conversión por
    // lotes (N archivos → N salidas). El picker del sistema permite elegir
    // uno solo igual, así que no hace falta un launcher separado para eso.
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.onFilesSelected(uris) }

    // Atajo "Capturar con cámara" (backlog UX 2026-08-30, HU-UX-03) --
    // reutiliza el mismo escáner de ML Kit ya probado en la pantalla
    // Escáner (siempre devuelve páginas como imagen, nunca PDF directo).
    // Cancelar (RESULT_CANCELED) no hace nada, deja el selector como estaba.
    val onCaptureWithCamera = rememberCaptureWithCameraAction(
        activity        = activity,
        onFilesSelected = viewModel::onFilesSelected,
        onScanError     = viewModel::onScanError
    )

    ConverterScreenSideEffects(
        uiState           = uiState,
        snackbarHostState = snackbarHostState,
        isRewardedReady   = isRewardedReady,
        activity          = activity,
        viewModel         = viewModel
    )

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        // Fondo animado global (backlog UX 2026-09-06): transparente para
        // dejar ver la capa pintada una sola vez en MainActivity. Se excluyen
        // los insets superior e inferior de systemBars (bug real "línea
        // blanca" + margen superior duplicado 2026-09-07: este Scaffold los
        // reservaba por duplicado sobre los que ya reserva el Scaffold de
        // MainActivity para toda la navegación -- no pasaba en Home/
        // Biblioteca/Ajustes/Seguridad porque esas pantallas no tienen su
        // propio Scaffold).
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal
        ),
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Pedido explícito del usuario 2026-09-07: mismo margen/espaciado
            // de banner que el resto de las pantallas -- ver
            // DocuSmartScreenHeader. `bannerBottomSpacing` reemplaza el
            // `vertical = 24.dp` que antes tenía el banner (esta pantalla usa
            // `spacedBy(0.dp)` a propósito para el resto de su contenido, así
            // que sin este espacio el banner quedaría pegado al siguiente
            // elemento).
            item {
                DocuSmartScreenHeader(
                    adUnitId  = AdConstants.BANNER_CONVERTER_ID,
                    adManager = viewModel.adManager,
                    bannerBottomSpacing = 24.dp
                ) {
                    DocuSmartTopBanner(
                        screenTitle    = stringResource(R.string.converter_title),
                        screenSubtitle = stringResource(R.string.converter_subtitle)
                    )
                }
            }

            // ── Contador de conversiones (usuarios free) ──────────────────────
            if (!isPremium) {
                item {
                    ConversionLimitIndicator(
                        count    = uiState.conversionCount,
                        limit    = uiState.conversionLimit,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.batchResults.isNotEmpty()) {
                item {
                    BatchConversionSuccess(
                        items                = uiState.batchResults,
                        savedToDownloads     = uiState.batchSavedToDownloads,
                        onConvertAnother     = { viewModel.clearAll() },
                        onSaveAllToDownloads = { viewModel.saveAllToDownloads(context) },
                        onOpenDocument       = { file -> onOpenDocument(file.absolutePath) },
                        modifier             = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            val result = uiState.conversionResult
            if (result is ConversionResult.Success) {
                item {
                    ConversionSuccess(
                        result            = result,
                        savedToDownloads  = uiState.savedToDownloads,
                        onConvertAnother  = { viewModel.clearAll() },
                        onSaveToDownloads = { viewModel.saveToDownloads(context) },
                        onOpenDocument    = { onOpenDocument(result.outputFile.absolutePath) },
                        modifier          = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            if (uiState.isConverting) {
                item {
                    ConversionProgress(
                        totalImages = uiState.selectedFiles.size,
                        modifier    = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            if (uiState.selectedType != null) {
                item {
                    ConversionDetailCard(
                        type             = uiState.selectedType!!,
                        selectedFiles    = uiState.selectedFiles,
                        fileName         = uiState.fileName,
                        // Hallazgo real de la revisión general 2026-09-16
                        // (#31): el guard de re-entrada real ya vive en
                        // ConverterViewModel.convert() (sincrónico, antes
                        // del launch) -- esto es defensa adicional en la UI
                        // para que el botón no quede tocable un instante
                        // entre el tap y la recomposición.
                        isConverting     = uiState.isConverting,
                        onFileNameChange = { viewModel.onFileNameChange(it) },
                        onSelectFiles    = { fileLauncher.launch(getMimeForType(uiState.selectedType!!)) },
                        // La cámara solo puede producir imágenes -- ofrecerla
                        // como origen no tiene sentido para PDF/Word/Excel/PPT.
                        onCaptureWithCamera = if (uiState.selectedType!!.fromFormat == "Imagen") {
                            onCaptureWithCamera
                        } else null,
                        onRemoveFile     = { viewModel.removeImage(it) },
                        onConvert = { viewModel.convert(context) },
                        onBack    = { viewModel.clearAll() },
                        modifier  = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            item {
                Text(
                    text       = stringResource(R.string.converter_select),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            val allTypes = ConversionType.entries.filterNot { it in HIDDEN_FROM_UI }
            CONVERSION_CATEGORIES.forEach { category ->
                val types = allTypes.filter { it.getCategoryForUi() == category.title }
                if (types.isNotEmpty()) {
                    item {
                        ConversionSection(
                            title          = category.title,
                            icon           = category.icon,
                            color          = category.color,
                            types          = types,
                            onTypeSelected = { viewModel.onTypeSelected(it) }
                        )
                    }
                }
            }
        }
    }
}

// Extraído de ConverterScreen (detekt: LongMethod) -- launcher del atajo
// "Capturar con cámara" (backlog UX 2026-08-30, HU-UX-03), reutiliza el
// mismo escáner de ML Kit que ya usa la pantalla Escáner (siempre devuelve
// páginas como imagen, nunca PDF directo).
@Composable
private fun rememberCaptureWithCameraAction(
    activity: Activity?,
    onFilesSelected: (List<Uri>) -> Unit,
    onScanError: (String) -> Unit
): () -> Unit {
    val scannerStartErrorTemplate = stringResource(R.string.scanner_start_error)
    val documentScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult
                .fromActivityResultIntent(result.data)
                ?.pages?.mapNotNull { it.imageUri } ?: emptyList()
            if (pages.isNotEmpty()) onFilesSelected(pages)
        }
    }
    return {
        activity?.let { act ->
            launchDocumentScanner(
                activity   = act,
                mode       = ScannerMode.DOCUMENT,
                onLaunched = { intentSender ->
                    documentScanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                },
                onError    = { message ->
                    onScanError(String.format(scannerStartErrorTemplate, message))
                }
            )
        }
    }
}

// Extraído de ConverterScreen (detekt: LongMethod) -- efectos y diálogo de
// límite diario, sin relación visual con el LazyColumn principal.
@Composable
private fun ConverterScreenSideEffects(
    uiState: ConverterUiState,
    snackbarHostState: SnackbarHostState,
    isRewardedReady: Boolean,
    activity: Activity?,
    viewModel: ConverterViewModel
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.conversionResult) {
        if (uiState.conversionResult is ConversionResult.Success) {
            activity?.let { viewModel.adManager.onConversionCompleted(it) }
        }
    }

    if (uiState.showLimitDialog) {
        DailyLimitDialog(
            usedCount       = uiState.conversionCount,
            limit           = uiState.conversionLimit,
            itemLabelPlural = stringResource(R.string.converter_daily_limit_label),
            isRewardedReady = isRewardedReady,
            onWatchAd       = { activity?.let { viewModel.watchAdForConversion(it) } },
            onDismiss       = { viewModel.dismissLimitDialog() },
            onGetPremium    = { }
        )
    }
}

// ── Indicador de límite diario ────────────────────────────────────────────────
@Composable
private fun ConversionLimitIndicator(
    count   : Int,
    limit   : Int,
    modifier: Modifier = Modifier
) {
    if (count == 0) return
    val progress = (count.toFloat() / limit).coerceIn(0f, 1f)
    val color    = when {
        progress >= 1f   -> MaterialTheme.colorScheme.error
        progress >= 0.6f -> MaterialTheme.colorScheme.tertiary
        else             -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SwapHoriz, null,
                    tint = color, modifier = Modifier.size(16.dp))
                Text(
                    // Hallazgo real de la revisión general 2026-09-16
                    // (#44): hardcodeado en español sin stringResource,
                    // visible para todo usuario free.
                    stringResource(R.string.converter_daily_count_label, count, limit),
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .width(80.dp)
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
                color      = color,
                trackColor = color.copy(alpha = 0.2f)
            )
        }
    }
}

// ── Sección por categoría ─────────────────────────────────────────────────────
@Composable
private fun ConversionSection(
    title         : String,
    icon          : ImageVector,
    color         : Color,
    types         : List<ConversionType>,
    onTypeSelected: (ConversionType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(
                modifier  = Modifier.weight(1f),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }
        val rows = types.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { type ->
                    ConversionGridCard(
                        type     = type,
                        onClick  = { onTypeSelected(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ── Tarjeta de grilla ─────────────────────────────────────────────────────────
@Composable
private fun ConversionGridCard(
    type    : ConversionType,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    val (fromColor, fromIcon) = getFormatStyle(type.fromFormat)
    val (toColor,   toIcon)   = getFormatStyle(type.toFormat)

    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .height(110.dp)
            .accentShadow(shape = shape, elevation = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .accentBorder(shape = shape)
            .clickable { onClick() }
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(fromColor.copy(alpha = 0.15f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(fromIcon, null, tint = fromColor, modifier = Modifier.size(18.dp))
                }
                Icon(Icons.Rounded.ArrowForward, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(toColor.copy(alpha = 0.15f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(toIcon, null, tint = toColor, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(
                    text       = type.localizedLabel(),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1
                )
                // Hallazgo real de la revisión general 2026-09-16 (#46):
                // título y subtítulo mostraban el mismo "Origen → Destino"
                // dos veces -- se reemplaza por las extensiones de origen
                // aceptadas (dato real y distinto, no un texto inventado),
                // visible en ~17 tarjetas de la grilla.
                Text(
                    text     = type.fromExtensions.joinToString(", ") { it.uppercase() },
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Detalle de conversión ─────────────────────────────────────────────────────
@Composable
private fun ConversionDetailCard(
    type               : ConversionType,
    selectedFiles      : List<Uri>,
    fileName           : String,
    isConverting       : Boolean = false,
    onFileNameChange   : (String) -> Unit,
    onSelectFiles      : () -> Unit,
    onCaptureWithCamera: (() -> Unit)? = null,
    onRemoveFile       : (Uri) -> Unit,
    onConvert          : () -> Unit,
    onBack             : () -> Unit,
    modifier           : Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TooltipIconButton(
                onClick     = onBack,
                tooltipText = stringResource(R.string.general_back_action),
                icon        = Icons.Rounded.ArrowBackIosNew,
                tint        = MaterialTheme.colorScheme.primary
            )
            Text(
                text       = type.localizedLabel(),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier  = Modifier.fillMaxWidth().clickable { onSelectFiles() },
            shape     = MaterialTheme.shapes.large,
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp))
                Text(
                    text = if (selectedFiles.isEmpty())
                        stringResource(R.string.converter_select_files, type.localizedFromFormat())
                    else
                        stringResource(R.string.converter_files_selected, selectedFiles.size),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = if (selectedFiles.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text  = stringResource(
                        R.string.converter_formats,
                        type.fromExtensions.joinToString(", ").uppercase()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Atajo "Capturar con cámara" (backlog UX 2026-08-30, HU-UX-03) --
        // junto a la opción de elegir del dispositivo, solo para origen
        // Imagen (es lo único que la cámara puede producir) y solo antes de
        // tener ya un archivo elegido (evita mezclar fuentes a mitad de
        // camino, fuera del alcance de esta HU).
        if (onCaptureWithCamera != null && selectedFiles.isEmpty()) {
            OutlinedButton(
                onClick  = onCaptureWithCamera,
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.converter_capture_camera))
            }
        }

        // Hallazgo de QA "vista en carrusel se ve vacía": el picker de
        // arriba solo mostraba un conteo en texto ("N archivo(s)
        // seleccionado(s)"), sin ninguna miniatura -- para conversiones con
        // origen Imagen, donde una vista previa visual sí aporta (a
        // diferencia de Word/Excel/PDF, donde un ícono genérico basta),
        // se agrega el carrusel real de miniaturas.
        if (type.fromFormat == "Imagen" && selectedFiles.isNotEmpty()) {
            SelectedImagesCarousel(uris = selectedFiles, onRemove = onRemoveFile)
        }

        // RF-CONV-08: con varios archivos (fuera de IMAGE_TO_PDF, que fusiona
        // todo en un solo PDF) cada archivo produce su propia salida con su
        // propio nombre original -- el campo de nombre único no aplica.
        val isBatchMode = selectedFiles.size > 1 && type != ConversionType.IMAGE_TO_PDF

        if (selectedFiles.isNotEmpty()) {
            if (isBatchMode) {
                Text(
                    text  = stringResource(R.string.converter_batch_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value         = fileName,
                    onValueChange = onFileNameChange,
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text(stringResource(R.string.converter_file_name_label)) },
                    placeholder   = { Text(stringResource(R.string.converter_file_name_placeholder)) },
                    trailingIcon  = {
                        Text(
                            text     = ".${type.outputExtension}",
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    shape      = MaterialTheme.shapes.medium
                )
            }

            Button(
                onClick  = onConvert,
                enabled  = !isConverting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (isBatchMode)
                        stringResource(R.string.converter_convert_batch_button, selectedFiles.size)
                    else
                        stringResource(R.string.converter_to_format, type.localizedToFormat()),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// Extraído de ConversionDetailCard -- miniaturas reales de las imágenes ya
// seleccionadas para una conversión con origen Imagen (corrige el hallazgo
// de QA "vista en carrusel se ve vacía": antes no existía ningún carrusel,
// solo un texto de conteo).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectedImagesCarousel(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(uris) { _, uri ->
            Box(modifier = Modifier.size(84.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                )
                // Botón subido de 24dp a 48dp (auditoría de testers
                // 2026-09-12, "botones pequeños") -- el badge visual se
                // mantiene chico (24dp) para no tapar la miniatura de 84dp,
                // pero el área táctil real ahora cumple el mínimo de Android.
                // Tooltip agregado el mismo día (feedback de testers): un
                // badge sin texto sobre una miniatura chica no siempre se
                // reconoce como tocable a primera vista.
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.general_delete)) } },
                    state = rememberTooltipState(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { onRemove(uri) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.general_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

// Extraído de ConverterScreen (detekt: LongMethod) -- resuelve el tipo
// pasado por navegación (nombre de ConversionType) al abrir la pantalla
// desde un acceso directo. No hace nada si ya hay un tipo seleccionado
// (evita pisar una elección manual del usuario) o si el nombre no
// coincide con ningún ConversionType real.
private fun applyInitialType(
    initialType   : String?,
    currentType   : ConversionType?,
    onTypeSelected: (ConversionType) -> Unit
) {
    if (initialType == null || currentType != null) return
    ConversionType.entries.find { it.name == initialType }?.let(onTypeSelected)
}

private fun ConversionType.getCategoryForUi(): String = when (this) {
    ConversionType.IMAGE_TO_PDF,
    ConversionType.IMAGE_TO_JPG,
    ConversionType.IMAGE_TO_PNG,
    ConversionType.IMAGE_TO_WEBP,
    ConversionType.IMAGE_TO_BMP  -> "Imagen"
    ConversionType.PDF_TO_IMAGE,
    ConversionType.PDF_TO_TXT,
    ConversionType.PDF_TO_WORD,
    ConversionType.PDF_TO_HTML   -> "PDF"
    ConversionType.WORD_TO_PDF,
    ConversionType.WORD_TO_TXT,
    ConversionType.WORD_TO_HTML  -> "Word"
    ConversionType.EXCEL_TO_PDF,
    ConversionType.EXCEL_TO_CSV,
    ConversionType.EXCEL_TO_HTML -> "Excel"
    ConversionType.PPT_TO_PDF,
    ConversionType.PPT_TO_TXT   -> "PowerPoint"
}

private fun getFormatStyle(format: String): Pair<Color, ImageVector> = when (format.lowercase()) {
    "pdf"                        -> Pair(ColorPdf,        Icons.Rounded.PictureAsPdf)
    "imagen","image","jpg","png",
    "webp","bmp"                 -> Pair(ColorImage,      Icons.Rounded.Image)
    "word","docx"                -> Pair(ColorWord,       Icons.Rounded.Description)
    "excel"                      -> Pair(ColorExcel,      Icons.Rounded.TableChart)
    "powerpoint"                 -> Pair(ColorPowerPoint, Icons.Rounded.Slideshow)
    "txt","texto","text"         -> Pair(ColorText,       Icons.Rounded.TextSnippet)
    "csv"                        -> Pair(ColorExcel,      Icons.Rounded.GridOn)
    "html"                       -> Pair(ColorOcr,        Icons.Rounded.Code)
    else                         -> Pair(ColorText,       Icons.Rounded.InsertDriveFile)
}

// ── Localización de ConversionType ────────────────────────────────────────
// fromFormat/toFormat son claves internas fijas (usadas también por
// getFormatStyle() para elegir color/ícono) -- "PDF", "Word", "Excel",
// "PowerPoint" y las extensiones (JPG, WebP, TXT, HTML, CSV...) son
// nombres propios/abreviaturas iguales en los 5 idiomas del proyecto, así
// que no necesitan traducción. La única palabra real es "Imagen".
@Composable
private fun localizedFormatName(format: String): String =
    if (format == "Imagen") stringResource(R.string.format_name_image) else format

@Composable
private fun ConversionType.localizedFromFormat(): String = localizedFormatName(fromFormat)

@Composable
private fun ConversionType.localizedToFormat(): String = localizedFormatName(toFormat)

@Composable
private fun ConversionType.localizedLabel(): String =
    "${localizedFromFormat()} → ${localizedToFormat()}"

private fun getMimeForType(type: ConversionType): String = when (type) {
    ConversionType.IMAGE_TO_PDF,
    ConversionType.IMAGE_TO_JPG,
    ConversionType.IMAGE_TO_PNG,
    ConversionType.IMAGE_TO_WEBP,
    ConversionType.IMAGE_TO_BMP  -> "image/*"
    ConversionType.PDF_TO_IMAGE,
    ConversionType.PDF_TO_TXT,
    ConversionType.PDF_TO_WORD,
    ConversionType.PDF_TO_HTML   -> "application/pdf"
    ConversionType.WORD_TO_PDF,
    ConversionType.WORD_TO_TXT,
    ConversionType.WORD_TO_HTML  -> "application/msword"
    ConversionType.EXCEL_TO_PDF,
    ConversionType.EXCEL_TO_CSV,
    ConversionType.EXCEL_TO_HTML -> "*/*"
    ConversionType.PPT_TO_PDF,
    ConversionType.PPT_TO_TXT   -> "*/*"
}