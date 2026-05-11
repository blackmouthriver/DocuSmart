package com.docsmart.features.converter.presentation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.*
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.presentation.components.ConversionProgress
import com.docsmart.features.converter.presentation.components.ConversionSuccess

@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onFilesSelected(uris)
    }

    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFilesSelected(listOf(it)) }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.conversionResult) {
        if (uiState.conversionResult is ConversionResult.Success) {
            activity?.let { viewModel.adManager.onConversionCompleted(it) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Banner ────────────────────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle = stringResource(R.string.converter_title),
                    screenSubtitle = stringResource(R.string.converter_subtitle)
                )
            }

            val result = uiState.conversionResult
            if (result is ConversionResult.Success) {
                item {
                    ConversionSuccess(
                        result = result,
                        savedToDownloads = uiState.savedToDownloads,
                        onConvertAnother = { viewModel.clearAll() },
                        onSaveToDownloads = { viewModel.saveToDownloads(context) }
                    )
                }
                return@LazyColumn
            }

            if (uiState.isConverting) {
                item {
                    ConversionProgress(totalImages = uiState.selectedFiles.size)
                }
                return@LazyColumn
            }

            if (uiState.selectedType == null) {
                item {
                    Text(
                        text = stringResource(R.string.converter_select),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    ConversionCategoryGrid(
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.onCategorySelected(it) }
                    )
                }
                item {
                    val types = uiState.filteredTypes
                    if (types.isNotEmpty()) {
                        ConversionTypeList(
                            types = types,
                            onTypeSelected = { viewModel.onTypeSelected(it) }
                        )
                    }
                }
            } else {
                item {
                    ConversionDetailCard(
                        type = uiState.selectedType!!,
                        selectedFiles = uiState.selectedFiles,
                        fileName = uiState.fileName,
                        onFileNameChange = { viewModel.onFileNameChange(it) },
                        onSelectFiles = {
                            val mime = getMimeForType(uiState.selectedType!!)
                            if (uiState.selectedType == ConversionType.IMAGE_TO_PDF) {
                                fileLauncher.launch(mime)
                            } else {
                                singleFileLauncher.launch(mime)
                            }
                        },
                        onConvert = { viewModel.convert() },
                        onBack = { viewModel.clearAll() }
                    )
                }
            }
        }
    }
}

// ── Grid de categorías ────────────────────────────────
@Composable
private fun ConversionCategoryGrid(
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit
) {
    // ── Usamos strings localizados como clave ─────────
    // La clave de categoría es el string en el idioma actual
    val imgLabel = stringResource(R.string.converter_category_image)
    val pdfLabel = stringResource(R.string.converter_category_pdf)
    val wordLabel = stringResource(R.string.converter_category_word)
    val excelLabel = stringResource(R.string.converter_category_excel)
    val pptLabel = stringResource(R.string.converter_category_ppt)

    val categories = listOf(
        Triple(imgLabel, Icons.Rounded.Image, ColorImage),
        Triple(pdfLabel, Icons.Rounded.PictureAsPdf, ColorPdf),
        Triple(wordLabel, Icons.Rounded.Description, ColorWord),
        Triple(excelLabel, Icons.Rounded.TableChart, ColorExcel),
        Triple(pptLabel, Icons.Rounded.Slideshow, ColorPowerPoint)
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(categories) { (label, icon, color) ->
            val isSelected = selectedCategory == label
            Card(
                modifier = Modifier
                    .width(90.dp)
                    .clickable { onCategorySelected(label) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        color.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    if (isSelected) 0.dp else 2.dp
                ),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(1.5.dp, color)
                else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) color
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) color
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Lista de tipos de conversión ──────────────────────
@Composable
private fun ConversionTypeList(
    types: List<ConversionType>,
    onTypeSelected: (ConversionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.converter_select_type),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        types.forEach { type ->
            ConversionTypeCard(
                type = type,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}

// ── Card de tipo de conversión ────────────────────────
@Composable
private fun ConversionTypeCard(
    type: ConversionType,
    onClick: () -> Unit
) {
    val (fromColor, fromIcon) = getFormatStyle(type.fromFormat)
    val (toColor, toIcon) = getFormatStyle(type.toFormat)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        fromColor.copy(alpha = 0.15f),
                        MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fromIcon,
                    contentDescription = null,
                    tint = fromColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        toColor.copy(alpha = 0.15f),
                        MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = toIcon,
                    contentDescription = null,
                    tint = toColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${type.fromFormat} → ${type.toFormat}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Detalle de conversión ─────────────────────────────
@Composable
private fun ConversionDetailCard(
    type: ConversionType,
    selectedFiles: List<Uri>,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectFiles: () -> Unit,
    onConvert: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.general_back_action),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFiles() },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
                    .copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (selectedFiles.isEmpty())
                        stringResource(R.string.converter_select_files, type.fromFormat)
                    else
                        stringResource(R.string.converter_files_selected, selectedFiles.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedFiles.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(
                        R.string.converter_formats,
                        type.fromExtensions.joinToString(", ").uppercase()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectedFiles.isNotEmpty()) {
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.converter_file_name_label)) },
                placeholder = { Text(stringResource(R.string.converter_file_name_placeholder)) },
                trailingIcon = {
                    Text(
                        text = ".${type.outputExtension}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Button(
                onClick = onConvert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.converter_to_format, type.toFormat),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────
private fun getFormatStyle(format: String): Pair<Color, ImageVector> {
    return when (format.lowercase()) {
        "pdf" -> Pair(ColorPdf, Icons.Rounded.PictureAsPdf)
        "imagen", "image", "jpg", "png" -> Pair(ColorImage, Icons.Rounded.Image)
        "word" -> Pair(ColorWord, Icons.Rounded.Description)
        "excel" -> Pair(ColorExcel, Icons.Rounded.TableChart)
        "powerpoint" -> Pair(ColorPowerPoint, Icons.Rounded.Slideshow)
        "txt", "texto", "text" -> Pair(ColorText, Icons.Rounded.TextSnippet)
        "csv" -> Pair(ColorExcel, Icons.Rounded.GridOn)
        else -> Pair(ColorText, Icons.Rounded.InsertDriveFile)
    }
}

private fun getMimeForType(type: ConversionType): String {
    return when (type) {
        ConversionType.IMAGE_TO_PDF,
        ConversionType.IMAGE_TO_JPG,
        ConversionType.IMAGE_TO_PNG -> "image/*"

        ConversionType.PDF_TO_IMAGE,
        ConversionType.PDF_TO_TXT -> "application/pdf"

        ConversionType.WORD_TO_PDF,
        ConversionType.WORD_TO_TXT -> "application/msword"

        ConversionType.EXCEL_TO_PDF,
        ConversionType.EXCEL_TO_CSV -> "application/vnd.ms-excel"

        ConversionType.PPT_TO_PDF -> "application/vnd.ms-powerpoint"
    }
}