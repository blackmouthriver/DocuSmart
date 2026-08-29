package com.docsmart.features.viewer.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.features.viewer.domain.usecase.PdfMatchRect
import com.docsmart.features.viewer.presentation.components.ViewerBottomBar
import com.docsmart.features.viewer.presentation.components.ViewerDeleteConfirmDialog
import com.docsmart.features.viewer.presentation.components.ViewerRenameDialog
import com.docsmart.features.viewer.presentation.components.ViewerTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

@Composable
fun ViewerScreen(
    documentId: String,
    onBack    : () -> Unit,
    viewModel : ViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteConfirm() }
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
                                highlights    = uiState.pdfSearchHighlights,
                                onPageChanged = { page, total -> viewModel.onPageChanged(page, total) },
                                onTap         = { viewModel.toggleControls() }
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

        // ── TopBar + SearchBar ────────────────────────────────────────────────
        uiState.document?.let { doc ->
            val mime        = (uiState.mimeType ?: "").lowercase()
            val isPdf       = mime.contains("pdf") || doc.name.endsWith(".pdf", ignoreCase = true)
            val isTextBased = isPdf ||
                    mime.contains("word")       ||
                    mime.contains("text")       ||
                    mime.contains("excel")      ||
                    mime.contains("powerpoint") ||
                    doc.name.endsWith(".txt")   ||
                    doc.name.endsWith(".md")    ||
                    doc.name.endsWith(".csv")

            // ── Búsqueda en PDF: los otros formatos filtran en línea vía
            // searchQuery (ver WordViewerContent/ExcelViewerContent/etc.); el
            // PDF necesita extraer texto por página (SearchPdfTextUseCase), así
            // que se dispara desde acá con un pequeño debounce.
            LaunchedEffect(searchQuery, isPdf, uiState.fileUri) {
                if (!isPdf) return@LaunchedEffect
                if (searchQuery.isBlank()) {
                    viewModel.clearPdfSearch()
                } else {
                    delay(300)
                    viewModel.searchInPdf(searchQuery)
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
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }
                },
                onRenameClick   = { viewModel.onRenameClick() },
                onDeleteClick   = { viewModel.onDeleteClick() },
                modifier        = Modifier.align(Alignment.TopCenter)
            )

            // ── Barra de búsqueda ─────────────────────────────────────────────
            if (showSearch && isTextBased) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .statusBarsPadding()
                        .zIndex(10f)
                ) {
                    SearchBar(
                        query    = searchQuery,
                        onQuery  = { searchQuery = it },
                        onClose  = { showSearch = false; searchQuery = "" }
                    )
                    if (isPdf) {
                        PdfSearchResultBar(
                            matchCount   = uiState.pdfSearchMatches.size,
                            currentIndex = uiState.pdfSearchIndex,
                            hasQuery     = searchQuery.isNotBlank(),
                            onNext       = { viewModel.nextPdfSearchResult() },
                            onPrevious   = { viewModel.previousPdfSearchResult() }
                        )
                    }
                }
            }
        }

        ViewerBottomBar(
            currentPage = uiState.currentPage,
            totalPages  = uiState.totalPages,
            visible     = uiState.showControls,
            modifier    = Modifier.align(Alignment.BottomCenter)
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
                        "Buscar en documento...",
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
            Text(
                text = when {
                    !hasQuery       -> ""
                    matchCount == 0 -> "Sin resultados"
                    else            -> "Coincidencia ${currentIndex + 1} de $matchCount"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp, contentDescription = "Coincidencia anterior",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown, contentDescription = "Siguiente coincidencia",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Página renderizada + su tamaño real en puntos PDF (RF-VIS-08: necesario
 *  para convertir la posición de una coincidencia de búsqueda a píxeles de
 *  pantalla y dibujar el resaltado sobre el bitmap ya renderizado). */
private data class PdfPageBitmap(val bitmap: Bitmap, val pageWidthPts: Float, val pageHeightPts: Float)

/** Copia el PDF de [uri] al caché de la app y renderiza cada página a un [Bitmap]. */
private fun renderPdfPagesToBitmaps(uri: Uri, context: android.content.Context): List<PdfPageBitmap> {
    val cacheFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
    if (!copyPdfUriToCache(uri, context, cacheFile)) {
        Timber.e("PdfViewer: no se pudo copiar el PDF al caché")
        return emptyList()
    }
    Timber.d("PdfViewer: cacheFile copiado → ${cacheFile.length()}b")
    return renderCachedPdfPages(cacheFile)
}

private fun copyPdfUriToCache(uri: Uri, context: android.content.Context, cacheFile: File): Boolean =
    if (uri.scheme == "file") copyFileSchemeToCache(uri, cacheFile)
    else copyContentUriToCache(uri, context, cacheFile)

private fun copyFileSchemeToCache(uri: Uri, cacheFile: File): Boolean {
    val srcFile = uri.path?.let(::File)
    Timber.d("PdfViewer: file:// path=${uri.path} existe=${srcFile?.exists()} size=${srcFile?.length()}")
    val valid = srcFile != null && srcFile.exists() && srcFile.length() > 0
    if (valid) {
        java.io.FileInputStream(srcFile).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return valid
}

private fun copyContentUriToCache(uri: Uri, context: android.content.Context, cacheFile: File): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false
} catch (e: Exception) {
    Timber.e("PdfViewer: error openInputStream → ${e.message}")
    false
}

private fun renderCachedPdfPages(cacheFile: File): List<PdfPageBitmap> {
    val fileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val pdfRenderer = PdfRenderer(fileDescriptor)
    val pages       = mutableListOf<PdfPageBitmap>()

    for (i in 0 until pdfRenderer.pageCount) {
        val page   = pdfRenderer.openPage(i)
        val bitmap = Bitmap.createBitmap(
            page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pages.add(PdfPageBitmap(bitmap, page.width.toFloat(), page.height.toFloat()))
        page.close()
    }

    pdfRenderer.close()
    fileDescriptor.close()
    return pages
}

// RF-VIS-08: color del resaltado de búsqueda -- amarillo semitransparente,
// mismo tono que usan la mayoría de lectores/navegadores para esto.
private val PdfHighlightColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)

// ── Visor de PDF ──────────────────────────────────────────────────────────────
@Composable
private fun PdfViewerContent(
    uri          : Uri?,
    targetPage   : Int?,
    highlights   : Map<Int, List<PdfMatchRect>>,
    onPageChanged: (Int, Int) -> Unit,
    onTap        : () -> Unit
) {
    val context   = LocalContext.current
    var pages     by remember { mutableStateOf<List<PdfPageBitmap>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var scale     by remember { mutableFloatStateOf(1f) }
    var offsetX   by remember { mutableFloatStateOf(0f) }
    var offsetY   by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale   = (scale * zoom).coerceIn(0.5f, 4f)
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
        contentPadding      = PaddingValues(top = 100.dp, bottom = 100.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, pageBitmap ->
            LaunchedEffect(index, pages.size) {
                onPageChanged(index, pages.size)
            }
            val pageHighlights = highlights[index + 1]
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = MaterialTheme.shapes.small,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    bitmap             = pageBitmap.bitmap.asImageBitmap(),
                    contentDescription = "Página ${index + 1}",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            // RF-VIS-08: resaltado inline -- convierte cada
                            // coincidencia de puntos PDF (origen abajo-izquierda)
                            // a píxeles de pantalla (origen arriba-izquierda),
                            // inverso exacto de mapOcrBoxToPdf en OcrPdfUseCase.
                            if (!pageHighlights.isNullOrEmpty()) {
                                val displayScale = size.width / pageBitmap.pageWidthPts
                                pageHighlights.forEach { r ->
                                    val screenX = r.xPts * displayScale
                                    val screenY = (pageBitmap.pageHeightPts - (r.yPts + r.heightPts)) * displayScale
                                    drawRect(
                                        color   = PdfHighlightColor,
                                        topLeft = Offset(screenX, screenY),
                                        size    = ComposeSize(r.widthPts * displayScale, r.heightPts * displayScale)
                                    )
                                }
                            }
                        }
                )
            }
        }
    }
}

// ── Visor de Word ─────────────────────────────────────────────────────────────
// Antes se descartaba <w:rPr> por completo al extraer el texto de cada
// párrafo -- se veía como texto plano aunque el .docx tuviera negrita/
// cursiva/tamaño reales. Ahora cada <w:r> (run) del párrafo se procesa por
// separado conservando su propio formato, igual que ya hace PdfToWordUseCase
// al reconstruir un .docx desde un PDF (RF-CONV-09) -- incluso dentro de un
// mismo párrafo, una palabra en negrita queda en negrita, no todo el bloque.
internal data class WordRun(val text: String, val bold: Boolean, val italic: Boolean, val fontSizeSp: Int?)
internal data class WordParagraph(val runs: List<WordRun>, val isHeading: Boolean)

// Hallazgo real verificado en dispositivo con un .docx generado por Word en
// español: el identificador de estilo interno (w:pStyle w:val) NO siempre es
// en inglés como se asumía -- Word en español escribió "Ttulo1" (con tilde
// quitada), no "Heading1". Mismo tipo de problema ya documentado para .doc/
// HWPF en RF-CONV-07 (WordFormatDetection.kt$isHeadingStyleName), aplicado
// acá con case-insensitive + variantes es/de/ru en vez de una lista de
// mayúsculas/minúsculas explícitas.
internal val WORD_HEADING_STYLE_REGEX = Regex(
    "w:val=\"(heading|title|titulo|ttulo|berschrift|titel|заголовок|название|h[123456])",
    RegexOption.IGNORE_CASE
)

internal fun parseWordRuns(paraXml: String): List<WordRun> {
    val runRegex = Regex("<w:r[ >](.*?)</w:r>", RegexOption.DOT_MATCHES_ALL)
    val rPrRegex = Regex("<w:rPr>(.*?)</w:rPr>", RegexOption.DOT_MATCHES_ALL)
    val textRegex = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
    val szRegex = Regex("""<w:sz\s+w:val="(\d+)"""")

    fun isToggledOn(rPr: String, tag: String): Boolean {
        val m = Regex("""<w:$tag(?:\s+w:val="([^"]*)")?\s*/>""").find(rPr) ?: return false
        val v = m.groupValues[1]
        return v.isBlank() || v == "1" || v.equals("true", ignoreCase = true) || v.equals("on", ignoreCase = true)
    }

    return runRegex.findAll(paraXml).mapNotNull { runMatch ->
        val runXml = runMatch.value
        val text = textRegex.findAll(runXml).joinToString("") { it.groupValues[1] }
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&nbsp;", " ")
        if (text.isBlank()) return@mapNotNull null

        val rPr = rPrRegex.find(runXml)?.groupValues?.get(1) ?: ""
        WordRun(
            text       = text,
            bold       = isToggledOn(rPr, "b"),
            italic     = isToggledOn(rPr, "i"),
            fontSizeSp = szRegex.find(rPr)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 2 }
        )
    }.toList()
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

    var paragraphs by remember { mutableStateOf<List<WordParagraph>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var hasError   by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        paragraphs = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zip    = ZipInputStream(input)
                    var entry  = zip.nextEntry
                    val result = mutableListOf<WordParagraph>()

                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xml       = zip.readBytes().toString(Charsets.UTF_8)
                            val paraRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)

                            paraRegex.findAll(xml).forEach { match ->
                                val paraXml   = match.value
                                val isHeading = paraXml.contains(WORD_HEADING_STYLE_REGEX)
                                val runs = parseWordRuns(paraXml)
                                if (runs.any { it.text.isNotBlank() }) result.add(WordParagraph(runs, isHeading))
                            }
                            break
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()
                    result
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e("Error leyendo Word: ${e.message}")
                hasError = true
                emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    // Filtrar por búsqueda
    val paragraphPlainText = { p: WordParagraph -> p.runs.joinToString("") { it.text } }
    val displayParagraphs = if (searchQuery.isBlank()) paragraphs
    else paragraphs.filter { paragraphPlainText(it).contains(searchQuery, ignoreCase = true) }

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
            hasError || paragraphs.isEmpty() -> Column(
                modifier            = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text      = "No se pudo leer el contenido del archivo Word",
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
                            text     = "${displayParagraphs.size} resultado(s) para \"$searchQuery\"",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                itemsIndexed(displayParagraphs) { _, para ->
                    val bgColor = if (searchQuery.isNotBlank())
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.background

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

// ── Visor de Excel ────────────────────────────────────────────────────────────
// Antes cada fila se mostraba como UN string con las celdas unidas por
// "  |  " -- se veía como texto plano, no como una hoja de cálculo. Ahora
// cada celda es su propia columna de ancho fijo dentro de un Row con scroll
// horizontal COMPARTIDO entre todas las filas (mismo ScrollState en cada
// una) para que se desplacen juntas como una grilla real, no una lista de
// filas independientes.
private val EXCEL_COLUMN_WIDTH = 120.dp

private data class ExcelRow(val cells: List<String>)

@Composable
private fun ExcelViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context = LocalContext.current

    var rows      by remember { mutableStateOf<List<ExcelRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        rows = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zip       = ZipInputStream(input)
                    var entry     = zip.nextEntry
                    var sharedXml = ""
                    var sheet1Xml = ""

                    while (entry != null) {
                        when (entry.name) {
                            "xl/sharedStrings.xml"     -> sharedXml = zip.readBytes().toString(Charsets.UTF_8)
                            "xl/worksheets/sheet1.xml" -> sheet1Xml = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()

                    val sharedStrings = mutableListOf<String>()
                    val tRegex = Regex("<t(?:\\s[^>]*)?>([^<]*)</t>")
                    tRegex.findAll(sharedXml).forEach { match ->
                        sharedStrings.add(
                            match.groupValues[1]
                                .replace("&amp;", "&").replace("&lt;", "<")
                                .replace("&gt;", ">").replace("&quot;", "\"").trim()
                        )
                    }

                    val result    = mutableListOf<ExcelRow>()
                    val rowRegex  = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                    val cellRegex = Regex("<c[^>]*>(.*?)</c>",     RegexOption.DOT_MATCHES_ALL)
                    val vRegex    = Regex("<v>([^<]*)</v>")

                    rowRegex.findAll(sheet1Xml).forEach { rowMatch ->
                        val rowCells = mutableListOf<String>()
                        cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                            val cellXml      = cellMatch.value
                            val typeAttr     = Regex("""t="([^"]*)"""").find(cellXml)?.groupValues?.get(1) ?: ""
                            val vValue       = vRegex.find(cellXml)?.groupValues?.get(1)?.trim() ?: ""
                            val displayValue = when (typeAttr) {
                                "s"                -> { val idx = vValue.toIntOrNull() ?: -1; if (idx >= 0 && idx < sharedStrings.size) sharedStrings[idx] else "" }
                                "b"                -> if (vValue == "1") "TRUE" else "FALSE"
                                "str","inlineStr"  -> vValue
                                else               -> vValue
                            }
                            rowCells.add(displayValue)
                        }
                        if (rowCells.any { it.isNotBlank() }) result.add(ExcelRow(rowCells))
                    }
                    result
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e("Error leyendo Excel: ${e.message}")
                hasError = true
                emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    val displayRows = if (searchQuery.isBlank()) rows
    else rows.filter { row -> row.cells.any { it.contains(searchQuery, ignoreCase = true) } }
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
            hasError || rows.isEmpty() -> Text(
                text      = "No se pudo leer el contenido del archivo Excel",
                modifier  = Modifier.align(Alignment.Center).padding(32.dp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 100.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text     = "${displayRows.size} resultado(s) para \"$searchQuery\"",
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
@Composable
private fun PptViewerContent(
    uri        : Uri?,
    searchQuery: String = "",
    onTap      : () -> Unit
) {
    val context = LocalContext.current

    data class SlideContent(val number: Int, val title: String, val body: String)

    var slides    by remember { mutableStateOf<List<SlideContent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        slides = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zip      = ZipInputStream(input)
                    var entry    = zip.nextEntry
                    val slideMap = mutableMapOf<Int, SlideContent>()

                    while (entry != null) {
                        if (entry.name.startsWith("ppt/slides/slide") &&
                            entry.name.endsWith(".xml") &&
                            !entry.name.contains("_rels")
                        ) {
                            val num       = entry.name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                            val xml       = zip.readBytes().toString(Charsets.UTF_8)
                            val paraRegex = Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
                            val lines     = paraRegex.findAll(xml).mapNotNull { match ->
                                val text = match.value
                                    .replace(Regex("<a:rPr[^/]*/?>|</a:rPr>"), "")
                                    .replace(Regex("<[^>]+>"), "")
                                    .replace("&lt;","<").replace("&gt;",">").replace("&amp;","&")
                                    .replace(Regex("\\s+"), " ").trim()
                                if (text.isNotBlank()) text else null
                            }.toList()

                            if (lines.isNotEmpty()) {
                                slideMap[num] = SlideContent(num, lines.first(), lines.drop(1).joinToString("\n"))
                            }
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()
                    slideMap.toSortedMap().values.toList()
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e("Error leyendo PPT: ${e.message}")
                emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    val displaySlides = if (searchQuery.isBlank()) slides
    else slides.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.body.contains(searchQuery, ignoreCase = true)
    }

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
                text      = "No se pudo leer el contenido de la presentación",
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
                            text     = "${displaySlides.size} slide(s) con \"$searchQuery\"",
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
                            text     = "Diapositiva ${slide.number}",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Card(
                            modifier  = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            shape     = MaterialTheme.shapes.large,
                            colors    = CardDefaults.cardColors(
                                containerColor = if (searchQuery.isNotBlank())
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                                Text(
                                    text       = slide.title,
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary,
                                    maxLines   = 2
                                )
                                if (slide.body.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    Spacer(Modifier.height(10.dp))
                                    Column(
                                        modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        slide.body.split("\n").forEach { line ->
                                            if (line.isNotBlank()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier              = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("•", color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.bodySmall)
                                                    Text(
                                                        text       = line,
                                                        style      = MaterialTheme.typography.bodySmall,
                                                        color      = MaterialTheme.colorScheme.onSurface,
                                                        lineHeight = 18.sp,
                                                        modifier   = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
                text       = text.ifBlank { "El archivo está vacío" },
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
            text  = "Sin resultados para \"$searchQuery\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Text(
        text     = "${lines.size} resultado(s):",
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
    var password    by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

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
                text      = "Documento protegido",
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
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
            text  = "\"$fileName\" está protegido con contraseña.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Contraseña") },
            placeholder   = { Text("Ingresa la contraseña") },
            visualTransformation = if (showPassword)
                androidx.compose.ui.text.input.VisualTransformation.None
            else
                androidx.compose.ui.text.input.PasswordVisualTransformation(),
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
        Text("Abrir documento")
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
        mimeType.contains("text")                                             -> "Texto"
        else                                                                  -> "Archivo"
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

