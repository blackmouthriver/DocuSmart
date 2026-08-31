package com.docsmart.features.scanner.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.buttons.DocuSmartPrimaryButton
import com.docsmart.core.ui.components.buttons.DocuSmartSecondaryButton
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.presentation.ConverterViewModel
import com.docsmart.features.scanner.domain.buildColorMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIME_PDF = "application/pdf"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    scannedUris: List<Uri>,
    isPdf: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    converterViewModel: ConverterViewModel = hiltViewModel(),
    editorViewModel: ScanImageEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by converterViewModel.uiState.collectAsState()
    val isPremium by converterViewModel.adManager.isPremium.collectAsStateWithLifecycle()

    var fileName by remember { mutableStateOf("") }
    var savedToDownloads by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var isPreparingShare by remember { mutableStateOf(false) }

    // RF-SCAN-06/07: lista editable -- empieza igual al resultado del
    // escáner, y cada página editada reemplaza su URI original por la del
    // archivo ya ajustado (brillo/contraste/escala), sin tocar las demás.
    var editableUris by remember(scannedUris) { mutableStateOf(scannedUris) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val defaultNameTemplate = stringResource(R.string.scan_result_default_name_prefix)
    val shareChooserTitle   = stringResource(R.string.scanner_share)

    // ── Inicializar según tipo de resultado ───────────
    LaunchedEffect(editableUris, isPdf) {
        if (!isPdf) {
            converterViewModel.onImagesSelected(editableUris)
        }
    }

    // ── Diálogo de edición (RF-SCAN-06/07) ────────────
    editingIndex?.let { index ->
        ScanImageEditorDialog(
            uri = editableUris[index],
            onDismiss = { editingIndex = null },
            onApply = { brightness, contrast, scalePercent ->
                editorViewModel.applyAdjustments(
                    uri = editableUris[index],
                    brightness = brightness,
                    contrast = contrast,
                    scalePercent = scalePercent
                ) { result ->
                    if (result != null) {
                        editableUris = editableUris.toMutableList().apply { set(index, result) }
                    } else {
                        Timber.e("No se pudo aplicar el ajuste a la página ${index + 1}")
                    }
                    editingIndex = null
                }
            }
        )
    }

    // ── Cuando se convierte a PDF, guardar referencia ─
    val conversionResult = uiState.conversionResult
    LaunchedEffect(conversionResult) {
        if (conversionResult is ConversionResult.Success) {
            savedFile = conversionResult.outputFile
        }
    }

    // Bug real reportado por el usuario 2026-08-30 (backlog UX §9): esta
    // pantalla usaba un Scaffold+TopAppBar con su propio título y flecha
    // de volver, duplicando el título que ya muestra el banner azul justo
    // debajo -- se reemplaza por el mismo patrón de "Volver" integrado en
    // el banner que ya usan el resto de sub-pantallas.
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── AdMob — solo para usuarios free (backlog UX §8) ───────
            if (!isPremium) {
                item {
                    DocuSmartBannerAd(
                        adUnitId  = AdConstants.BANNER_SCAN_RESULT_ID,
                        adManager = converterViewModel.adManager
                    )
                }
            }

            // ── Banner ────────────────────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle = stringResource(R.string.scanner_result_title),
                    screenSubtitle = stringResource(R.string.scan_result_subtitle_pages, scannedUris.size),
                    onBack = onBack
                )
            }

            // ── Vista previa ──────────────────────────
            if (!isPdf && editableUris.isNotEmpty()) {
                item {
                    ScanPreviewSection(
                        uris = editableUris,
                        onEditPage = { index -> editingIndex = index }
                    )
                }
            }

            // ── Nombre del archivo ────────────────────
            item {
                ScanFilenameField(fileName = fileName, onFileNameChange = { fileName = it })
            }

            // ── Botones ───────────────────────────────
            item {
                ScanResultActions(
                    state = ScanResultActionsState(
                        isPdf = isPdf,
                        scannedUris = scannedUris,
                        hasResult = isPdf || conversionResult is ConversionResult.Success,
                        isConverting = uiState.isConverting,
                        fileName = fileName,
                        defaultNameTemplate = defaultNameTemplate,
                        shareChooserTitle = shareChooserTitle,
                        savedFile = savedFile,
                        savedToDownloads = savedToDownloads,
                        isPreparingShare = isPreparingShare
                    ),
                    onSavedToDownloadsChange = { savedToDownloads = it },
                    onPreparingShareChange = { isPreparingShare = it },
                    onGeneratePdf = {
                        val customName = fileName.trim().ifBlank { null }
                        if (customName != null) {
                            converterViewModel.onFileNameChange(customName)
                        }
                        converterViewModel.convertToPdf(context)
                    },
                    onScanAgain = {
                        converterViewModel.clearAll()
                        onBack()
                    },
                    onDone = {
                        converterViewModel.clearAll()
                        onDone()
                    }
                )
            }
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

// Extraído de ScanResultScreen (LongMethod de detekt) -- campo de nombre
// del archivo antes de guardar/generar el PDF.
@Composable
private fun ScanFilenameField(fileName: String, onFileNameChange: (String) -> Unit) {
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
                    text = ".pdf",
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
    val isPreparingShare: Boolean
)

// Extraído de ScanResultScreen (LongMethod de detekt) -- guardar/compartir/
// generar PDF, según el estado actual del resultado del escaneo.
@Composable
private fun ScanResultActions(
    state: ScanResultActionsState,
    onSavedToDownloadsChange: (Boolean) -> Unit,
    onPreparingShareChange: (Boolean) -> Unit,
    onGeneratePdf: () -> Unit,
    onScanAgain: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    onClick = {
                        scope.launch {
                            val name = state.fileName.ifBlank {
                                String.format(state.defaultNameTemplate, generateTimestamp())
                            }
                            val success = when {
                                state.savedFile != null -> saveFileToDownloads(context, state.savedFile)
                                state.isPdf -> savePdfUriToDownloads(context, state.scannedUris.first(), name)
                                else -> false
                            }
                            onSavedToDownloadsChange(success)
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
                    text = stringResource(R.string.scanner_share),
                    onClick = {
                        scope.launch {
                            onPreparingShareChange(true)
                            try {
                                shareScanResult(context, state)
                            } finally {
                                onPreparingShareChange(false)
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
                        text = stringResource(R.string.scan_result_generating_pdf),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            DocuSmartPrimaryButton(
                text = stringResource(R.string.scanner_generate),
                onClick = onGeneratePdf,
                leadingIcon = Icons.Rounded.PictureAsPdf
            )
            DocuSmartSecondaryButton(
                text = stringResource(R.string.scanner_again),
                onClick = onScanAgain,
                leadingIcon = Icons.Rounded.DocumentScanner
            )
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
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(2.dp)
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

@Composable
private fun ScanImageEditorDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onApply: (brightness: Int, contrast: Int, scalePercent: Int) -> Unit
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var scalePercent by remember { mutableIntStateOf(100) }

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
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix(buildColorMatrix(brightness.toInt(), contrast.toInt()))
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.scan_edit_brightness, brightness.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -100f..100f
                )
                Text(
                    text = stringResource(R.string.scan_edit_contrast, contrast.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = -100f..100f
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.scan_edit_scale),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCAN_EDIT_SCALE_OPTIONS.forEach { percent ->
                        FilterChip(
                            selected = scalePercent == percent,
                            onClick = { scalePercent = percent },
                            label = { Text("$percent%") }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.general_cancel))
                    }
                    Button(
                        onClick = { onApply(brightness.toInt(), contrast.toInt(), scalePercent) },
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
            type = MIME_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        Timber.d("shareFile: compartiendo ${file.name}")
    } catch (e: Exception) {
        Timber.e(e, "Error compartiendo archivo: ${e.message}")
    }
}

// ── Guardar File en Descargas ─────────────────────────
private fun saveFileToDownloads(context: Context, file: File): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, MIME_PDF)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            file.copyTo(File(dir, file.name), overwrite = true)
            true
        }
    } catch (e: Exception) {
        Timber.e(e, "Error guardando en Descargas")
        false
    }
}

// ── Guardar URI en Descargas ──────────────────────────
private suspend fun savePdfUriToDownloads(
    context: Context,
    uri: Uri,
    fileName: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.Downloads.MIME_TYPE, MIME_PDF)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val destUri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return@withContext false
            resolver.openInputStream(uri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
            true
        } else {
            // Pre-Q (API < 29): sin MediaStore.Downloads, se escribe directo
            // al directorio público de Descargas (requiere WRITE_EXTERNAL_STORAGE,
            // ya declarado para este rango de API).
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(dir, "$fileName.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            true
        }
    } catch (e: Exception) {
        Timber.e(e, "Error guardando URI en Descargas")
        false
    }
}

private fun generateTimestamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())