package com.docsmart.features.viewer.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.features.viewer.presentation.components.ViewerBottomBar
import com.docsmart.features.viewer.presentation.components.ViewerTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

@Composable
fun ViewerScreen(
    documentId: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId, context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error ?: stringResource(R.string.viewer_error),
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

                when {
                    mime.contains("image") ||
                            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                            fileName.endsWith(".png") || fileName.endsWith(".webp") ||
                            fileName.endsWith(".gif") -> {
                        ImageViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("pdf") || fileName.endsWith(".pdf") -> {
                        PdfViewerContent(
                            uri           = fileUri,
                            onPageChanged = { page, total -> viewModel.onPageChanged(page, total) },
                            onTap         = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("word") || mime.contains("msword") ||
                            mime.contains("wordprocessingml") ||
                            fileName.endsWith(".doc") || fileName.endsWith(".docx") -> {
                        WordViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("excel") || mime.contains("spreadsheet") ||
                            mime.contains("ms-excel") || mime.contains("sheet") ||
                            fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> {
                        ExcelViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("powerpoint") || mime.contains("presentation") ||
                            fileName.endsWith(".ppt") || fileName.endsWith(".pptx") -> {
                        PptViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mime.contains("text") ||
                            fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                            fileName.endsWith(".csv") -> {
                        TextViewerContent(
                            uri   = fileUri,
                            onTap = { viewModel.toggleControls() }
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

        uiState.document?.let { doc ->
            ViewerTopBar(
                fileName        = doc.name,
                isFavorite      = uiState.isFavorite,
                visible         = uiState.showControls,
                onBackClick     = onBack,
                onFavoriteClick = { viewModel.toggleFavorite() },
                onShareClick    = { viewModel.shareDocument(context) },
                onSearchClick   = { },
                modifier        = Modifier.align(Alignment.TopCenter)
            )
        }

        ViewerBottomBar(
            currentPage = uiState.currentPage,
            totalPages  = uiState.totalPages,
            visible     = uiState.showControls,
            modifier    = Modifier.align(Alignment.BottomCenter)
        )
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
                    .padding(top = 92.dp, bottom = 92.dp) // ← FIX
            )
        }
    }
}

// ── Visor de PDF ──────────────────────────────────────────────────────────────
@Composable
private fun PdfViewerContent(
    uri          : Uri?,
    onPageChanged: (Int, Int) -> Unit,
    onTap        : () -> Unit
) {
    val context   = LocalContext.current
    var pages     by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var scale     by remember { mutableFloatStateOf(1f) }
    var offsetX   by remember { mutableFloatStateOf(0f) }
    var offsetY   by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        pages = withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "temp_preview.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext emptyList()

                val fileDescriptor = ParcelFileDescriptor.open(
                    cacheFile, ParcelFileDescriptor.MODE_READ_ONLY
                )
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val bitmaps     = mutableListOf<Bitmap>()

                for (i in 0 until pdfRenderer.pageCount) {
                    val page   = pdfRenderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }

                pdfRenderer.close()
                fileDescriptor.close()
                bitmaps
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
        contentPadding = PaddingValues(
            top    = 100.dp, bottom = 100.dp, // ← FIX
            start  = 8.dp,  end    = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            LaunchedEffect(index, pages.size) {
                onPageChanged(index, pages.size)
            }
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = MaterialTheme.shapes.small,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = "Página ${index + 1}",
                    modifier           = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Visor de Word ─────────────────────────────────────────────────────────────
@Composable
private fun WordViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current

    data class WordParagraph(val text: String, val isHeading: Boolean)

    var paragraphs by remember { mutableStateOf<List<WordParagraph>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var hasError   by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        paragraphs = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zip   = ZipInputStream(input)
                    var entry = zip.nextEntry
                    val result = mutableListOf<WordParagraph>()

                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xml        = zip.readBytes().toString(Charsets.UTF_8)
                            val paraRegex  = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)

                            paraRegex.findAll(xml).forEach { match ->
                                val paraXml   = match.value
                                val isHeading = paraXml.contains(Regex(
                                    "w:val=\"(Heading|heading|Title|title|Titulo|H[123456])"
                                ))
                                val text = paraXml
                                    .replace(Regex("<w:rPr>.*?</w:rPr>", RegexOption.DOT_MATCHES_ALL), "")
                                    .replace(Regex("<w:pPr>.*?</w:pPr>", RegexOption.DOT_MATCHES_ALL), "")
                                    .replace(Regex("<[^>]+>"), "")
                                    .replace("&lt;", "<")
                                    .replace("&gt;", ">")
                                    .replace("&amp;", "&")
                                    .replace("&nbsp;", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                if (text.isNotBlank()) result.add(WordParagraph(text, isHeading))
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top    = 100.dp, bottom = 100.dp, // ← FIX
                    start  = 20.dp, end    = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(paragraphs) { _, para ->
                    if (para.isHeading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text       = para.text,
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
                            text       = para.text,
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp,
                            modifier   = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Visor de Excel ────────────────────────────────────────────────────────────
@Composable
private fun ExcelViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current

    data class ExcelCell(val value: String, val row: Int)

    var cells     by remember { mutableStateOf<List<ExcelCell>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError  by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        cells = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zip       = ZipInputStream(input)
                    var entry     = zip.nextEntry
                    var sharedXml = ""
                    var sheet1Xml = ""

                    while (entry != null) {
                        when (entry.name) {
                            "xl/sharedStrings.xml"        -> sharedXml = zip.readBytes().toString(Charsets.UTF_8)
                            "xl/worksheets/sheet1.xml"    -> sheet1Xml = zip.readBytes().toString(Charsets.UTF_8)
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

                    val result    = mutableListOf<ExcelCell>()
                    var rowNum    = 0
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
                                "s"                      -> { val idx = vValue.toIntOrNull() ?: -1; if (idx >= 0 && idx < sharedStrings.size) sharedStrings[idx] else "" }
                                "b"                      -> if (vValue == "1") "TRUE" else "FALSE"
                                "str", "inlineStr"       -> vValue
                                else                     -> vValue
                            }
                            if (displayValue.isNotBlank()) rowCells.add(displayValue)
                        }
                        if (rowCells.isNotEmpty()) {
                            result.add(ExcelCell(rowCells.joinToString("  |  "), rowNum))
                            rowNum++
                        }
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
            hasError || cells.isEmpty() -> Text(
                text      = "No se pudo leer el contenido del archivo Excel",
                modifier  = Modifier.align(Alignment.Center).padding(32.dp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 100.dp) // ← FIX
            ) {
                val header = cells.firstOrNull()
                val data   = if (cells.size > 1) cells.drop(1) else cells

                if (header != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text       = header.value,
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                itemsIndexed(data) { index, cell ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text  = cell.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

// ── Visor de PowerPoint ───────────────────────────────────────────────────────
@Composable
private fun PptViewerContent(uri: Uri?, onTap: () -> Unit) {
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
                                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
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
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top    = 100.dp, bottom = 100.dp, // ← FIX
                    start  = 16.dp, end    = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(slides) { _, slide ->
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = MaterialTheme.shapes.large,
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                            topStart = 16.dp, topEnd = 16.dp
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text       = "${slide.number}",
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text       = slide.title,
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier   = Modifier.weight(1f)
                                )
                            }
                            if (slide.body.isNotBlank()) {
                                Column(
                                    modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    slide.body.split("\n").forEach { line ->
                                        if (line.isNotBlank()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier              = Modifier.fillMaxWidth()
                                            ) {
                                                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text       = line,
                                                    style      = MaterialTheme.typography.bodyMedium,
                                                    color      = MaterialTheme.colorScheme.onSurface,
                                                    lineHeight = 22.sp,
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

// ── Visor de texto plano ──────────────────────────────────────────────────────
@Composable
private fun TextViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context   = LocalContext.current
    var text      by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
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
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top    = 100.dp, bottom = 100.dp, // ← FIX
                        start  = 20.dp, end    = 20.dp
                    )
            ) {
                Text(
                    text       = text.ifBlank { "El archivo está vacío" },
                    style      = MaterialTheme.typography.bodyMedium,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }
        }
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
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(formatLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(fileName,        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,        textAlign = TextAlign.Center)
            Text(unsupportedText, style = MaterialTheme.typography.bodyMedium,  color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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