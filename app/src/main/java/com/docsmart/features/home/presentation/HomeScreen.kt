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
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.util.ReloadOnScreenResume
import com.docsmart.features.home.presentation.component.HomeBanner
import com.docsmart.features.home.presentation.component.QuickAccessGrid
import com.docsmart.features.home.presentation.component.RecentDocuments
import timber.log.Timber

@Composable
fun HomeScreen(
    onOpenFile: (Uri) -> Unit = {},
    onConvert: () -> Unit = {},
    // ← acceso rápido "Img→PDF": preselecciona Imagen→PDF
    onQuickConvertImageToPdf: () -> Unit = onConvert,
    onScan: () -> Unit = {},
    onSecurity: () -> Unit = {},
    // ← tab inicial: Lectura=0, Notas=1, Pomodoro=2
    onStudy: (Int) -> Unit = {},
    // ← acceso rápido a la Agenda (antes solo se llegaba desde Modo Estudio)
    onAgenda: () -> Unit = {},
    onDocumentClick: (String) -> Unit = {},
    onSeeAll: () -> Unit = {},
    onQrReader: () -> Unit = {},
    onQrCreator: () -> Unit = {},
    // ← NUEVO: acceso rápido a la papelera
    onTrash: () -> Unit = {},
    // Atajos desde el menú "⋮" de un documento en Recientes (backlog UX
    // 2026-08-30, HU-UX-01/02) -- por defecto caen al CTA genérico existente
    // si la pantalla llamante no pasa una versión que preseleccione el
    // archivo (mismo criterio ya usado en onQuickConvertImageToPdf).
    onConvertDocument: (DocumentUiModel) -> Unit = { onConvert() },
    onCreateQrFromDocument: (DocumentUiModel) -> Unit = { onQrCreator() },
    // Backlog UX 2026-08-30/09-10 (HU-42): sin CTA genérico al que caer --
    // a diferencia de Convertir/Crear QR, no tiene sentido abrir OCR/
    // Firmar/Carpeta Segura sin un archivo ya elegido, así que quedan
    // `null` (opción no visible en el menú) si la pantalla llamante no las
    // conecta.
    onMakeSearchableDocument: ((DocumentUiModel) -> Unit)? = null,
    onSignDocument: ((DocumentUiModel) -> Unit)? = null,
    onMoveToSecureFolderDocument: ((DocumentUiModel) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    Timber.d("HomeScreen: iniciando composición")

    // Único disparador de la carga inicial (ver HomeViewModel.kt -- ya no
    // carga en `init{}` para no duplicar esta misma llamada, hallazgo
    // G10 de la octava ronda). Se re-ejecuta en cada composición nueva de
    // esta pantalla, incluida la que sigue a un cambio de configuración
    // (rotación) -- a diferencia de un `init{}` de ViewModel, que no
    // vuelve a correr si el ViewModel sobrevive la recreación de la
    // Activity.
    LaunchedEffect(Unit) {
        viewModel.loadRecentDocuments()
    }

    // Hallazgo real de la revisión general 2026-09-16 (#52): "Recientes" en
    // Home solo se cargaba una vez, al componer por primera vez. Un
    // documento movido/borrado desde otra pantalla (Visor, Biblioteca,
    // Carpeta Segura) seguía apareciendo como "fantasma" al volver a Home.
    ReloadOnScreenResume {
        viewModel.loadRecentDocuments()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
    // Baja -- G9): un fallo real de lectura de Recientes se veía
    // exactamente igual que "sin documentos" -- mismo patrón que
    // deleteError de arriba, para no dejarlo pasar en silencio.
    LaunchedEffect(uiState.loadError) {
        uiState.loadError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissLoadError()
        }
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = result.data?.data
            Timber.d("filePicker: uri recibida = $uri")
            if (uri != null) onOpenFile(uri)
        }

    val openFileLauncher = {
        // Bug real reportado por el usuario 2026-09-11: un documento abierto
        // con este botón se podía ver en Biblioteca/Recientes pero "Eliminar"
        // no hacía nada -- el permiso pedido acá era solo de LECTURA, nunca
        // de escritura, así que DocumentsContract.deleteDocument() (llamado
        // desde DocumentRepository para cualquier Uri que no sea de
        // MediaStore) fallaba con SecurityException en silencio. Se agrega
        // FLAG_GRANT_WRITE_URI_PERMISSION para que el picker sí otorgue
        // permiso de escritura desde el principio.
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        filePicker.launch(intent)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Pedido explícito del usuario 2026-09-07: los banners de todas las
        // pantallas se veían de tamaños/separaciones distintas -- unificado
        // en DocuSmartScreenHeader (mismo margen horizontal, mismo espacio
        // entre el anuncio y el banner, sin hueco antes del anuncio).
        item {
            DocuSmartScreenHeader(
                adUnitId = AdConstants.BANNER_HOME_ID,
                adManager = viewModel.adManager,
            ) {
                HomeBanner(
                    onOpenFileClick = openFileLauncher,
                    onConvertClick = onConvert,
                )
            }
        }
        item {
            QuickAccessGrid(
                onScanClick = onScan,
                onImageToPdfClick = onQuickConvertImageToPdf,
                onSafeBoxClick = onSecurity,
                onStudyModeClick = { onStudy(0) },
                onStudyMenuClick = { onStudy(-1) },
                onNotesClick = { onStudy(1) },
                onPomodoroClick = { onStudy(2) },
                onAgendaClick = onAgenda,
                onQrReaderClick = onQrReader,
                onQrCreatorClick = onQrCreator,
                onTrashClick = onTrash,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            RecentDocuments(
                documents = uiState.recentDocuments,
                isLoading = uiState.isLoading,
                onDocumentClick = { doc -> onDocumentClick(doc.id) },
                onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
                onSeeAllClick = onSeeAll,
                onOpenFileClick = openFileLauncher,
                onRenameClick = { id, newName -> viewModel.renameDocument(id, newName) },
                onConvertClick = onConvertDocument,
                onCreateQrClick = onCreateQrFromDocument,
                onMakeSearchableClick = onMakeSearchableDocument,
                onSignClick = onSignDocument,
                onMoveToSecureFolderClick = onMoveToSecureFolderDocument,
                onDeleteClick = { id -> viewModel.removeDocument(id) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
