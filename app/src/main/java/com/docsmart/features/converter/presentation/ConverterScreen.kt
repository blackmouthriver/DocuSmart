package com.docsmart.features.converter.presentation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
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
    val uiState           = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val context           = LocalContext.current
    val activity          = context as? Activity

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) viewModel.onFilesSelected(uris) }

    val singleFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onFilesSelected(listOf(it)) } }

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

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Banner azul ───────────────────────────────────────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle    = stringResource(R.string.converter_title),
                    screenSubtitle = stringResource(R.string.converter_subtitle),
                    modifier       = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }

            // ── Banner AdMob ──────────────────────────────────────────────────
            item {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_CONVERTER_ID,
                    adManager = viewModel.adManager,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )
            }

            // ── Éxito ─────────────────────────────────────────────────────────
            val result = uiState.conversionResult
            if (result is ConversionResult.Success) {
                item {
                    ConversionSuccess(
                        result            = result,
                        savedToDownloads  = uiState.savedToDownloads,
                        onConvertAnother  = { viewModel.clearAll() },
                        onSaveToDownloads = { viewModel.saveToDownloads(context) },
                        modifier          = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            // ── Progreso ──────────────────────────────────────────────────────
            if (uiState.isConverting) {
                item {
                    ConversionProgress(
                        totalImages = uiState.selectedFiles.size,
                        modifier    = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            // ── Detalle de conversión seleccionada ────────────────────────────
            if (uiState.selectedType != null) {
                item {
                    ConversionDetailCard(
                        type             = uiState.selectedType!!,
                        selectedFiles    = uiState.selectedFiles,
                        fileName         = uiState.fileName,
                        onFileNameChange = { viewModel.onFileNameChange(it) },
                        onSelectFiles    = {
                            val mime = getMimeForType(uiState.selectedType!!)
                            if (uiState.selectedType == ConversionType.IMAGE_TO_PDF)
                                fileLauncher.launch(mime)
                            else
                                singleFileLauncher.launch(mime)
                        },
                        onConvert = { viewModel.convert() },
                        onBack    = { viewModel.clearAll() },
                        modifier  = Modifier.padding(horizontal = 20.dp)
                    )
                }
                return@LazyColumn
            }

            // ── Título grilla ─────────────────────────────────────────────────
            item {
                Text(
                    text       = stringResource(R.string.converter_select),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // ── Secciones por categoría ───────────────────────────────────────
            val allTypes = ConversionType.entries.toList()

            val imageTypes = allTypes.filter { it.getCategoryForUi() == "Imagen" }
            if (imageTypes.isNotEmpty()) {
                item {
                    ConversionSection(
                        title          = "Imagen",
                        icon           = Icons.Rounded.Image,
                        color          = ColorImage,
                        types          = imageTypes,
                        onTypeSelected = { viewModel.onTypeSelected(it) }
                    )
                }
            }

            val pdfTypes = allTypes.filter { it.getCategoryForUi() == "PDF" }
            if (pdfTypes.isNotEmpty()) {
                item {
                    ConversionSection(
                        title          = "PDF",
                        icon           = Icons.Rounded.PictureAsPdf,
                        color          = ColorPdf,
                        types          = pdfTypes,
                        onTypeSelected = { viewModel.onTypeSelected(it) }
                    )
                }
            }

            val wordTypes = allTypes.filter { it.getCategoryForUi() == "Word" }
            if (wordTypes.isNotEmpty()) {
                item {
                    ConversionSection(
                        title          = "Word",
                        icon           = Icons.Rounded.Description,
                        color          = ColorWord,
                        types          = wordTypes,
                        onTypeSelected = { viewModel.onTypeSelected(it) }
                    )
                }
            }

            val excelTypes = allTypes.filter { it.getCategoryForUi() == "Excel" }
            if (excelTypes.isNotEmpty()) {
                item {
                    ConversionSection(
                        title          = "Excel",
                        icon           = Icons.Rounded.TableChart,
                        color          = ColorExcel,
                        types          = excelTypes,
                        onTypeSelected = { viewModel.onTypeSelected(it) }
                    )
                }
            }

            val pptTypes = allTypes.filter { it.getCategoryForUi() == "PowerPoint" }
            if (pptTypes.isNotEmpty()) {
                item {
                    ConversionSection(
                        title          = "PowerPoint",
                        icon           = Icons.Rounded.Slideshow,
                        color          = ColorPowerPoint,
                        types          = pptTypes,
                        onTypeSelected = { viewModel.onTypeSelected(it) }
                    )
                }
            }
        }
    }
}

// ── Sección por categoría con grilla 2 columnas ───────────────────────────────

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
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = color,
                    modifier           = Modifier.size(18.dp)
                )
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
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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

    Card(
        modifier  = modifier
            .height(110.dp)
            .clickable { onClick() },
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(14.dp),
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
                    Icon(
                        imageVector        = fromIcon,
                        contentDescription = null,
                        tint               = fromColor,
                        modifier           = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector        = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(14.dp)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(toColor.copy(alpha = 0.15f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = toIcon,
                        contentDescription = null,
                        tint               = toColor,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Text(
                    text       = type.label,
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1
                )
                Text(
                    text  = "${type.fromFormat} → ${type.toFormat}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Detalle de conversión ─────────────────────────────────────────────────────

@Composable
private fun ConversionDetailCard(
    type            : ConversionType,
    selectedFiles   : List<Uri>,
    fileName        : String,
    onFileNameChange: (String) -> Unit,
    onSelectFiles   : () -> Unit,
    onConvert       : () -> Unit,
    onBack          : () -> Unit,
    modifier        : Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.general_back_action),
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text       = type.label,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFiles() },
            shape  = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(40.dp)
                )
                Text(
                    text = if (selectedFiles.isEmpty())
                        stringResource(R.string.converter_select_files, type.fromFormat)
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

        if (selectedFiles.isNotEmpty()) {
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

            Button(
                onClick  = onConvert,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector        = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.converter_to_format, type.toFormat),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun ConversionType.getCategoryForUi(): String = when (this) {
    ConversionType.IMAGE_TO_PDF,
    ConversionType.IMAGE_TO_JPG,
    ConversionType.IMAGE_TO_PNG  -> "Imagen"
    ConversionType.PDF_TO_IMAGE,
    ConversionType.PDF_TO_TXT    -> "PDF"
    ConversionType.WORD_TO_PDF,
    ConversionType.WORD_TO_TXT   -> "Word"
    ConversionType.EXCEL_TO_PDF,
    ConversionType.EXCEL_TO_CSV  -> "Excel"
    ConversionType.PPT_TO_PDF    -> "PowerPoint"
}

private fun getFormatStyle(format: String): Pair<Color, ImageVector> = when (format.lowercase()) {
    "pdf"                           -> Pair(ColorPdf,       Icons.Rounded.PictureAsPdf)
    "imagen", "image", "jpg", "png" -> Pair(ColorImage,     Icons.Rounded.Image)
    "word"                          -> Pair(ColorWord,       Icons.Rounded.Description)
    "excel"                         -> Pair(ColorExcel,      Icons.Rounded.TableChart)
    "powerpoint"                    -> Pair(ColorPowerPoint, Icons.Rounded.Slideshow)
    "txt", "texto", "text"          -> Pair(ColorText,       Icons.Rounded.TextSnippet)
    "csv"                           -> Pair(ColorExcel,      Icons.Rounded.GridOn)
    else                            -> Pair(ColorText,       Icons.Rounded.InsertDriveFile)
}

private fun getMimeForType(type: ConversionType): String = when (type) {
    ConversionType.IMAGE_TO_PDF,
    ConversionType.IMAGE_TO_JPG,
    ConversionType.IMAGE_TO_PNG  -> "image/*"
    ConversionType.PDF_TO_IMAGE,
    ConversionType.PDF_TO_TXT    -> "application/pdf"
    ConversionType.WORD_TO_PDF,
    ConversionType.WORD_TO_TXT   -> "application/msword"
    ConversionType.EXCEL_TO_PDF,
    ConversionType.EXCEL_TO_CSV  -> "application/vnd.ms-excel"
    ConversionType.PPT_TO_PDF    -> "application/vnd.ms-powerpoint"
}