package com.docsmart.features.pdftools.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.components.DailyLimitDialog
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.docsmart.features.pdftools.domain.usecase.CompressPdfMessages
import com.docsmart.features.pdftools.domain.usecase.MergePdfMessages
import com.docsmart.features.pdftools.domain.usecase.NumberPagesMessages
import com.docsmart.features.pdftools.domain.usecase.RotatePdfMessages
import com.docsmart.features.pdftools.domain.usecase.SplitPdfMessages
import com.docsmart.features.pdftools.presentation.components.CompressPdfScreen
import com.docsmart.features.pdftools.presentation.components.MergePdfScreen
import com.docsmart.features.pdftools.presentation.components.NumberPagesScreen
import com.docsmart.features.pdftools.presentation.components.OutputFileNameField
import com.docsmart.features.pdftools.presentation.components.PdfToolsMenu
import com.docsmart.features.pdftools.presentation.components.RotatePdfScreen
import com.docsmart.features.pdftools.presentation.components.SplitPdfScreen
import com.docsmart.R

private const val MIME_PDF = "application/pdf"

@Composable
fun PdfToolsScreen(
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium       by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val isRewardedReady by viewModel.adManager.isRewardedReady.collectAsStateWithLifecycle()
    val context    = LocalContext.current
    val activity   = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val adNotAvailableMessage = stringResource(R.string.pdf_tools_ad_not_available)
    val shareChooserTitle = stringResource(R.string.pdf_tools_share)
    val shareErrorMessage = stringResource(R.string.pdf_tools_share_error)
    val saveErrorMessage  = stringResource(R.string.pdf_tools_save_error)
    // ── Mensajes de los 4 use cases, resueltos acá (stringResource() no puede
    // llamarse dentro de remember{}, @DisallowComposableCalls) y empaquetados
    // sin recalcular en cada recomposición ──────────────────────────────────
    val mergeMinPdfsError    = stringResource(R.string.pdf_merge_min_pdfs_error)
    val mergeReadError       = stringResource(R.string.pdf_merge_read_error)
    val mergeGenerateError   = stringResource(R.string.pdf_merge_generate_error)
    val mergeSuccess         = stringResource(R.string.pdf_merge_success)
    val mergeGenericError    = stringResource(R.string.pdf_merge_error)
    val splitReadError       = stringResource(R.string.pdf_split_read_error)
    val splitNoPages         = stringResource(R.string.pdf_split_no_pages)
    val splitRangeTooSmall   = stringResource(R.string.pdf_split_range_too_small)
    val splitGenerateError   = stringResource(R.string.pdf_split_generate_error)
    val splitSuccess         = stringResource(R.string.pdf_split_success)
    val splitGenericError    = stringResource(R.string.pdf_split_error)
    val compressReadError        = stringResource(R.string.pdf_compress_read_error)
    val compressEmptyFile        = stringResource(R.string.pdf_compress_empty_file)
    val compressNoPages          = stringResource(R.string.pdf_compress_no_pages)
    val compressGenerateError    = stringResource(R.string.pdf_compress_generate_error)
    val compressAlreadyOptimized = stringResource(R.string.pdf_compress_already_optimized)
    val compressSuccess          = stringResource(R.string.pdf_compress_success)
    val compressGenericError     = stringResource(R.string.pdf_compress_error)
    val rotateReadError      = stringResource(R.string.pdf_rotate_read_error)
    val rotateGenerateError  = stringResource(R.string.pdf_rotate_generate_error)
    val rotateSuccess        = stringResource(R.string.pdf_rotate_success)
    val rotateGenericError   = stringResource(R.string.pdf_rotate_error)
    val numberPagesReadError        = stringResource(R.string.pdf_number_pages_read_error)
    val numberPagesNoPages          = stringResource(R.string.pdf_number_pages_no_pages)
    val numberPagesGenerateError    = stringResource(R.string.pdf_number_pages_generate_error)
    val numberPagesSuccess          = stringResource(R.string.pdf_number_pages_success)
    val numberPagesGenericError     = stringResource(R.string.pdf_number_pages_error)
    val numberPagesTemplate         = stringResource(R.string.pdf_number_pages_footer_template)

    val pdfToolMessages = remember {
        PdfToolMessages(
            merge = MergePdfMessages(
                minPdfsError  = mergeMinPdfsError,
                readError     = mergeReadError,
                generateError = mergeGenerateError,
                success       = mergeSuccess,
                genericError  = mergeGenericError
            ),
            split = SplitPdfMessages(
                readError     = splitReadError,
                noPages       = splitNoPages,
                rangeTooSmall = splitRangeTooSmall,
                generateError = splitGenerateError,
                success       = splitSuccess,
                genericError  = splitGenericError
            ),
            compress = CompressPdfMessages(
                readError        = compressReadError,
                emptyFile        = compressEmptyFile,
                noPages          = compressNoPages,
                generateError    = compressGenerateError,
                alreadyOptimized = compressAlreadyOptimized,
                success          = compressSuccess,
                genericError     = compressGenericError
            ),
            rotate = RotatePdfMessages(
                readError     = rotateReadError,
                generateError = rotateGenerateError,
                success       = rotateSuccess,
                genericError  = rotateGenericError
            ),
            numberPages = NumberPagesMessages(
                readError           = numberPagesReadError,
                noPages             = numberPagesNoPages,
                generateError       = numberPagesGenerateError,
                success             = numberPagesSuccess,
                genericError        = numberPagesGenericError,
                pageOfTotalTemplate = numberPagesTemplate
            )
        )
    }

    val multiPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addPdfsToMerge(uris)
    }

    val singlePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onPdfsSelected(listOf(it)) }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // ── Dialog de límite diario ────────────────────────
    if (uiState.showLimitDialog) {
        DailyLimitDialog(
            usedCount       = uiState.toolUseCount,
            limit           = uiState.toolUseLimit,
            itemLabelPlural = stringResource(R.string.pdf_tools_daily_limit_label),
            isRewardedReady = isRewardedReady,
            onWatchAd       = {
                activity?.let { viewModel.watchAdForTool(it, adNotAvailableMessage) }
            },
            onDismiss       = { viewModel.dismissLimitDialog() },
            onGetPremium    = { }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Banner AdMob — solo para usuarios free ─
            if (!isPremium) {
                item {
                    DocuSmartBannerAd(
                        adUnitId  = AdConstants.BANNER_TOOLS_ID,
                        adManager = viewModel.adManager,
                        modifier  = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // ── Banner azul con logo ───────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle    = stringResource(R.string.pdf_tools_title),
                    screenSubtitle = stringResource(R.string.pdf_tools_subtitle),
                    modifier       = Modifier.padding(
                        horizontal = 20.dp,
                        vertical   = 24.dp
                    )
                )
            }

            // ── Menú principal ────────────────────────
            if (uiState.selectedTool == PdfTool.NONE) {
                item {
                    PdfToolsMenu(
                        onToolSelected = { viewModel.selectTool(it) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // ── Herramienta activa ────────────────────
            if (uiState.selectedTool != PdfTool.NONE) {
                item {
                    TextButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBackIosNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pdf_tools_back_to_tools),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // ── Contador de usos diarios (usuarios free) ──
                if (!isPremium && uiState.toolUseCount > 0) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.pdf_tools_usage_today,
                                uiState.toolUseCount, uiState.toolUseLimit
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.toolUseCount >= uiState.toolUseLimit)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                val result = uiState.result
                if (result is PdfToolResult.Success) {
                    item {
                        ToolSuccessCard(
                            result = result,
                            savedToDownloads = uiState.savedToDownloads,
                            onShareClick = {
                                viewModel.shareResult(context, shareChooserTitle, shareErrorMessage)
                            },
                            onSaveClick = { viewModel.saveToDownloads(context, saveErrorMessage) },
                            onNewOperation = { viewModel.reset() },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                if (result !is PdfToolResult.Success) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            when (uiState.selectedTool) {
                                PdfTool.MERGE -> MergePdfScreen(
                                    selectedPdfs = uiState.selectedPdfs,
                                    isProcessing = uiState.isProcessing,
                                    fileName = uiState.outputFileName,
                                    onFileNameChange = {
                                        viewModel.onOutputFileNameChange(it)
                                    },
                                    onSelectPdfs = {
                                        multiPdfLauncher.launch(MIME_PDF)
                                    },
                                    onRemovePdf = { viewModel.removePdf(it) },
                                    onExecute = { viewModel.execute(pdfToolMessages) }
                                )
                                PdfTool.SPLIT -> SplitPdfScreen(
                                    selectedPdf = uiState.selectedPdfs.firstOrNull(),
                                    fromPage = uiState.splitFromPage,
                                    toPage = uiState.splitToPage,
                                    isProcessing = uiState.isProcessing,
                                    fileName = uiState.outputFileName,
                                    onFileNameChange = {
                                        viewModel.onOutputFileNameChange(it)
                                    },
                                    onSelectPdf = {
                                        singlePdfLauncher.launch(MIME_PDF)
                                    },
                                    onFromPageChange = {
                                        viewModel.onSplitFromPageChange(it)
                                    },
                                    onToPageChange = {
                                        viewModel.onSplitToPageChange(it)
                                    },
                                    onExecute = { viewModel.execute(pdfToolMessages) }
                                )
                                PdfTool.COMPRESS -> CompressPdfScreen(
                                    selectedPdf = uiState.selectedPdfs.firstOrNull(),
                                    quality = uiState.compressionQuality,
                                    isProcessing = uiState.isProcessing,
                                    fileName = uiState.outputFileName,
                                    onFileNameChange = {
                                        viewModel.onOutputFileNameChange(it)
                                    },
                                    onSelectPdf = {
                                        singlePdfLauncher.launch(MIME_PDF)
                                    },
                                    onQualityChange = {
                                        viewModel.onCompressionQualityChange(it)
                                    },
                                    onExecute = { viewModel.execute(pdfToolMessages) }
                                )
                                PdfTool.ROTATE -> RotatePdfScreen(
                                    selectedPdf = uiState.selectedPdfs.firstOrNull(),
                                    degrees = uiState.rotationDegrees,
                                    isProcessing = uiState.isProcessing,
                                    fileName = uiState.outputFileName,
                                    onFileNameChange = {
                                        viewModel.onOutputFileNameChange(it)
                                    },
                                    onSelectPdf = {
                                        singlePdfLauncher.launch(MIME_PDF)
                                    },
                                    onDegreesChange = {
                                        viewModel.onRotationDegreesChange(it)
                                    },
                                    onExecute = { viewModel.execute(pdfToolMessages) }
                                )
                                PdfTool.NUMBER_PAGES -> NumberPagesScreen(
                                    selectedPdf = uiState.selectedPdfs.firstOrNull(),
                                    format = uiState.pageNumberFormat,
                                    isProcessing = uiState.isProcessing,
                                    fileName = uiState.outputFileName,
                                    onFileNameChange = {
                                        viewModel.onOutputFileNameChange(it)
                                    },
                                    onSelectPdf = {
                                        singlePdfLauncher.launch(MIME_PDF)
                                    },
                                    onFormatChange = {
                                        viewModel.onPageNumberFormatChange(it)
                                    },
                                    onExecute = { viewModel.execute(pdfToolMessages) }
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Card de resultado exitoso ─────────────────────────
@Composable
private fun ToolSuccessCard(
    result: PdfToolResult.Success,
    savedToDownloads: Boolean,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNewOperation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = result.message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.outputFile.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${result.outputFile.length() / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!savedToDownloads) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pdf_tools_save_to_downloads),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pdf_tools_share),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                TextButton(
                    onClick = onNewOperation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pdf_tools_new_operation),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}