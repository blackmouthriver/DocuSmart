package com.docsmart.features.pdftools.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.core.ui.theme.SmartBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

private const val THUMBNAIL_TARGET_WIDTH_PX = 220

@Composable
fun ReorderPagesScreen(
    selectedPdf: Uri?,
    pageOrder: List<Int>,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onPagesLoaded: (totalPages: Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRemovePage: (pageNumber: Int) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnails by remember { mutableStateOf<Map<Int, Bitmap?>>(emptyMap()) }
    var isLoadingThumbnails by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPdf) {
        if (selectedPdf == null) {
            thumbnails = emptyMap()
            return@LaunchedEffect
        }
        isLoadingThumbnails = true
        thumbnails = withContext(Dispatchers.IO) {
            loadThumbnails(context, selectedPdf, onPagesLoaded)
        }
        isLoadingThumbnails = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_reorder_pages),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_reorder_pages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_reorder_pages_ready),
            accentColor = SmartBlue
        )

        when {
            selectedPdf != null && isLoadingThumbnails -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SmartBlue)
                }
            }
            selectedPdf != null && pageOrder.isNotEmpty() -> {
                ReorderableThumbnailList(
                    pageOrder = pageOrder,
                    thumbnails = thumbnails,
                    onReorder = onReorder,
                    onRemovePage = onRemovePage
                )
            }
            else -> {}
        }

        if (selectedPdf != null) {
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null && pageOrder.isNotEmpty(),
            progressText = stringResource(R.string.pdf_reorder_pages_progress),
            buttonLabel = stringResource(R.string.pdf_reorder_pages_execute),
            buttonIcon = Icons.Rounded.Reorder,
            onExecute = onExecute,
            accentColor = SmartBlue
        )
    }
}

private suspend fun loadThumbnails(
    context: android.content.Context,
    pdfUri: Uri,
    onPagesLoaded: (Int) -> Unit
): Map<Int, Bitmap?> {
    return try {
        val file = File(context.cacheDir, "reorder_preview_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        onPagesLoaded(renderer.pageCount)

        val result = (0 until renderer.pageCount).associate { index ->
            val page = renderer.openPage(index)
            val scale = THUMBNAIL_TARGET_WIDTH_PX.toFloat() / page.width
            val width = THUMBNAIL_TARGET_WIDTH_PX
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            (index + 1) to bmp
        }
        renderer.close()
        fd.close()
        file.delete()
        result
    } catch (e: Exception) {
        Timber.e(e, "ReorderPagesScreen: error generando miniaturas")
        emptyMap()
    }
}

@Composable
private fun ReorderableThumbnailList(
    pageOrder: List<Int>,
    thumbnails: Map<Int, Bitmap?>,
    onReorder: (from: Int, to: Int) -> Unit,
    onRemovePage: (Int) -> Unit
) {
    val density = LocalDensity.current
    val itemHeightDp = 88.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val currentPageOrder by rememberUpdatedState(pageOrder)

    var draggingPage by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.pdf_reorder_pages_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pageOrder, key = { _, page -> page }) { _, pageNumber ->
                val isDragging = draggingPage == pageNumber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 8f else 0f
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auditoría de testers 2026-09-12 ("botones pequeños"): el
                    // detector de arrastre vivía directo sobre el ícono de
                    // 28dp -- peor que un botón chico, porque encima exige un
                    // gesto de arrastre preciso, no un simple toque. Se mueve
                    // el `pointerInput` a un Box de 48dp que envuelve el
                    // ícono, que se mantiene visualmente en 28dp.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(pageNumber) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingPage = pageNumber
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingPage = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingPage = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val order = currentPageOrder
                                        val fromIndex = order.indexOf(pageNumber)
                                        if (fromIndex >= 0) {
                                            val targetIndex = (fromIndex + (dragOffset / itemHeightPx).roundToInt())
                                                .coerceIn(0, order.lastIndex)
                                            if (targetIndex != fromIndex) {
                                                onReorder(fromIndex, targetIndex)
                                                dragOffset -= (targetIndex - fromIndex) * itemHeightPx
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.pdf_reorder_pages_drag_handle_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    val bmp = thumbnails[pageNumber]
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(68.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = stringResource(R.string.pdf_reorder_pages_page_label, pageNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { onRemovePage(pageNumber) },
                        enabled = pageOrder.size > 1
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.general_delete),
                            tint = if (pageOrder.size > 1)
                                ErrorRed
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
