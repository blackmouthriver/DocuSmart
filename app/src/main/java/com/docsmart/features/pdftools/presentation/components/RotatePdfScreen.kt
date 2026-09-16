package com.docsmart.features.pdftools.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@Composable
fun RotatePdfScreen(
    selectedPdf: Uri?,
    degrees: Int,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onDegreesChange: (Int) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // ── Recarga vista previa cuando cambia PDF o ángulo
    LaunchedEffect(selectedPdf, degrees) {
        if (selectedPdf == null) {
            previewBitmap = null
            return@LaunchedEffect
        }
        isLoadingPreview = true
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // `fd`/`renderer`/`page` solo se cerraban en el camino feliz (ej.
        // un PDF con contraseña de propietario -- PdfRenderer no lo abre,
        // aunque iText sí -- o de 0 páginas -- openPage(0) sin chequear
        // pageCount lanza de inmediato), y el archivo temporal `file`
        // nunca se borraba ni siquiera en el camino feliz. `.use{}`
        // anidado + `finally { file.delete() }`, mismo patrón ya usado en
        // OcrPdfUseCase.
        previewBitmap = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
            try {
                context.contentResolver.openInputStream(selectedPdf)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount == 0) return@withContext null
                        renderer.openPage(0).use { page ->
                            val bmp = Bitmap.createBitmap(
                                page.width, page.height, Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Aplica rotación real al Bitmap
                            val matrix = android.graphics.Matrix().apply {
                                postRotate(degrees.toFloat())
                            }
                            val rotated = Bitmap.createBitmap(
                                bmp, 0, 0, bmp.width, bmp.height, matrix, true
                            )
                            bmp.recycle()
                            rotated
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "RotatePdfScreen: error generando vista previa")
                null
            } finally {
                file.delete()
            }
        }
        isLoadingPreview = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Título ────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_rotate),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_rotate_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Selector PDF ──────────────────────────────
        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_rotate_ready),
            accentColor = WarningAmber
        )

        // ── Card: Vista previa + controles ────────────
        val shape = MaterialTheme.shapes.large
        Box(
            modifier = Modifier
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.pdf_rotate_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Vista previa ──────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // Sin PDF seleccionado
                        selectedPdf == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    tint = WarningAmber.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = stringResource(R.string.pdf_rotate_preview_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Cargando preview
                        isLoadingPreview || previewBitmap == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = WarningAmber
                                )
                                Text(
                                    text = stringResource(R.string.pdf_rotate_loading_preview),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Vista previa — Bitmap ya rotado
                        else -> {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.pdf_rotate_preview_desc),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                // ── Chips de ángulo ───────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(90, 180, 270).forEach { angle ->
                        FilterChip(
                            selected = degrees == angle,
                            onClick = { onDegreesChange(angle) },
                            label = {
                                Text(
                                    text = "${angle}°",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (degrees == angle) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarningAmber.copy(alpha = 0.2f),
                                selectedLabelColor = WarningAmber
                            )
                        )
                    }
                }

                // ── Descripción del ángulo ────────────
                Text(
                    text = when (degrees) {
                        90  -> stringResource(R.string.pdf_rotate_angle_90)
                        180 -> stringResource(R.string.pdf_rotate_angle_180)
                        270 -> stringResource(R.string.pdf_rotate_angle_270)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Nombre del archivo ────────────────────────
        if (selectedPdf != null) {
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        // ── Progreso o botón ──────────────────────────
        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null,
            progressText = stringResource(R.string.pdf_rotate_progress, degrees),
            buttonLabel = stringResource(R.string.pdf_rotate_execute, degrees),
            buttonIcon = Icons.Rounded.RotateRight,
            onExecute = onExecute,
            accentColor = WarningAmber
        )
    }
}