package com.docsmart.features.home.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    onOpenFile: (Uri) -> Unit = {},
    onConvert: () -> Unit = {},
    onScan: () -> Unit = {},
    onSecurity: () -> Unit = {},
    onStudy: () -> Unit = {},
    onDocumentClick: (String) -> Unit = {},
    onSeeAll: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen: iniciando composición")
    val context = LocalContext.current

// ── Recargar recientes al volver al Home ──────────────
    LaunchedEffect(Unit) {
        viewModel.loadRecentDocuments()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        Timber.d("filePicker: uri recibida = $uri")
        if (uri != null) {
            Timber.d("filePicker: llamando onOpenFile con $uri")
            onOpenFile(uri)
        } else {
            Timber.d("filePicker: uri es NULL, usuario canceló")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // ── Home usa su propio HomeBanner especial ─
            // con botones de acción integrados
            HomeBanner(
                onOpenFileClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    filePicker.launch(intent)
                },
                onConvertClick = onConvert,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            DocuSmartBannerAd(
                adUnitId = AdConstants.BANNER_HOME_ID,
                adManager = viewModel.adManager,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            QuickAccessGrid(
                onScanClick = onScan,
                onImageToPdfClick = onConvert,
                onSafeBoxClick = onSecurity,
                onStudyModeClick = onStudy,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        item {
            RecentDocuments(
                documents = uiState.recentDocuments,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onSeeAllClick = onSeeAll,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}