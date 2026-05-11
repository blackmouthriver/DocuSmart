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
                val fileUri = uiState.fileUri
                val mimeType = uiState.mimeType ?: ""
                when {
                    mimeType.contains("image") -> {
                        ImageViewerContent(
                            uri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mimeType.contains("pdf") -> {
                        PdfViewerContent(
                            uri = fileUri,
                            onPageChanged = { page, total ->
                                viewModel.onPageChanged(page, total)
                            },
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mimeType.contains("word") ||
                            mimeType.contains("msword") ||
                            mimeType.contains("wordprocessingml") -> {
                        WordViewerContent(
                            uri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mimeType.contains("excel") ||
                            mimeType.contains("spreadsheet") ||
                            mimeType.contains("ms-excel") -> {
                        ExcelViewerContent(
                            uri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mimeType.contains("powerpoint") ||
                            mimeType.contains("presentation") -> {
                        PptViewerContent(
                            uri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    mimeType.contains("text") -> {
                        TextViewerContent(
                            uri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                    else -> {
                        UnsupportedFormatContent(
                            mimeType = mimeType,
                            fileName = uiState.document?.name ?: "",
                            fileUri = fileUri,
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                }
            }
        }

        uiState.document?.let { doc ->
            ViewerTopBar(
                fileName = doc.name,
                isFavorite = uiState.isFavorite,
                visible = uiState.showControls,
                onBackClick = onBack,
                onFavoriteClick = { viewModel.toggleFavorite() },
                onShareClick = { viewModel.shareDocument(context) },
                onSearchClick = { },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        ViewerBottomBar(
            currentPage = uiState.currentPage,
            totalPages = uiState.totalPages,
            visible = uiState.showControls,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Visor de imágenes ─────────────────────────────────
@Composable
private fun ImageViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
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
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, bottom = 72.dp)
            )
        }
    }
}

// ── Visor de PDF ──────────────────────────────────────
@Composable
private fun PdfViewerContent(
    uri: Uri?,
    onPageChanged: (Int, Int) -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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
                val bitmaps = mutableListOf<Bitmap>()

                for (i in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(
                        bitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
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
                text = stringResource(R.string.viewer_error),
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
                    text = stringResource(R.string.viewer_rendering),
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
                    scale = (scale * zoom).coerceIn(0.5f, 4f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentPadding = PaddingValues(
            top = 72.dp, bottom = 72.dp,
            start = 8.dp, end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            LaunchedEffect(index, pages.size) {
                onPageChanged(index, pages.size)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Página ${index + 1}",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Visor de Word ─────────────────────────────────────
// Lee el .docx como ZIP y extrae el XML sin Apache POI
@Composable
private fun WordViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        paragraphs = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zipInput = ZipInputStream(input)
                    var entry = zipInput.nextEntry
                    val result = mutableListOf<String>()

                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val content = zipInput.readBytes().toString(Charsets.UTF_8)
                            val texts = content
                                .replace(Regex("<w:p[ >]"), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&amp;", "&")
                                .replace("&nbsp;", " ")
                                .split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            result.addAll(texts)
                            break
                        }
                        entry = zipInput.nextEntry
                    }
                    zipInput.close()
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
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
            hasError || paragraphs.isEmpty() -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No se pudo leer el contenido del archivo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 80.dp, bottom = 80.dp,
                    start = 20.dp, end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(paragraphs) { _, text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// ── Visor de Excel ────────────────────────────────────
// Lee el .xlsx como ZIP y extrae strings compartidos
@Composable
private fun ExcelViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        rows = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zipInput = ZipInputStream(input)
                    var entry = zipInput.nextEntry
                    val result = mutableListOf<String>()

                    while (entry != null) {
                        if (entry.name == "xl/sharedStrings.xml") {
                            val content = zipInput.readBytes().toString(Charsets.UTF_8)
                            val texts = content
                                .split(Regex("<t>|<t "))
                                .drop(1)
                                .map { it.substringBefore("</t>").trim() }
                                .filter { it.isNotBlank() && !it.startsWith("<") }
                            result.addAll(texts)
                            break
                        }
                        entry = zipInput.nextEntry
                    }
                    zipInput.close()
                    result.distinct()
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e("Error leyendo Excel: ${e.message}")
                emptyList()
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
                color = MaterialTheme.colorScheme.primary
            )
            rows.isEmpty() -> Text(
                text = "No se pudo leer el contenido",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 80.dp, bottom = 80.dp,
                    start = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(rows) { index, cell ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

// ── Visor de PowerPoint ───────────────────────────────
// Lee el .pptx como ZIP y extrae el texto de cada slide
@Composable
private fun PptViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var slides by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        slides = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val zipInput = ZipInputStream(input)
                    var entry = zipInput.nextEntry
                    val slideMap = mutableMapOf<Int, String>()

                    while (entry != null) {
                        if (entry.name.startsWith("ppt/slides/slide") &&
                            entry.name.endsWith(".xml") &&
                            !entry.name.contains("_rels")
                        ) {
                            val slideNum = entry.name
                                .removePrefix("ppt/slides/slide")
                                .removeSuffix(".xml")
                                .toIntOrNull() ?: 0

                            val content = zipInput.readBytes().toString(Charsets.UTF_8)
                            val text = content
                                .replace(Regex("<a:p[ >]"), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&amp;", "&")
                                .split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .joinToString("\n")

                            if (text.isNotBlank()) {
                                slideMap[slideNum] = text
                            }
                        }
                        entry = zipInput.nextEntry
                    }
                    zipInput.close()
                    slideMap.toSortedMap().entries.map { Pair(it.key, it.value) }
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
            .clickable { onTap() }
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
            slides.isEmpty() -> Text(
                text = "No se pudo leer el contenido",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 80.dp, bottom = 80.dp,
                    start = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(slides) { _, slide ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Slide ${slide.first}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp, vertical = 4.dp
                                    )
                                )
                            }
                            Text(
                                text = slide.second,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Visor de texto plano ──────────────────────────────
@Composable
private fun TextViewerContent(uri: Uri?, onTap: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
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
                color = MaterialTheme.colorScheme.primary
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = 80.dp, bottom = 80.dp,
                        start = 20.dp, end = 20.dp
                    )
            ) {
                Text(
                    text = text.ifBlank { "El archivo está vacío" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

// ── Formato no soportado ──────────────────────────────
@Composable
private fun UnsupportedFormatContent(
    mimeType: String,
    fileName: String,
    fileUri: Uri?,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val openWithText = stringResource(R.string.viewer_open_other)
    val unsupportedText = stringResource(R.string.viewer_unsupported)

    val formatLabel = when {
        mimeType.contains("word") || mimeType.contains("msword") -> "Word"
        mimeType.contains("excel") || mimeType.contains("sheet") -> "Excel"
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "PowerPoint"
        mimeType.contains("text") -> "Texto"
        else -> "Archivo"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = unsupportedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (fileUri != null) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, openWithText)
                            )
                        } catch (e: Exception) {
                            Timber.e("No se pudo abrir: ${e.message}")
                        }
                    }
                ) {
                    Text(openWithText)
                }
            }
        }
    }
}