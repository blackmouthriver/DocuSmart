package com.docsmart.features.scanner.presentation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.buttons.DocuSmartPrimaryButton
import com.docsmart.core.ui.components.buttons.DocuSmartSecondaryButton
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.presentation.ConverterViewModel
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
    converterViewModel: ConverterViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by converterViewModel.uiState.collectAsState()

    var fileName by remember { mutableStateOf("") }
    var savedToDownloads by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var isPreparingShare by remember { mutableStateOf(false) }

    val defaultNameTemplate = stringResource(R.string.scan_result_default_name_prefix)
    val shareChooserTitle   = stringResource(R.string.scanner_share)

    // ── Inicializar según tipo de resultado ───────────
    LaunchedEffect(scannedUris, isPdf) {
        if (!isPdf) {
            converterViewModel.onImagesSelected(scannedUris)
        }
    }

    // ── Cuando se convierte a PDF, guardar referencia ─
    val conversionResult = uiState.conversionResult
    LaunchedEffect(conversionResult) {
        if (conversionResult is ConversionResult.Success) {
            savedFile = conversionResult.outputFile
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Banner ────────────────────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle = stringResource(R.string.scanner_result_title),
                    screenSubtitle = stringResource(R.string.scan_result_subtitle_pages, scannedUris.size)
                )
            }

            // ── Vista previa ──────────────────────────
            if (!isPdf && scannedUris.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.scan_result_preview),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(scannedUris) { index, uri ->
                            Card(
                                modifier = Modifier.size(120.dp, 160.dp),
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.scan_result_page_content_desc, index + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                )
                            }
                        }
                    }
                }
            }

            // ── Nombre del archivo ────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.scan_result_filename_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
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

            // ── Botones ───────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    val hasResult = isPdf || conversionResult is ConversionResult.Success

                    if (hasResult) {
                        // ── Guardado confirmado ───────
                        if (savedToDownloads) {
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
                            // ── Guardar en Descargas ──
                            DocuSmartPrimaryButton(
                                text = stringResource(R.string.converter_save),
                                onClick = {
                                    scope.launch {
                                        val name = fileName.ifBlank {
                                            String.format(defaultNameTemplate, generateTimestamp())
                                        }
                                        val success = when {
                                            savedFile != null ->
                                                saveFileToDownloads(context, savedFile!!)
                                            isPdf ->
                                                savePdfUriToDownloads(
                                                    context,
                                                    scannedUris.first(),
                                                    name
                                                )
                                            else -> false
                                        }
                                        savedToDownloads = success
                                    }
                                },
                                leadingIcon = Icons.Rounded.Download
                            )
                        }

                        // ── Compartir PDF ─────────────
                        if (isPreparingShare) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
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
                                        isPreparingShare = true
                                        try {
                                            when {
                                                // ── Compartir desde File ──────
                                                savedFile != null -> {
                                                    shareFile(context, savedFile!!, shareChooserTitle)
                                                }
                                                // ── Compartir URI del escáner ─
                                                // Primero copiar al cache para
                                                // evitar problemas de permisos
                                                isPdf -> {
                                                    val cacheFile = copyUriToCache(
                                                        context,
                                                        scannedUris.first(),
                                                        fileName.ifBlank {
                                                            String.format(defaultNameTemplate, generateTimestamp())
                                                        }
                                                    )
                                                    if (cacheFile != null) {
                                                        shareFile(context, cacheFile, shareChooserTitle)
                                                    } else {
                                                        Timber.e("No se pudo copiar PDF al cache")
                                                    }
                                                }
                                            }
                                        } finally {
                                            isPreparingShare = false
                                        }
                                    }
                                },
                                leadingIcon = Icons.Rounded.Share
                            )
                        }

                        TextButton(
                            onClick = {
                                converterViewModel.clearAll()
                                onDone()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.scanner_back))
                        }

                    } else {
                        // ── Generar PDF desde imágenes ─
                        if (uiState.isConverting) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary
                                    )
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
                                onClick = {
                                    val customName = fileName.trim().ifBlank { null }
                                    if (customName != null) {
                                        converterViewModel.onFileNameChange(customName)
                                    }
                                    converterViewModel.convertToPdf()
                                },
                                leadingIcon = Icons.Rounded.PictureAsPdf
                            )

                            DocuSmartSecondaryButton(
                                text = stringResource(R.string.scanner_again),
                                onClick = {
                                    converterViewModel.clearAll()
                                    onBack()
                                },
                                leadingIcon = Icons.Rounded.DocumentScanner
                            )
                        }
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