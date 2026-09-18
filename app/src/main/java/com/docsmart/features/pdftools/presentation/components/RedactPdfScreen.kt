package com.docsmart.features.pdftools.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.features.pdftools.domain.usecase.RedactionRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PAGE_PREVIEW_TARGET_WIDTH_PX = 900
private const val MIN_RECT_FRACTION = 0.015f

@Composable
fun RedactPdfScreen(
    selectedPdf: Uri?,
    currentPage: Int,
    totalPages: Int,
    rects: List<RedactionRect>,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onTotalPagesLoaded: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onAddRect: (RedactionRect) -> Unit,
    onUndoLastRect: () -> Unit,
    onClearRects: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPdf, currentPage) {
        if (selectedPdf == null) {
            pageBitmap = null
            return@LaunchedEffect
        }
        isLoadingPage = true
        pageBitmap = withContext(Dispatchers.IO) {
            loadPage(context, selectedPdf, currentPage - 1, onTotalPagesLoaded)
        }
        isLoadingPage = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_redact),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_redact_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_redact_ready),
            accentColor = ErrorRed
        )

        if (selectedPdf != null) {
            RedactPageEditor(
                currentPage = currentPage,
                totalPages = totalPages,
                pageBitmap = pageBitmap,
                isLoadingPage = isLoadingPage,
                rectsForPage = rects.filter { it.pageNumber == currentPage },
                onPageChange = onPageChange,
                onAddRect = onAddRect
            )

            RedactRectsSummary(
                totalRects = rects.size,
                onUndoLastRect = onUndoLastRect,
                onClearRects = onClearRects
            )

            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null && rects.isNotEmpty(),
            progressText = stringResource(R.string.pdf_redact_progress),
            buttonLabel = stringResource(R.string.pdf_redact_execute),
            buttonIcon = Icons.Rounded.VisibilityOff,
            onExecute = onExecute,
            accentColor = ErrorRed
        )
    }
}

@Composable
private fun RedactPageEditor(
    currentPage: Int,
    totalPages: Int,
    pageBitmap: Bitmap?,
    isLoadingPage: Boolean,
    rectsForPage: List<RedactionRect>,
    onPageChange: (Int) -> Unit,
    onAddRect: (RedactionRect) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── Navegación de páginas ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 1) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.pdf_redact_prev_page)
                )
            }
            Text(
                text = stringResource(R.string.pdf_redact_page_indicator, currentPage, totalPages),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.pdf_redact_next_page)
                )
            }
        }

        // ── Vista previa con overlay de dibujo ─────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoadingPage || pageBitmap == null -> {
                    Column(
                        modifier = Modifier.padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = ErrorRed)
                        Text(
                            text = stringResource(R.string.pdf_redact_loading_preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val bitmap = pageBitmap
                    var dragStart by remember(currentPage) { mutableStateOf<Offset?>(null) }
                    var dragCurrent by remember(currentPage) { mutableStateOf<Offset?>(null) }

                    val redactCanvasDescription = stringResource(
                        R.string.pdf_redact_canvas_desc, rectsForPage.size
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            // Hallazgo real de la auditoría general
                            // 2026-09-17/18 (décima ronda, Alta -- H2):
                            // la única forma de agregar una zona de
                            // censura es el gesto de arrastre, sin
                            // ninguna semántica -- TalkBack solo
                            // anunciaba "Vista previa de la página" del
                            // Image de abajo, sin indicar que el gesto
                            // existe ni cuántas zonas ya hay marcadas.
                            // No hay una alternativa accesible real para
                            // marcar coordenadas arbitrarias sin un
                            // gesto de arrastre (a diferencia de Firmar,
                            // que sí admite escribir el nombre) -- se
                            // documenta la limitación en el string en
                            // vez de fingir que este cambio la resuelve
                            // del todo, y al menos se anuncia el estado
                            // (cuántas zonas hay) y la instrucción.
                            .semantics(mergeDescendants = true) {
                                contentDescription = redactCanvasDescription
                            }
                            .pointerInput(currentPage) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStart = offset
                                        dragCurrent = offset
                                    },
                                    onDrag = { change, _ -> dragCurrent = change.position },
                                    onDragEnd = {
                                        val start = dragStart
                                        val end = dragCurrent
                                        if (start != null && end != null) {
                                            val xFrac = (min(start.x, end.x) / size.width).coerceIn(0f, 1f)
                                            val yFrac = (min(start.y, end.y) / size.height).coerceIn(0f, 1f)
                                            val wFrac = (abs(end.x - start.x) / size.width).coerceIn(0f, 1f)
                                            val hFrac = (abs(end.y - start.y) / size.height).coerceIn(0f, 1f)
                                            if (wFrac > MIN_RECT_FRACTION && hFrac > MIN_RECT_FRACTION) {
                                                onAddRect(RedactionRect(currentPage, xFrac, yFrac, wFrac, hFrac))
                                            }
                                        }
                                        dragStart = null
                                        dragCurrent = null
                                    },
                                    onDragCancel = {
                                        dragStart = null
                                        dragCurrent = null
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.pdf_redact_preview_desc),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.matchParentSize()
                        )
                        Canvas(modifier = Modifier.matchParentSize()) {
                            rectsForPage.forEach { rect ->
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    topLeft = Offset(rect.xFrac * size.width, rect.yFrac * size.height),
                                    size = Size(rect.wFrac * size.width, rect.hFrac * size.height)
                                )
                            }
                            val start = dragStart
                            val end = dragCurrent
                            if (start != null && end != null) {
                                drawRect(
                                    color = ErrorRed.copy(alpha = 0.4f),
                                    topLeft = Offset(min(start.x, end.x), min(start.y, end.y)),
                                    size = Size(abs(end.x - start.x), abs(end.y - start.y)),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.pdf_redact_draw_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RedactRectsSummary(
    totalRects: Int,
    onUndoLastRect: () -> Unit,
    onClearRects: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.pdf_redact_zones_marked, totalRects),
            style = MaterialTheme.typography.labelMedium,
            color = if (totalRects > 0) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onUndoLastRect, enabled = totalRects > 0) {
                Text(text = stringResource(R.string.pdf_redact_undo), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onClearRects, enabled = totalRects > 0) {
                Text(text = stringResource(R.string.pdf_redact_clear_all), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): `fd`/
// `renderer`/`page` solo se cerraban y `file` solo se borraba en el camino
// feliz. Además, `pageIndex.coerceIn(0, renderer.pageCount - 1)` con un
// PDF de 0 páginas evalúa `coerceIn(0, -1)` -- rango vacío, lanza
// IllegalArgumentException -- así que un PDF de 0 páginas ya disparaba la
// excepción (y por lo tanto el leak) en cada intento. `.use{}` anidado +
// `finally { file.delete() }`, con el chequeo explícito de 0 páginas antes
// del coerceIn.
private fun renderRedactPageBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap? {
    if (renderer.pageCount == 0) return null
    val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
    return renderer.openPage(safeIndex).use { page ->
        val scale = PAGE_PREVIEW_TARGET_WIDTH_PX.toFloat() / page.width
        val width = PAGE_PREVIEW_TARGET_WIDTH_PX
        val height = max((page.height * scale).toInt(), 1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bmp
    }
}

private fun loadPage(
    context: android.content.Context,
    pdfUri: Uri,
    pageIndex: Int,
    onTotalPagesLoaded: (Int) -> Unit
): Bitmap? {
    val file = File(context.cacheDir, "redact_preview_${System.currentTimeMillis()}.pdf")
    return try {
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                onTotalPagesLoaded(renderer.pageCount)
                renderRedactPageBitmap(renderer, pageIndex)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "RedactPdfScreen: error generando vista previa de página")
        null
    } finally {
        file.delete()
    }
}
