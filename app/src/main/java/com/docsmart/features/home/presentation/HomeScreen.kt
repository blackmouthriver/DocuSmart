package com.docsmart.features.home.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.features.home.presentation.component.HomeBanner
import com.docsmart.features.home.presentation.component.QuickAccessGrid
import com.docsmart.features.home.presentation.component.RecentDocuments
import timber.log.Timber

@Composable
fun HomeScreen(
    onOpenFile     : (Uri) -> Unit = {},
    onConvert      : () -> Unit = {},
    onScan         : () -> Unit = {},
    onSecurity     : () -> Unit = {},
    onStudy        : (Int) -> Unit = {},  // ← tab inicial: Lectura=0, Notas=1, Pomodoro=2
    onDocumentClick: (String) -> Unit = {},
    onSeeAll       : () -> Unit = {},
    onQr           : () -> Unit = {},
    onQrReader     : () -> Unit = {},
    onQrCreator    : () -> Unit = {},
    onTrash        : () -> Unit = {},  // ← NUEVO: acceso rápido a la papelera
    viewModel      : HomeViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen: iniciando composición")

    LaunchedEffect(Unit) {
        viewModel.loadRecentDocuments()
    }

    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val context    = LocalContext.current

    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        Timber.d("filePicker: uri recibida = $uri")
        if (uri != null) onOpenFile(uri)
    }

    val openFileLauncher = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        filePicker.launch(intent)
    }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (!isPremium) {
            item {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_HOME_ID,
                    adManager = viewModel.adManager,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        item {
            HomeBanner(
                onOpenFileClick = openFileLauncher,
                onConvertClick  = onConvert,
                modifier        = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            QuickAccessGrid(
                onScanClick       = onScan,
                onImageToPdfClick = onConvert,
                onSafeBoxClick    = onSecurity,
                onStudyModeClick  = { onStudy(0) },
                onNotesClick      = { onStudy(1) },
                onPomodoroClick   = { onStudy(2) },
                onQrClick         = onQr,
                onQrReaderClick   = onQrReader,
                onQrCreatorClick  = onQrCreator,
                onTrashClick      = onTrash,
                modifier          = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            RecentDocuments(
                documents       = uiState.recentDocuments,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onSeeAllClick   = onSeeAll,
                onOpenFileClick = openFileLauncher,
                onRenameClick   = { id, newName -> viewModel.renameDocument(id, newName) },
                onConvertClick  = { doc -> onConvert() },
                onDeleteClick   = { id -> viewModel.removeDocument(id) },
                modifier        = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}