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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Draw
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

        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_sign_ready),
            accentColor = NavyDark
        )

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

            SignatureCanvas(hasSignature = hasSignature, onSignatureCaptured = onSignatureCaptured)

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

        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null && hasSignature,
            progressText = stringResource(R.string.pdf_sign_progress),
            buttonLabel = stringResource(R.string.pdf_sign_execute),
            buttonIcon = Icons.Rounded.Draw,
            onExecute = onExecute,
            accentColor = NavyDark
        )
    }
}

@Composable
private fun SignatureCanvas(hasSignature: Boolean, onSignatureCaptured: (ByteArray) -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Hallazgo real de la revisión general 2026-09-16: "Limpiar" solo
    // borraba signatureImageBytes en el ViewModel -- el trazo dibujado acá
    // (estado local del Canvas) nunca se reiniciaba, así que el dibujo
    // seguía visible y se reincorporaba al próximo trazo (o se mezclaba
    // entre documentos distintos al cambiar de PDF, que también pone
    // hasSignature en false). hasSignature ya refleja ambos casos -- limpiar
    // el lienzo cuando pasa a false cubre los dos sin parámetros nuevos.
    LaunchedEffect(hasSignature) {
        if (!hasSignature) {
            strokes = emptyList()
            currentStroke = emptyList()
        }
    }

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
