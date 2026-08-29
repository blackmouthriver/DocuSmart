package com.docsmart.features.pdftools.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.NavyDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File

private const val SIGNATURE_STROKE_WIDTH_PX = 6f

@Composable
fun SignPdfScreen(
    selectedPdf: Uri?,
    pageNumber: Int,
    totalPages: Int,
    hasSignature: Boolean,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onTotalPagesLoaded: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onSignatureCaptured: (ByteArray) -> Unit,
    onClearSignature: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(selectedPdf) {
        if (selectedPdf == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { loadTotalPages(context, selectedPdf, onTotalPagesLoaded) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_sign),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_sign_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SignSelectZone(selectedPdf, onSelectPdf)

        if (selectedPdf != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onPageChange(pageNumber - 1) }, enabled = pageNumber > 1) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.pdf_sign_prev_page)
                    )
                }
                Text(
                    text = stringResource(R.string.pdf_sign_page_indicator, pageNumber, totalPages),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onPageChange(pageNumber + 1) }, enabled = pageNumber < totalPages) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.pdf_sign_next_page)
                    )
                }
            }

            SignatureCanvas(onSignatureCaptured = onSignatureCaptured)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (hasSignature) R.string.pdf_sign_captured else R.string.pdf_sign_hint
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasSignature) NavyDark else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearSignature, enabled = hasSignature) {
                    Text(text = stringResource(R.string.pdf_sign_clear), style = MaterialTheme.typography.labelMedium)
                }
            }

            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        if (isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NavyDark,
                    trackColor = NavyDark.copy(alpha = 0.2f)
                )
                Text(
                    text = stringResource(R.string.pdf_sign_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = onExecute,
                enabled = selectedPdf != null && hasSignature,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyDark,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Draw,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pdf_sign_execute),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SignSelectZone(selectedPdf: Uri?, onSelectPdf: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (selectedPdf != null) 1.dp else 1.5.dp,
                color = if (selectedPdf != null)
                    NavyDark
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (selectedPdf != null)
                    NavyDark.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
            .clickable { onSelectPdf() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selectedPdf != null)
                    Icons.Rounded.CheckCircle
                else
                    Icons.Rounded.FileOpen,
                contentDescription = null,
                tint = if (selectedPdf != null)
                    NavyDark
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(
                        if (selectedPdf != null) R.string.pdf_sign_ready
                        else R.string.pdf_tools_select_pdf_prompt
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedPdf != null)
                        NavyDark
                    else
                        MaterialTheme.colorScheme.primary
                )
                if (selectedPdf != null) {
                    Text(
                        text = selectedPdf.lastPathSegment
                            ?.substringAfterLast("/") ?: stringResource(R.string.pdf_tools_default_filename),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureCanvas(onSignatureCaptured: (ByteArray) -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun publishSignature(allStrokes: List<List<Offset>>) {
        if (allStrokes.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) return
        onSignatureCaptured(strokesToPngBytes(allStrokes, canvasSize.width, canvasSize.height))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.pdf_sign_draw_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> currentStroke = listOf(offset) },
                        onDrag = { change, _ -> currentStroke = currentStroke + change.position },
                        onDragEnd = {
                            val updated = strokes + listOf(currentStroke)
                            strokes = updated
                            currentStroke = emptyList()
                            publishSignature(updated)
                        },
                        onDragCancel = { currentStroke = emptyList() }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                (strokes + listOf(currentStroke)).forEach { stroke ->
                    for (i in 0 until stroke.size - 1) {
                        drawLine(
                            color = Color.Black,
                            start = stroke[i],
                            end = stroke[i + 1],
                            strokeWidth = SIGNATURE_STROKE_WIDTH_PX
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.pdf_sign_draw_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun strokesToPngBytes(strokes: List<List<Offset>>, width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = SIGNATURE_STROKE_WIDTH_PX
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    strokes.forEach { stroke ->
        for (i in 0 until stroke.size - 1) {
            canvas.drawLine(stroke[i].x, stroke[i].y, stroke[i + 1].x, stroke[i + 1].y, paint)
        }
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

private fun loadTotalPages(context: android.content.Context, pdfUri: Uri, onTotalPagesLoaded: (Int) -> Unit) {
    try {
        val file = File(context.cacheDir, "sign_pages_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        onTotalPagesLoaded(renderer.pageCount)
        renderer.close()
        fd.close()
        file.delete()
    } catch (e: Exception) {
        Timber.e(e, "SignPdfScreen: error obteniendo el total de páginas")
    }
}
