package com.docsmart.features.viewer.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.pdf.PdfPageBitmap
import com.docsmart.core.pdf.renderPdfPagesToBitmaps
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.features.converter.domain.usecase.WordFileFormat
import com.docsmart.features.converter.domain.usecase.detectWordFormat
import com.docsmart.features.converter.domain.usecase.extractLegacyDocBlocks
import com.docsmart.features.converter.domain.usecase.isHeadingStyleName
import com.docsmart.features.viewer.domain.annotation.PdfRectPts
import com.docsmart.features.viewer.domain.annotation.isValidHighlightSize
import com.docsmart.features.viewer.domain.annotation.screenDragToPdfRect
import com.docsmart.features.viewer.domain.annotation.screenPointToPdfPoint
import com.docsmart.features.viewer.domain.annotation.pdfPointToScreenPoint
import com.docsmart.features.viewer.domain.usecase.PdfMatchRect
import com.docsmart.features.viewer.presentation.components.ViewerAnnotationDetailDialog
import com.docsmart.features.viewer.presentation.components.ViewerAnnotationToolbar
import com.docsmart.features.viewer.presentation.components.ViewerBottomBar
import com.docsmart.features.viewer.presentation.components.ViewerDeleteConfirmDialog
import com.docsmart.features.viewer.presentation.components.ViewerNoteInputDialog
import com.docsmart.features.viewer.presentation.components.ViewerRenameDialog
import com.docsmart.features.viewer.presentation.components.ViewerShareChoiceDialog
import com.docsmart.features.viewer.presentation.components.ViewerTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipInputStream

@Composable
fun ViewerScreen(
    documentId: String,
    onBack    : () -> Unit,
    // Atajos "Convertir"/"Crear QR" desde el menú del Visor (backlog UX
    // 2026-08-30, HU-UX-01/02, AC5) -- reciben el documento actualmente
    // abierto para poder precargarlo en la pantalla de destino.
    onConvertClick : (DocumentUiModel) -> Unit = {},
    onCreateQrClick: (DocumentUiModel) -> Unit = {},
    onMakeSearchableClick   : (DocumentUiModel) -> Unit = {},
    onSignClick             : (DocumentUiModel) -> Unit = {},
    onMoveToSecureFolderClick: (DocumentUiModel) -> Unit = {},
    viewModel : ViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    var showSearch    by remember { mutableStateOf(false) }
    var searchQuery   by remember { mutableStateOf("") }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId, context)
    }

    // ── RF-VIS-06: eliminar exitosamente cierra el Visor ─────────────────────
    LaunchedEffect(uiState.documentDeleted) {
        if (uiState.documentDeleted) onBack()
    }

    // ── RF-VIS-06: aviso transitorio si no se pudo eliminar ──────────────────
    LaunchedEffect(uiState.deleteError) {
        uiState.deleteError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissDeleteError()
        }
    }

    // HU-46: aviso transitorio si falla compartir -- mismo criterio que
    // deleteError arriba, no el `error` de nivel superior (ese reemplaza
    // toda la vista del documento por una pantalla de "documento roto";
    // un fallo al compartir no debería expulsar al usuario del documento).
    LaunchedEffect(uiState.shareError) {
        uiState.shareError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissShareError()
        }
    }

    // ── RF-VIS-06: diálogos de renombrar/eliminar ────────────────────────────
    if (uiState.showRenameDialog) {
        ViewerRenameDialog(
            currentName = uiState.document?.name ?: "",
            onConfirm   = { newName -> viewModel.renameDocument(newName) },
            onDismiss   = { viewModel.dismissRenameDialog() }
        )
    }
    if (uiState.showDeleteConfirm) {
        ViewerDeleteConfirmDialog(
            fileName  = uiState.document?.name ?: "",
            onConfirm = { viewModel.confirmDelete(context) },
            onDismiss = { viewModel.dismissDeleteConfirm() }
        )
    }

    // ── HU-46: anotaciones (resaltado + notas adhesivas) ──────────────────────
    if (uiState.pendingNoteAnchor != null) {
        ViewerNoteInputDialog(
            onConfirm = { text -> viewModel.confirmNote(text) },
            onDismiss = { viewModel.cancelPendingNote() }
        )
    }
    uiState.viewingAnnotation?.let { annotation ->
        ViewerAnnotationDetailDialog(
            annotation = annotation,
            onDelete   = { viewModel.deleteAnnotation(annotation.id) },
            onDismiss  = { viewModel.dismissAnnotationDetail() }
        )
    }
    if (uiState.showShareChoiceDialog) {
        ViewerShareChoiceDialog(
            isFlattening           = uiState.isFlatteningForShare,
            onShareWithAnnotations = { viewModel.shareWithAnnotations(context) },
            onShareOriginal        = { viewModel.shareOriginal(context) },
            onDismiss               = { viewModel.dismissShareChoiceDialog() }
        )
    }

    // ── Dialog de contraseña PDF ──────────────────────────────────────────────
    if (uiState.requiresPassword) {
        PdfPasswordDialog(
            fileName      = uiState.document?.name ?: "Documento",
            passwordError = uiState.passwordError,
            isLoading     = uiState.isLoading,
            onConfirm     = { password -> viewModel.unlockPdfWithPassword(password) },
            onDismiss     = {
                viewModel.dismissPasswordDialog()
                // Usar onBack — el NavGraph decide si popBackStack o finish()
                onBack()
            }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.requiresPassword -> {
                // No mostrar nada mientras se pide contraseña — el dialog ya se muestra arriba
            }
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = MaterialTheme.colorScheme.primary
                )
            }
            uiState.error != null -> {
                Column(
                    modifier            = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        modifier           = Modifier.size(64.dp),
                        tint               = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text  = uiState.error ?: stringResource(R.string.viewer_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.viewer_back))
                    }
                }
            }
            uiState.document != null -> {
                val fileUri  = uiState.fileUri
                val mime     = (uiState.mimeType ?: "").lowercase()
                val fileName = (uiState.document?.name ?: "").lowercase()
                android.util.Log.d("ViewerScreen", "document!=null fileUri=$fileUri mime=$mime fileName=$fileName requiresPassword=${uiState.requiresPassword} error=${uiState.error}")

                when {
                    mime.contains("image") ||
                            fileName.endsWith(".jpg")  || fileName.endsWith(".jpeg") ||
                            fileName.endsWith(".png")  || fileName.endsWith(".webp") ||
                            fileName.endsWith(".gif")  -> {
                        ImageViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("pdf") || fileName.endsWith(".pdf") -> {
                        // key fuerza recrear el composable cuando cambia la URI (ej: después de desencriptar)
                        key(fileUri?.toString()) {
                            PdfViewerContent(
                                uri           = fileUri,
                                targetPage    = uiState.pdfSearchMatches
                                    .getOrNull(uiState.pdfSearchIndex)
                                    ?.minus(1),
                                highlights           = uiState.pdfSearchHighlights,
                                onPageChanged        = { page, total -> viewModel.onPageChanged(page, total) },
                                onTap                = { viewModel.toggleControls() },
                                annotationMode       = uiState.annotationMode,
                                selectedHighlightColor = uiState.selectedHighlightColor,
                                documentAnnotations  = uiState.annotations,
                                onHighlightDrawn     = { page, rect -> viewModel.addHighlight(page, rect) },
                                onNoteRequested       = { page, anchor -> viewModel.requestAddNote(page, anchor) },
                                onAnnotationTap       = { annotation -> viewModel.viewAnnotation(annotation) }
                            )
                        }
                    }
                    mime.contains("word") || mime.contains("msword") ||
                            mime.contains("wordprocessingml") ||
                            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> {
                        WordViewerContent(
                            uri         = fileUri,
                            searchQuery = searchQuery,
                            onTap       = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("excel") || mime.contains("spreadsheet") ||
                            mime.contains("ms-excel") || mime.contains("sheet") ||
                            fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> {
                        ExcelViewerContent(
                            uri         = fileUri,
                            searchQuery = searchQuery,
                            onTap       = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("powerpoint") || mime.contains("presentation") ||
                            fileName.endsWith(".ppt") || fileName.endsWith(".pptx") -> {
                        PptViewerContent(
                            uri         = fileUri,
                            searchQuery = searchQuery,
                            onTap       = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("text") ||
                            fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                            fileName.endsWith(".csv") -> {
                        TextViewerContent(
                            uri         = fileUri,
                            searchQuery = searchQuery,
                            onTap       = { viewModel.toggleControls() }
                        )
                    }
                    else -> {
                        UnsupportedFormatContent(
                            mimeType = mime,
                            fileName = uiState.document?.name ?: "",
                            fileUri  = fileUri,
                            onTap    = { viewModel.toggleControls() }
                        )
                    }
                }
            }
        }

        // ── TopBar + SearchBar (extraído a ViewerTopBarSection -- LongMethod
        // de detekt tras agregar los accesos de HU-42) ───────────────────────
        uiState.document?.let { doc ->
            ViewerTopBarSection(
                doc     = doc,
                uiState = uiState,
                onBack  = onBack,
                viewModel = viewModel,
                search  = ViewerSearchState(
                    query          = searchQuery,
                    active         = showSearch,
                    onQueryChange  = { searchQuery = it },
                    onActiveChange = { showSearch = it }
                ),
                documentActions = ViewerDocumentActions(
                    onConvert            = onConvertClick,
                    onCreateQr           = onCreateQrClick,
                    onMakeSearchable     = onMakeSearchableClick,
                    onSign               = onSignClick,
                    onMoveToSecureFolder = onMoveToSecureFolderClick
                )
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // ── AdMob — solo para usuarios free (backlog UX §8), oculto/
            // visible junto con el resto de los controles del Visor en vez
            // de fijo (rompería el modo de lectura inmersiva) ────────────
            if (!isPremium && uiState.showControls) {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_VIEWER_ID,
                    adManager = viewModel.adManager
                )
            }
            ViewerBottomBar(
                currentPage = uiState.currentPage,
                totalPages  = uiState.totalPages,
                visible     = uiState.showControls
            )
        }
    }
}

// Backlog UX 2026-08-30/09-10 (HU-42): agrupa los 5 accesos directos del
// menú del Visor en un solo parámetro -- evita LongParameterList al
// extraer ViewerTopBarSection() de abajo (fix de LongMethod de detekt,
// disparado al agregar esos mismos accesos a ViewerScreen()).
private data class ViewerDocumentActions(
    val onConvert           : (DocumentUiModel) -> Unit,
    val onCreateQr          : (DocumentUiModel) -> Unit,
    val onMakeSearchable    : (DocumentUiModel) -> Unit,
    val onSign              : (DocumentUiModel) -> Unit,
    val onMoveToSecureFolder: (DocumentUiModel) -> Unit
)

// Estado mutable de la búsqueda, agrupado por el mismo motivo de arriba --
// `searchQuery`/`showSearch` siguen viviendo en ViewerScreen() (también los
// usa el contenido principal para Word/Excel/PowerPoint/Texto), acá solo se
// pasan junto con sus setters.
private data class ViewerSearchState(
    val query         : String,
    val active        : Boolean,
    val onQueryChange : (String) -> Unit,
    val onActiveChange: (Boolean) -> Unit
)

// Extraído de ViewerScreen() (LongMethod de detekt, disparado al agregar
// los accesos directos de HU-42) -- la barra superior del Visor y su barra
// de búsqueda inline, con toda la lógica de qué formatos son "de texto"
// (habilitan buscar) y el debounce de búsqueda en PDF.
@Composable
private fun BoxScope.ViewerTopBarSection(
    doc            : DocumentUiModel,
    uiState        : ViewerUiState,
    onBack         : () -> Unit,
    viewModel      : ViewerViewModel,
    search         : ViewerSearchState,
    documentActions: ViewerDocumentActions
) {
    val context = LocalContext.current
    val mime        = (uiState.mimeType ?: "").lowercase()
    val isPdf       = mime.contains("pdf") || doc.name.endsWith(".pdf", ignoreCase = true)
    // Bug real encontrado 2026-09-14 (revisión pre-fusión HU-42, preexistente
    // -- no introducido por esta extracción): faltaba el mismo fallback por
    // extensión que ya tiene el `when` de renderizado principal para
    // Word/Excel/PowerPoint con MIME genérico (p.ej. `application/octet-stream`
    // de algunos `DocumentsProvider`), lo que dejaba el botón de búsqueda
    // inactivo aunque el documento sí se renderizara.
    val isTextBased = isPdf ||
            mime.contains("word")       ||
            mime.contains("text")       ||
            mime.contains("excel")      ||
            mime.contains("powerpoint") ||
            doc.name.endsWith(".txt")   ||
            doc.name.endsWith(".md")    ||
            doc.name.endsWith(".csv")   ||
            doc.name.endsWith(".doc")   ||
            doc.name.endsWith(".docx")  ||
            doc.name.endsWith(".xls")   ||
            doc.name.endsWith(".xlsx")  ||
            doc.name.endsWith(".ppt")   ||
            doc.name.endsWith(".pptx")

    // ── Búsqueda en PDF: los otros formatos filtran en línea vía
    // searchQuery (ver WordViewerContent/ExcelViewerContent/etc.); el
    // PDF necesita extraer texto por página (SearchPdfTextUseCase), así
    // que se dispara desde acá con un pequeño debounce.
    LaunchedEffect(search.query, isPdf, uiState.fileUri) {
        if (!isPdf) return@LaunchedEffect
        if (search.query.isBlank()) {
            viewModel.clearPdfSearch()
        } else {
            delay(300)
            viewModel.searchInPdf(search.query)
        }
    }

    ViewerTopBar(
        fileName        = doc.name,
        isFavorite      = uiState.isFavorite,
        visible         = uiState.showControls,
        onBackClick     = onBack,
        onFavoriteClick = { viewModel.toggleFavorite() },
        onShareClick    = { viewModel.shareDocument(context) },
        onSearchClick   = {
            if (isTextBased) {
                val newActive = !search.active
                search.onActiveChange(newActive)
                if (!newActive) search.onQueryChange("")
                // HU-46: hallazgo de la revisión de correctitud -- buscar y
                // anotar no tenían exclusión mutua, ambas barras se dibujaban
                // superpuestas en el mismo lugar si se abrían las dos.
                if (newActive && uiState.showAnnotationToolbar) {
                    viewModel.closeAnnotationToolbar()
                }
            }
        },
        onConvertClick  = { documentActions.onConvert(doc) },
        onCreateQrClick = { documentActions.onCreateQr(doc) },
        isPdf           = isPdf,
        onMakeSearchableClick    = { documentActions.onMakeSearchable(doc) },
        onSignClick              = { documentActions.onSign(doc) },
        onMoveToSecureFolderClick = { documentActions.onMoveToSecureFolder(doc) },
        onRenameClick   = { viewModel.onRenameClick() },
        onDeleteClick   = { viewModel.onDeleteClick() },
        isAnnotating    = uiState.showAnnotationToolbar,
        onAnnotateClick = {
            viewModel.toggleAnnotationToolbar()
            // HU-46: misma exclusión mutua que arriba, en el sentido inverso.
            if (search.active) {
                search.onActiveChange(false)
                search.onQueryChange("")
            }
        },
        modifier        = Modifier.align(Alignment.TopCenter)
    )

    // ── Barra de búsqueda ─────────────────────────────────────────────
    if (search.active && isTextBased) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .statusBarsPadding()
                .zIndex(10f)
        ) {
            SearchBar(
                query    = search.query,
                onQuery  = { search.onQueryChange(it) },
                onClose  = { search.onActiveChange(false); search.onQueryChange("") }
            )
            if (isPdf) {
                PdfSearchResultBar(
                    matchCount   = uiState.pdfSearchMatches.size,
                    currentIndex = uiState.pdfSearchIndex,
                    hasQuery     = search.query.isNotBlank(),
                    onNext       = { viewModel.nextPdfSearchResult() },
                    onPrevious   = { viewModel.previousPdfSearchResult() }
                )
            }
        }
    }

    // ── HU-46: barra de herramientas de anotación ─────────────────────
    if (uiState.showAnnotationToolbar && isPdf) {
        ViewerAnnotationToolbar(
            mode            = uiState.annotationMode,
            selectedColor   = uiState.selectedHighlightColor,
            highlightColors = ANNOTATION_HIGHLIGHT_COLORS,
            onColorSelected = { color ->
                viewModel.setHighlightColor(color)
                viewModel.setAnnotationMode(AnnotationMode.HIGHLIGHT)
            },
            onNoteSelected  = { viewModel.setAnnotationMode(AnnotationMode.NOTE) },
            onDone          = { viewModel.closeAnnotationToolbar() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .statusBarsPadding()
                .zIndex(10f)
        )
    }
}

// ── Barra de búsqueda inline ──────────────────────────────────────────────────
@Composable
private fun SearchBar(
    query   : String,
    onQuery : (String) -> Unit,
    onClose : () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.Search, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            OutlinedTextField(
                value         = query,
                onValueChange = onQuery,
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    Text(
                        stringResource(R.string.viewer_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape      = MaterialTheme.shapes.medium,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Visor de imágenes ─────────────────────────────────────────────────────────
@Composable
private fun ImageViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var bitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Timber.e("Error cargando imagen: ${e.message}")
                null
            }
        }
    }

    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale   = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .graphicsLayer(
                scaleX       = scale,
                scaleY       = scale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap             = it.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .padding(top = 92.dp, bottom = 92.dp)
            )
        }
    }
}

// ── Barra de resultados de búsqueda en PDF ────────────────────────────────────
@Composable
private fun PdfSearchResultBar(
    matchCount  : Int,
    currentIndex: Int,
    hasQuery    : Boolean,
    onNext      : () -> Unit,
    onPrevious  : () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bug real encontrado 2026-09-14 (repaso general): estos textos
            // y content descriptions estaban hardcodeados en español,
            // saltándose el sistema de 12 idiomas.
            Text(
                text = when {
                    !hasQuery       -> ""
                    matchCount == 0 -> stringResource(R.string.viewer_search_no_results)
                    else            -> String.format(
                        stringResource(R.string.viewer_search_match_format), currentIndex + 1, matchCount
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.viewer_search_previous_match),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.viewer_search_next_match),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// RF-VIS-08: color del resaltado de búsqueda -- amarillo semitransparente,
// mismo tono que usan la mayoría de lectores/navegadores para esto.
private val PdfHighlightColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)

// HU-46: alpha del resaltado persistido (mismo criterio que PdfHighlightColor
// de arriba, aplicada sobre el color elegido por el usuario en vez de fijo).
private const val ANNOTATION_HIGHLIGHT_ALPHA = 0.35f
private val NoteMarkerRadiusDp = 9.dp
private val NoteHitRadiusDp    = 22.dp // más grande que el marcador visual -- objetivo táctil cómodo

// ── Visor de PDF ──────────────────────────────────────────────────────────────
@Composable
private fun PdfViewerContent(
    uri          : Uri?,
    targetPage   : Int?,
    highlights   : Map<Int, List<PdfMatchRect>>,
    onPageChanged: (Int, Int) -> Unit,
    onTap        : () -> Unit,
    // HU-46: resaltado + notas adhesivas persistentes por documento.
    annotationMode         : AnnotationMode = AnnotationMode.NONE,
    selectedHighlightColor : Int = ANNOTATION_HIGHLIGHT_COLORS.first(),
    documentAnnotations    : Map<Int, List<AnnotationEntity>> = emptyMap(),
    onHighlightDrawn       : (Int, PdfRectPts) -> Unit = { _, _ -> },
    onNoteRequested        : (Int, PdfRectPts) -> Unit = { _, _ -> },
    onAnnotationTap        : (AnnotationEntity) -> Unit = {}
) {
    val context   = LocalContext.current
    var pages     by remember { mutableStateOf<List<PdfPageBitmap>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var scale         by remember { mutableFloatStateOf(1f) }
    var offsetX       by remember { mutableFloatStateOf(0f) }
    var offsetY       by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val listState = rememberLazyListState()
    val noteHitRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { NoteHitRadiusDp.toPx() }

    LaunchedEffect(targetPage, pages.size) {
        if (targetPage != null && targetPage in pages.indices) {
            listState.animateScrollToItem(targetPage)
        }
    }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        pages = withContext(Dispatchers.IO) {
            try {
                renderPdfPagesToBitmaps(uri, context)
            } catch (e: Exception) {
                Timber.e("Error renderizando PDF: ${e.message}")
                loadError = true
                emptyList()
            }
        }
        onPageChanged(0, pages.size)
    }

    if (loadError) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.viewer_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    text  = stringResource(R.string.viewer_rendering),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // HU-46: mientras se está anotando (HIGHLIGHT/NOTE), se deshabilita el
    // zoom/pan libre y el "tap para alternar controles" del modo normal --
    // conviven mal con arrastrar un rectángulo o tocar para anclar una nota.
    // En AnnotationMode.NONE el comportamiento es exactamente el de antes.
    val isAnnotating = annotationMode != AnnotationMode.NONE
    val columnModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { containerSize = it }
        .let { base ->
            if (isAnnotating) base else base
                .clickable { onTap() }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 4f)
                        // Bug real (QA): sin límite, arrastrar tras hacer zoom
                        // podía sacar el contenido del área visible por
                        // completo -- "el PDF se pierde arriba" -- sin ninguna
                        // forma de recuperarlo salvo adivinar cuánto arrastrar
                        // de vuelta. `graphicsLayer` escala/traslada desde el
                        // centro por defecto, así que el desplazamiento máximo
                        // que deja al menos el borde del contenido visible es
                        // `tamaño * (escala - 1) / 2` por eje -- en escala 1 el
                        // rango es [0, 0], forzando el desplazamiento de vuelta
                        // a cero en cuanto se hace pinch-zoom-out del todo.
                        val maxX = (containerSize.width  * (newScale - 1) / 2f).coerceAtLeast(0f)
                        val maxY = (containerSize.height * (newScale - 1) / 2f).coerceAtLeast(0f)
                        scale   = newScale
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    }
                }
        }
        .graphicsLayer(
            scaleX       = if (isAnnotating) 1f else scale,
            scaleY       = if (isAnnotating) 1f else scale,
            translationX = if (isAnnotating) 0f else offsetX,
            translationY = if (isAnnotating) 0f else offsetY
        )

    LazyColumn(
        state = listState,
        modifier = columnModifier,
        contentPadding      = PaddingValues(top = 100.dp, bottom = 100.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, pageBitmap ->
            LaunchedEffect(index, pages.size) {
                onPageChanged(index, pages.size)
            }
            val pageNumber      = index + 1
            val pageHighlights  = highlights[pageNumber]
            val pageAnnotations = documentAnnotations[pageNumber].orEmpty()
            var dragStart   by remember { mutableStateOf<Offset?>(null) }
            var dragCurrent by remember { mutableStateOf<Offset?>(null) }
            val shape = MaterialTheme.shapes.small
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .accentShadow(shape = shape, elevation = 2.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .accentBorder(shape = shape)
            ) {
                Image(
                    bitmap             = pageBitmap.bitmap.asImageBitmap(),
                    contentDescription = "Página $pageNumber",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .pdfAnnotationGestures(
                            annotationMode  = annotationMode,
                            pageBitmap      = pageBitmap,
                            pageNumber      = pageNumber,
                            pageAnnotations = pageAnnotations,
                            noteHitRadiusPx = noteHitRadiusPx,
                            onDragPreview   = { start, current -> dragStart = start; dragCurrent = current },
                            callbacks = PdfAnnotationCallbacks(
                                onHighlightDrawn = onHighlightDrawn,
                                onNoteRequested  = onNoteRequested,
                                onAnnotationTap  = onAnnotationTap,
                                onTap            = onTap
                            )
                        )
                        .drawWithContent {
                            drawContent()
                            val preview = dragStart?.let { s -> dragCurrent?.let { c -> s to c } }
                            drawPdfPageOverlays(
                                pageBitmap             = pageBitmap,
                                pageHighlights         = pageHighlights,
                                pageAnnotations        = pageAnnotations,
                                dragPreview            = preview,
                                selectedHighlightColor = selectedHighlightColor
                            )
                        }
                )
            }
        }
    }
}

// HU-46: agrupa los callbacks de gestos de anotación -- evita
// LongParameterList en pdfAnnotationGestures() de abajo (mismo criterio ya
// usado en ViewerDocumentActions/ViewerMenuActions de este mismo archivo).
private data class PdfAnnotationCallbacks(
    val onHighlightDrawn: (Int, PdfRectPts) -> Unit,
    val onNoteRequested : (Int, PdfRectPts) -> Unit,
    val onAnnotationTap : (AnnotationEntity) -> Unit,
    val onTap           : () -> Unit
)

// HU-46: gestos de anotación de una página -- extraído de PdfViewerContent
// (LongMethod de detekt) para mantener el composable principal corto. En
// HIGHLIGHT arrastra un rectángulo (estado del arrastre vive local al
// bloque suspendido, no en `remember` -- `onDragPreview` es el único canal
// hacia la composición, para poder dibujar la vista previa en vivo); en
// NOTE/NONE un tap decide entre "abrir anotación existente", "anclar nota
// nueva" o "alternar controles" (comportamiento normal, sin regresión).
// Hallazgo real de la revisión general 2026-09-16 (#14): pageAnnotations
// era clave de este pointerInput -- cualquier emisión de Room durante un
// arrastre en curso (ni siquiera tiene que ser de esta página: el Flow
// observa TODAS las anotaciones del documento) reiniciaba la corrutina de
// gestos, cancelando en silencio el resaltado que el usuario estaba
// dibujando. Se saca pageAnnotations de las keys y se lee vía
// rememberUpdatedState -- el detectTapGestures/detectDragGestures ya no se
// reinicia por eso, pero el hit-test de notas sigue viendo la lista
// vigente en cada tap (Modifier.composed{} es necesario para poder llamar
// una función @Composable como rememberUpdatedState acá).
private fun Modifier.pdfAnnotationGestures(
    annotationMode  : AnnotationMode,
    pageBitmap      : PdfPageBitmap,
    pageNumber      : Int,
    pageAnnotations : List<AnnotationEntity>,
    noteHitRadiusPx : Float,
    onDragPreview   : (Offset?, Offset?) -> Unit,
    callbacks       : PdfAnnotationCallbacks
): Modifier = composed {
    val currentAnnotations by rememberUpdatedState(pageAnnotations)
    pointerInput(annotationMode, pageBitmap) {
        val displayScale = size.width / pageBitmap.pageWidthPts
        if (annotationMode == AnnotationMode.HIGHLIGHT) {
            var dragStart  : Offset? = null
            var dragCurrent: Offset? = null
            detectDragGestures(
                onDragStart = { offset -> dragStart = offset; dragCurrent = offset; onDragPreview(offset, offset) },
                onDrag      = { change, _ -> dragCurrent = change.position; onDragPreview(dragStart, dragCurrent) },
                onDragEnd   = {
                    val start   = dragStart
                    val current = dragCurrent
                    if (start != null && current != null) {
                        val rect = screenDragToPdfRect(
                            start.x, start.y, current.x, current.y, displayScale, pageBitmap.pageHeightPts
                        )
                        if (isValidHighlightSize(rect)) callbacks.onHighlightDrawn(pageNumber, rect)
                    }
                    dragStart = null; dragCurrent = null
                    onDragPreview(null, null)
                },
                onDragCancel = { dragStart = null; dragCurrent = null; onDragPreview(null, null) }
            )
        } else {
            detectTapGestures(onTap = { offset ->
                val hit = hitTestAnnotation(
                    offset, currentAnnotations, displayScale, pageBitmap.pageHeightPts, noteHitRadiusPx
                )
                when {
                    hit != null -> callbacks.onAnnotationTap(hit)
                    annotationMode == AnnotationMode.NOTE -> callbacks.onNoteRequested(
                        pageNumber,
                        screenPointToPdfPoint(offset.x, offset.y, displayScale, pageBitmap.pageHeightPts)
                    )
                    else -> callbacks.onTap()
                }
            })
        }
    }
}

// HU-46: dibuja, sobre el bitmap ya renderizado de una página, el
// resaltado de búsqueda (RF-VIS-08, ya existente), las anotaciones
// persistidas y la vista previa en vivo del resaltado que se está
// arrastrando -- extraído de PdfViewerContent (LongMethod de detekt).
private fun DrawScope.drawPdfPageOverlays(
    pageBitmap             : PdfPageBitmap,
    pageHighlights         : List<PdfMatchRect>?,
    pageAnnotations        : List<AnnotationEntity>,
    dragPreview            : Pair<Offset, Offset>?,
    selectedHighlightColor : Int
) {
    val displayScale = size.width / pageBitmap.pageWidthPts
    // RF-VIS-08: resaltado inline -- convierte cada coincidencia de puntos
    // PDF (origen abajo-izquierda) a píxeles de pantalla (origen
    // arriba-izquierda), inverso exacto de mapOcrBoxToPdf en OcrPdfUseCase.
    pageHighlights?.forEach { r ->
        val screenX = r.xPts * displayScale
        val screenY = (pageBitmap.pageHeightPts - (r.yPts + r.heightPts)) * displayScale
        drawRect(
            color   = PdfHighlightColor,
            topLeft = Offset(screenX, screenY),
            size    = ComposeSize(r.widthPts * displayScale, r.heightPts * displayScale)
        )
    }
    pageAnnotations.forEach { annotation ->
        when (annotation.type) {
            AnnotationType.HIGHLIGHT -> {
                val screenX = annotation.xPts * displayScale
                val screenY = (pageBitmap.pageHeightPts - (annotation.yPts + annotation.heightPts)) * displayScale
                drawRect(
                    color   = Color(annotation.color).copy(alpha = ANNOTATION_HIGHLIGHT_ALPHA),
                    topLeft = Offset(screenX, screenY),
                    size    = ComposeSize(annotation.widthPts * displayScale, annotation.heightPts * displayScale)
                )
            }
            AnnotationType.NOTE -> {
                val (cx, cy) = pdfPointToScreenPoint(
                    annotation.xPts, annotation.yPts, displayScale, pageBitmap.pageHeightPts
                )
                drawCircle(color = Color(annotation.color), radius = NoteMarkerRadiusDp.toPx(), center = Offset(cx, cy))
            }
        }
    }
    dragPreview?.let { (start, current) ->
        drawRect(
            color   = Color(selectedHighlightColor).copy(alpha = ANNOTATION_HIGHLIGHT_ALPHA),
            topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y)),
            size    = ComposeSize(kotlin.math.abs(current.x - start.x), kotlin.math.abs(current.y - start.y))
        )
    }
}

// HU-46: busca si un tap cae sobre una anotación ya persistida -- de la más
// reciente a la más vieja, para que una superposición favorezca la de
// arriba. NOTE usa un radio táctil (más grande que el marcador visual);
// HIGHLIGHT usa su rectángulo real.
private fun hitTestAnnotation(
    tap          : Offset,
    annotations  : List<AnnotationEntity>,
    displayScale : Float,
    pageHeightPts: Float,
    noteHitRadiusPx: Float
): AnnotationEntity? {
    for (annotation in annotations.asReversed()) {
        val hit = when (annotation.type) {
            AnnotationType.NOTE -> {
                val (cx, cy) = pdfPointToScreenPoint(annotation.xPts, annotation.yPts, displayScale, pageHeightPts)
                val dx = tap.x - cx
                val dy = tap.y - cy
                (dx * dx + dy * dy) <= noteHitRadiusPx * noteHitRadiusPx
            }
            AnnotationType.HIGHLIGHT -> {
                val screenX = annotation.xPts * displayScale
                val screenY = (pageHeightPts - (annotation.yPts + annotation.heightPts)) * displayScale
                val screenW = annotation.widthPts * displayScale
                val screenH = annotation.heightPts * displayScale
                tap.x in screenX..(screenX + screenW) && tap.y in screenY..(screenY + screenH)
            }
        }
        if (hit) return annotation
    }
    return null
}

// ── Visor de Word ─────────────────────────────────────────────────────────────
// Reescrito con Apache POI (RF pedido por el usuario 2026-09-03, mismo
// enfoque ya aplicado a PowerPoint) en vez de expresiones regulares sobre el
// XML crudo de word/document.xml -- acceso estructurado real vía
// XWPFDocument, ya probado en producción por WordToTextUseCase/
// WordToPdfUseCase. Reutiliza detectWordFormat()/extractLegacyDocBlocks()/
// isHeadingStyleName() de WordFormatDetection.kt (converter) en vez de
// duplicar esa lógica -- mismo manejo ya establecido de .doc legado (OLE2)
// vs .docx (OOXML) y de nombres de estilo de encabezado no-ingleses (Word en
// español escribe "Ttulo1", no "Heading1").
internal data class WordRun(val text: String, val bold: Boolean, val italic: Boolean, val fontSizeSp: Int?)
internal data class WordParagraph(val runs: List<WordRun>, val isHeading: Boolean)

// Antes cada .docx se aplanaba a una sola lista de párrafos -- una tabla
// real (ej. una factura, un formulario) se perdía entre el texto plano.
// Ahora se preserva su forma real de grilla, mismo componente visual que ya
// usa el visor de Excel (ExcelGridRow).
internal sealed interface WordBlock
internal data class WordParagraphBlock(val paragraph: WordParagraph) : WordBlock
internal data class WordTableBlock(val rows: List<List<String>>) : WordBlock

internal fun extractWordBlocks(input: java.io.InputStream): List<WordBlock> {
    val (format, stream) = detectWordFormat(input)
    return if (format == WordFileFormat.OLE2) extractLegacyWordBlocks(stream) else extractOoxmlWordBlocks(stream)
}

internal fun extractLegacyWordBlocks(stream: java.io.InputStream): List<WordBlock> =
    extractLegacyDocBlocks(stream).map { (text, isHeading) ->
        val run = WordRun(text, bold = false, italic = false, fontSizeSp = null)
        WordParagraphBlock(WordParagraph(listOf(run), isHeading))
    }

internal fun extractOoxmlWordBlocks(stream: java.io.InputStream): List<WordBlock> {
    val blocks = mutableListOf<WordBlock>()
    XWPFDocument(stream).use { doc ->
        doc.bodyElements.forEach { element ->
            when (element) {
                is XWPFParagraph -> extractWordParagraphBlock(element)?.let { blocks.add(it) }
                is XWPFTable -> extractWordTableBlock(element)?.let { blocks.add(it) }
            }
        }
    }
    return blocks
}

internal fun extractWordParagraphBlock(paragraph: XWPFParagraph): WordParagraphBlock? {
    val runs = paragraph.runs.mapNotNull { run ->
        val text = run.text()
        if (text.isNullOrBlank()) return@mapNotNull null
        WordRun(
            text       = text,
            bold       = run.isBold,
            italic     = run.isItalic,
            fontSizeSp = run.fontSizeAsDouble?.toInt()
        )
    }
    if (runs.isEmpty()) return null
    val isHeading = isHeadingStyleName(paragraph.style ?: "")
    return WordParagraphBlock(WordParagraph(runs, isHeading))
}

internal fun extractWordTableBlock(table: XWPFTable): WordTableBlock? {
    val rows = table.rows.map { row -> row.tableCells.map { it.text } }
    return if (rows.isEmpty()) null else WordTableBlock(rows)
}

private fun wordParagraphAnnotatedString(
    para: WordParagraph,
    baseSizeSp: TextUnit
): AnnotatedString = buildAnnotatedString {
    para.runs.forEach { run ->
        withStyle(
            SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle  = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                fontSize   = run.fontSizeSp?.sp ?: baseSizeSp
            )
        ) { append(run.text) }
    }
}

@Composable
private fun WordViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context = LocalContext.current

    var blocks    by remember { mutableStateOf<List<WordBlock>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        blocks = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    extractWordBlocks(input)
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Error leyendo Word")
                hasError = true
                emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun blockPlainText(block: WordBlock): String = when (block) {
        is WordParagraphBlock -> block.paragraph.runs.joinToString("") { it.text }
        is WordTableBlock     -> block.rows.joinToString(" ") { row -> row.joinToString(" ") }
    }

    val displayBlocks = if (searchQuery.isBlank()) blocks
    else blocks.filter { blockPlainText(it).contains(searchQuery, ignoreCase = true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary
            )
            hasError || blocks.isEmpty() -> Column(
                modifier            = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text      = stringResource(R.string.viewer_word_read_error),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding      = PaddingValues(top = 100.dp, bottom = 100.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text     = stringResource(
                                R.string.viewer_search_results_count, displayBlocks.size, searchQuery
                            ),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                itemsIndexed(displayBlocks) { _, block ->
                    val bgColor = if (searchQuery.isNotBlank())
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.background

                    when (block) {
                        is WordTableBlock -> WordTableView(block, modifier = Modifier.padding(vertical = 8.dp))
                        is WordParagraphBlock -> {
                            val para = block.paragraph
                            if (para.isHeading) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgColor, RoundedCornerShape(8.dp))
                                ) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text       = wordParagraphAnnotatedString(para, baseSizeSp = 18.sp),
                                        style      = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.primary,
                                        lineHeight = 26.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(
                                        color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        thickness = 1.dp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            } else {
                                Text(
                                    text       = wordParagraphAnnotatedString(para, baseSizeSp = 14.sp),
                                    style      = MaterialTheme.typography.bodyMedium,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp,
                                    modifier   = Modifier
                                        .padding(vertical = 3.dp)
                                        .fillMaxWidth()
                                        .background(bgColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = if (searchQuery.isNotBlank()) 8.dp else 0.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Antes una tabla real (ej. una factura, un formulario) se perdía como texto
// plano entre los párrafos -- ahora se ve como una grilla real, con scroll
// horizontal propio si tiene más columnas de las que caben en pantalla.
@Composable
private fun WordTableView(table: WordTableBlock, modifier: Modifier = Modifier) {
    val columnCount = table.rows.maxOfOrNull { it.size } ?: 0
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                for (col in 0 until columnCount) {
                    if (col > 0) {
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(EXCEL_COLUMN_WIDTH)
                            .background(
                                if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text       = row.getOrElse(col) { "" },
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines   = 3
                        )
                    }
                }
            }
        }
    }
}

// ── Visor de Excel ────────────────────────────────────────────────────────────
// Reescrito con Apache POI (RF pedido por el usuario 2026-09-03, mismo
// enfoque ya aplicado a PowerPoint/Word) en vez de expresiones regulares
// sobre xl/worksheets/sheet1.xml -- WorkbookFactory detecta y abstrae
// .xls/.xlsx automáticamente (a diferencia de Word, no hace falta elegir
// entre dos APIs distintas). Dos mejoras reales que el regex nunca pudo dar:
// (1) DataFormatter muestra fechas/monedas/porcentajes como Excel los
// formatea, no el número crudo de serie; (2) ya no se asume "solo la
// primera hoja" -- todas las hojas están disponibles con pestañas para
// cambiar entre ellas.
private val EXCEL_COLUMN_WIDTH = 120.dp

private data class ExcelRow(val cells: List<String>)
private data class ExcelSheetModel(val name: String, val rows: List<ExcelRow>)

private fun extractExcelSheets(input: java.io.InputStream): List<ExcelSheetModel> {
    val workbook = WorkbookFactory.create(input)
    val formatter = DataFormatter()
    val evaluator = try {
        workbook.creationHelper.createFormulaEvaluator()
    } catch (e: Exception) {
        Timber.w(e, "extractExcelSheets: no se pudo crear el evaluador de fórmulas")
        null
    }
    val sheets = (0 until workbook.numberOfSheets).mapNotNull { sheetIndex ->
        val sheet = workbook.getSheetAt(sheetIndex)
        val rows = sheet.mapNotNull { row ->
            val lastCell = row.lastCellNum.toInt()
            if (lastCell < 0) return@mapNotNull null
            val cells = (0 until lastCell).map { col ->
                val cell = row.getCell(col) ?: return@map ""
                try {
                    if (evaluator != null) formatter.formatCellValue(cell, evaluator)
                    else formatter.formatCellValue(cell)
                } catch (e: Exception) {
                    Timber.w(e, "extractExcelSheets: no se pudo formatear una celda")
                    ""
                }
            }
            if (cells.any { it.isNotBlank() }) ExcelRow(cells) else null
        }
        if (rows.isEmpty()) null else ExcelSheetModel(sheet.sheetName, rows)
    }
    workbook.close()
    return sheets
}

@Composable
private fun ExcelViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context = LocalContext.current

    var sheets    by remember { mutableStateOf<List<ExcelSheetModel>>(emptyList()) }
    var sheetIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        sheets = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    extractExcelSheets(input)
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Error leyendo Excel")
                hasError = true
                emptyList()
            } finally {
                isLoading = false
            }
        }
        sheetIndex = 0
    }

    fun sheetMatches(sheet: ExcelSheetModel) =
        sheet.rows.any { row -> row.cells.any { it.contains(searchQuery, ignoreCase = true) } }

    // Hallazgo real de la revisión general 2026-09-16 (#13): la búsqueda
    // solo filtraba la hoja activa -- si la coincidencia estaba en otra
    // hoja, el usuario veía "0 resultados" en silencio sin ninguna pista de
    // que el dato sí existe en el archivo. Si la hoja activa no tiene
    // coincidencias pero otra sí, se cambia automáticamente a la primera
    // que las tenga (a diferencia de PDF/Word/PowerPoint, acá no hay una
    // lista de bloques única para filtrar -- las hojas son grillas
    // independientes con sus propias columnas).
    LaunchedEffect(searchQuery, sheets) {
        if (searchQuery.isBlank() || sheets.isEmpty()) return@LaunchedEffect
        if (sheets.getOrNull(sheetIndex)?.let(::sheetMatches) != true) {
            val firstMatch = sheets.indexOfFirst(::sheetMatches)
            if (firstMatch >= 0) sheetIndex = firstMatch
        }
    }

    val rows = sheets.getOrNull(sheetIndex)?.rows.orEmpty()
    val displayRows = if (searchQuery.isBlank()) rows
    else rows.filter { row -> row.cells.any { it.contains(searchQuery, ignoreCase = true) } }
    val totalMatches = if (searchQuery.isBlank()) 0 else sheets.sumOf { sheet ->
        sheet.rows.count { row -> row.cells.any { it.contains(searchQuery, ignoreCase = true) } }
    }
    val columnCount = rows.maxOfOrNull { it.cells.size } ?: 0
    val gridScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary
            )
            hasError || sheets.isEmpty() -> Text(
                text      = stringResource(R.string.viewer_excel_read_error),
                modifier  = Modifier.align(Alignment.Center).padding(32.dp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(100.dp))
                // Pestañas de hojas -- solo si hay más de una, para no meter
                // ruido visual en el caso más común de un solo Excel simple.
                if (sheets.size > 1) {
                    ExcelSheetTabs(
                        sheetNames   = sheets.map { it.name },
                        selectedIndex = sheetIndex,
                        onSelect     = { sheetIndex = it }
                    )
                }
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            // totalMatches cuenta en TODO el libro, no solo la
                            // hoja activa (#13) -- displayRows sigue acotado a
                            // la hoja activa (ya auto-seleccionada arriba si
                            // hacía falta) porque la grilla es por hoja.
                            text     = stringResource(
                                R.string.viewer_search_results_count, totalMatches, searchQuery
                            ),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                val header = if (searchQuery.isBlank()) displayRows.firstOrNull() else null
                val data   = if (searchQuery.isBlank() && displayRows.size > 1)
                    displayRows.drop(1) else displayRows

                if (header != null) {
                    item {
                        ExcelGridRow(
                            cells       = header.cells,
                            columnCount = columnCount,
                            scrollState = gridScrollState,
                            isHeader    = true,
                            highlighted = false,
                            zebra       = false
                        )
                    }
                }

                itemsIndexed(data) { index, row ->
                    ExcelGridRow(
                        cells       = row.cells,
                        columnCount = columnCount,
                        scrollState = gridScrollState,
                        isHeader    = false,
                        highlighted = searchQuery.isNotBlank(),
                        zebra       = index % 2 == 0
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun ExcelSheetTabs(
    sheetNames   : List<String>,
    selectedIndex: Int,
    onSelect     : (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding      = 16.dp,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        sheetNames.forEachIndexed { index, name ->
            Tab(
                selected = index == selectedIndex,
                onClick  = { onSelect(index) },
                text     = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun ExcelGridRow(
    cells      : List<String>,
    columnCount: Int,
    scrollState: ScrollState,
    isHeader   : Boolean,
    highlighted: Boolean,
    zebra      : Boolean
) {
    val bgColor = when {
        isHeader    -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        zebra       -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else        -> MaterialTheme.colorScheme.surface
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .background(bgColor)
        ) {
            for (col in 0 until columnCount) {
                if (col > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(if (isHeader) 40.dp else 36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(EXCEL_COLUMN_WIDTH)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(
                        text       = cells.getOrElse(col) { "" },
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        color      = if (isHeader) MaterialTheme.colorScheme.onPrimary
                                     else MaterialTheme.colorScheme.onSurface,
                        maxLines   = 2
                    )
                }
            }
        }
        HorizontalDivider(
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

// ── Visor de PowerPoint ───────────────────────────────────────────────────────
// Antes: cada .pptx se abría como zip y se extraía título+cuerpo por regex
// sobre el XML crudo de cada slide -- sin imágenes, sin diseño real (RF
// pedido por el usuario 2026-09-03: "no se visualizan como el archivo
// original"). Ahora se usa Apache POI (XMLSlideShow/XSLFShape, ya probado en
// producción por WordToPdfUseCase/ExcelToPdfUseCase) para leer cada forma
// real de la diapositiva -- texto con su formato real e imágenes reales --
// en vez de solo título+viñetas.
//
// Nota de alcance: NO se usa la posición/tamaño real de cada forma
// (`XSLFShape.anchor`) porque esa API devuelve `java.awt.geom.Rectangle2D` --
// el compilador de Kotlin ni siquiera puede resolver esa clase contra el
// classpath de Android ("Cannot access class 'Rectangle2D'"), confirmado
// al intentarlo. Las formas se muestran apiladas en su orden original, no
// en su posición exacta -- sigue siendo una mejora real (formato real por
// forma, imágenes reales) sin depender de una API que no compila en Android.
internal data class PptShapeContent(
    val runs      : List<WordRun>,
    val isTitle   : Boolean,
    val imageBytes: ByteArray?
)
internal data class PptSlideModel(val number: Int, val shapes: List<PptShapeContent>)

private fun extractPptSlides(input: InputStream): List<PptSlideModel> {
    val slideShow = XMLSlideShow(input)
    val slides = slideShow.slides.mapIndexed { index, slide ->
        val shapes = slide.shapes.mapNotNull { shape -> extractPptShapeContent(shape) }
        PptSlideModel(index + 1, shapes)
    }
    slideShow.close()
    return slides
}

private fun extractPptShapeContent(shape: XSLFShape): PptShapeContent? {
    if (shape is XSLFPictureShape) {
        val bytes = try {
            shape.pictureData?.data
        } catch (e: Exception) {
            Timber.w(e, "extractPptShapeContent: no se pudo leer una imagen")
            null
        }
        return bytes?.let { PptShapeContent(runs = emptyList(), isTitle = false, imageBytes = it) }
    }

    if (shape !is XSLFTextShape) return null
    val isTitle = try {
        shape.isPlaceholder && shape.textType?.name?.contains("TITLE", ignoreCase = true) == true
    } catch (e: Exception) {
        Timber.w(e, "extractPptShapeContent: no se pudo determinar si la forma es un título")
        false
    }
    // Cada `XSLFTextParagraph` es un punto/viñeta separado (RF pedido por el
    // usuario 2026-09-03) -- sin un salto de línea entre ellos, "Punto uno"
    // y "Punto dos" quedaban pegados como "Punto unoPunto dos". En cuerpos
    // (no títulos) se antepone "• " a cada punto -- PowerPoint dibuja la
    // viñeta aparte, no la incluye en el texto del run.
    val paragraphRuns = shape.textParagraphs.mapNotNull { paragraph ->
        val runs = paragraph.textRuns.mapNotNull { run ->
            val text = run.rawText
            if (text.isNullOrBlank()) return@mapNotNull null
            WordRun(
                text       = text,
                bold       = run.isBold,
                italic     = run.isItalic,
                fontSizeSp = run.fontSize?.toInt()
            )
        }
        if (runs.isEmpty()) return@mapNotNull null
        if (isTitle) runs else listOf(WordRun("• ", bold = false, italic = false, fontSizeSp = null)) + runs
    }
    if (paragraphRuns.isEmpty()) return null
    val runs = paragraphRuns.reduce { acc, paragraph ->
        acc + WordRun("\n", bold = false, italic = false, fontSizeSp = null) + paragraph
    }
    return PptShapeContent(runs = runs, isTitle = isTitle, imageBytes = null)
}

@Composable
private fun PptViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context = LocalContext.current

    var slides    by remember { mutableStateOf<List<PptSlideModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        slides = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    extractPptSlides(input)
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Error leyendo PPT")
                emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun slideText(slide: PptSlideModel) = slide.shapes.joinToString(" ") { shape ->
        shape.runs.joinToString(" ") { it.text }
    }

    val displaySlides = if (searchQuery.isBlank()) slides
    else slides.filter { slideText(it).contains(searchQuery, ignoreCase = true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary
            )
            slides.isEmpty() -> Text(
                text      = stringResource(R.string.viewer_ppt_read_error),
                modifier  = Modifier.align(Alignment.Center).padding(32.dp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(top = 100.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text     = stringResource(
                                R.string.viewer_search_results_count, displaySlides.size, searchQuery
                            ),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                // RF: cada diapositiva se muestra como un lienzo de proporción
                // 16:9 (como una diapositiva real), no como una tarjeta de lista
                // genérica -- el número de diapositiva queda como leyenda FUERA
                // del lienzo, igual que un editor de presentaciones muestra sus
                // miniaturas.
                itemsIndexed(displaySlides) { _, slide ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text     = stringResource(R.string.viewer_slide_number, slide.number),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val shape = MaterialTheme.shapes.large
                        val containerColor = if (searchQuery.isNotBlank())
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else
                            Color.White
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .accentShadow(shape = shape, elevation = 3.dp)
                                .clip(shape)
                                .background(containerColor)
                                .accentBorder(shape = shape)
                        ) {
                            PptSlideCanvas(slide)
                        }
                    }
                }
            }
        }
    }
}

// Lista apilada en el orden original de las formas -- no la posición exacta
// (ver nota de alcance sobre `Rectangle2D` más arriba), pero cada forma
// mantiene su propio formato real (negrita/cursiva/tamaño) y las imágenes
// se muestran de verdad, algo que el regex anterior ni siquiera intentaba.
@Composable
private fun PptSlideCanvas(slide: PptSlideModel) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        slide.shapes.forEach { shapeContent -> PptShapeView(shapeContent) }
    }
}

@Composable
private fun PptShapeView(shape: PptShapeContent) {
    val imageBytes = shape.imageBytes
    if (imageBytes != null) {
        val bitmap = remember(imageBytes) {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth(),
                contentScale       = androidx.compose.ui.layout.ContentScale.FillWidth
            )
        }
        return
    }
    Text(
        text       = wordParagraphAnnotatedString(
            WordParagraph(shape.runs, isHeading = shape.isTitle),
            baseSizeSp = if (shape.isTitle) 20.sp else 13.sp
        ),
        style      = MaterialTheme.typography.bodyMedium,
        fontWeight = if (shape.isTitle) FontWeight.Bold else FontWeight.Normal,
        color      = if (shape.isTitle) MaterialTheme.colorScheme.primary else Color(0xFF222222),
        maxLines   = if (shape.isTitle) 3 else Int.MAX_VALUE
    )
}

// ── Visor de texto plano ──────────────────────────────────────────────────────
@Composable
private fun TextViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context   = LocalContext.current
    var text      by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
            } catch (e: Exception) {
                Timber.e("Error leyendo TXT: ${e.message}")
                ""
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary
            )
            else -> TextViewerBody(text, searchQuery)
        }
    }
}

@Composable
private fun TextViewerBody(text: String, searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 100.dp, bottom = 100.dp, start = 20.dp, end = 20.dp)
    ) {
        if (searchQuery.isBlank()) {
            Text(
                text       = text.ifBlank { stringResource(R.string.viewer_empty_file) },
                style      = MaterialTheme.typography.bodyMedium,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        } else {
            TextViewerSearchResults(text, searchQuery)
        }
    }
}

@Composable
private fun TextViewerSearchResults(text: String, searchQuery: String) {
    val lines = text.lines().filter { it.contains(searchQuery, ignoreCase = true) }
    if (lines.isEmpty()) {
        Text(
            text  = stringResource(R.string.viewer_search_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Text(
        text     = stringResource(R.string.viewer_text_results_count, lines.size),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    lines.forEach { line ->
        Text(
            text       = line,
            style      = MaterialTheme.typography.bodyMedium,
            fontSize   = 15.sp,
            color      = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp,
            modifier   = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ── Dialog de contraseña PDF ──────────────────────────────────────────────────
@Composable
private fun PdfPasswordDialog(
    fileName     : String,
    passwordError: String?,
    isLoading    : Boolean,
    onConfirm    : (String) -> Unit,
    onDismiss    : () -> Unit
) {
    // Hallazgo real de la revisión general 2026-09-16: `remember` simple
    // perdía la contraseña tecleada al rotar el dispositivo -- mismo fix
    // que en ViewerNoteInputDialog/ViewerRenameDialog.
    var password    by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = MaterialTheme.shapes.extraLarge,
        icon             = {
            Icon(
                Icons.Rounded.Lock, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text      = stringResource(R.string.viewer_pdf_password_title),
                style     = MaterialTheme.typography.titleLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            PdfPasswordDialogBody(
                fileName      = fileName,
                password      = password,
                onPasswordChange = { password = it },
                showPassword  = showPassword,
                onToggleShowPassword = { showPassword = !showPassword },
                passwordError = passwordError
            )
        },
        confirmButton = {
            Button(
                onClick  = { if (password.isNotBlank()) onConfirm(password) },
                enabled  = password.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium
            ) {
                PdfPasswordConfirmButtonContent(isLoading)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

@Composable
private fun PdfPasswordDialogBody(
    fileName            : String,
    password            : String,
    onPasswordChange    : (String) -> Unit,
    showPassword        : Boolean,
    onToggleShowPassword: () -> Unit,
    passwordError       : String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text  = stringResource(R.string.viewer_pdf_password_body, fileName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(stringResource(R.string.viewer_pdf_password_label)) },
            placeholder   = { Text(stringResource(R.string.viewer_pdf_password_placeholder)) },
            visualTransformation = if (showPassword)
                androidx.compose.ui.text.input.VisualTransformation.None
            else
                androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            trailingIcon = {
                IconButton(onClick = onToggleShowPassword) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff
                        else Icons.Rounded.Visibility,
                        null
                    )
                }
            },
            isError    = passwordError != null,
            singleLine = true,
            shape      = MaterialTheme.shapes.medium
        )

        passwordError?.let {
            Text(
                text  = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PdfPasswordConfirmButtonContent(isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier    = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color       = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.viewer_pdf_password_open_button))
    }
}
// ── Formato no soportado ──────────────────────────────────────────────────────
@Composable
private fun UnsupportedFormatContent(
    mimeType: String,
    fileName: String,
    fileUri : Uri?,
    onTap   : () -> Unit
) {
    val context         = LocalContext.current
    val openWithText    = stringResource(R.string.viewer_open_other)
    val unsupportedText = stringResource(R.string.viewer_unsupported)

    val formatLabel = when {
        mimeType.contains("word")       || mimeType.contains("msword")       -> "Word"
        mimeType.contains("excel")      || mimeType.contains("sheet")        -> "Excel"
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "PowerPoint"
        mimeType.contains("text") -> stringResource(R.string.viewer_format_text)
        else                      -> stringResource(R.string.viewer_format_generic)
    }

    Box(
        modifier         = Modifier.fillMaxSize().clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(formatLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(fileName,        style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,        textAlign = TextAlign.Center)
            Text(unsupportedText, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (fileUri != null) {
                Button(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, openWithText))
                    } catch (e: Exception) {
                        Timber.e("No se pudo abrir: ${e.message}")
                    }
                }) { Text(openWithText) }
            }
        }
    }
}

