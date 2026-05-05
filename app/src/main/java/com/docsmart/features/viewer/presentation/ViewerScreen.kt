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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.features.viewer.presentation.components.ViewerBottomBar
import com.docsmart.features.viewer.presentation.components.ViewerTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
                        text = uiState.error ?: "Error al abrir el documento",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Volver") }
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
private fun ImageViewerContent(
    uri: Uri?,
    onTap: () -> Unit
) {
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
                } ?: run {
                    Timber.e("openInputStream null para imagen: $uri")
                    null
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
                contentDescription = "Imagen",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, bottom = 72.dp)
            )
        }
    }
}

// ── Visor de PDF con PdfRenderer nativo ──────────────
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
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Timber.e("openInputStream null para PDF: $uri")
                    return@withContext emptyList()
                }

                val fileDescriptor = ParcelFileDescriptor.open(
                    cacheFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No se pudo abrir el documento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Renderizando documento...",
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
            top = 72.dp,
            bottom = 72.dp,
            start = 8.dp,
            end = 8.dp
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

// ── Formato no soportado ──────────────────────────────
@Composable
private fun UnsupportedFormatContent(
    mimeType: String,
    fileName: String,
    fileUri: Uri?,
    onTap: () -> Unit
) {
    val context = LocalContext.current

    val formatInfo = when {
        mimeType.contains("word") || mimeType.contains("msword") ->
            Pair("Word", "Este archivo requiere Microsoft Word o Google Docs para verse correctamente.")
        mimeType.contains("excel") || mimeType.contains("sheet") ->
            Pair("Excel", "Este archivo requiere Microsoft Excel o Google Sheets para verse correctamente.")
        mimeType.contains("powerpoint") || mimeType.contains("presentation") ->
            Pair("PowerPoint", "Este archivo requiere Microsoft PowerPoint para verse correctamente.")
        mimeType.contains("text") ->
            Pair("Texto", "Archivo de texto plano.")
        else ->
            Pair("Archivo", "Este formato no es compatible con el visor integrado.")
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
                    text = formatInfo.first,
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
                text = formatInfo.second,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (fileUri != null) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Abrir con...")
                            )
                        } catch (e: Exception) {
                            Timber.e("No se pudo abrir: ${e.message}")
                        }
                    }
                ) {
                    Text("Abrir con otra aplicación")
                }
            }
        }
    }
}