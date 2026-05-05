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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        previewBitmap = withContext(Dispatchers.IO) {
            try {
                val file = File(
                    context.cacheDir,
                    "preview_${System.currentTimeMillis()}.pdf"
                )
                context.contentResolver.openInputStream(selectedPdf)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val fd = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val renderer = PdfRenderer(fd)
                val page = renderer.openPage(0)
                val bmp = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    bmp, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()
                renderer.close()
                fd.close()

                // Aplica rotación real al Bitmap
                val matrix = android.graphics.Matrix().apply {
                    postRotate(degrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(
                    bmp, 0, 0,
                    bmp.width, bmp.height,
                    matrix, true
                )
                bmp.recycle()
                rotated

            } catch (e: Exception) {
                e.printStackTrace()
                null
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
                text = "Rotar PDF",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Rota todas las páginas del documento",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Selector PDF ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.large)
                .border(
                    width = if (selectedPdf != null) 1.dp else 1.5.dp,
                    color = if (selectedPdf != null)
                        WarningAmber
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.large
                )
                .background(
                    if (selectedPdf != null)
                        WarningAmber.copy(alpha = 0.1f)
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
                        WarningAmber
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (selectedPdf != null)
                            "PDF listo para rotar"
                        else
                            "Seleccionar PDF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedPdf != null)
                            WarningAmber
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    if (selectedPdf != null) {
                        Text(
                            text = selectedPdf.lastPathSegment
                                ?.substringAfterLast("/") ?: "archivo.pdf",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Card: Vista previa + controles ────────────
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Vista previa y ángulo de rotación",
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
                                    text = "Selecciona un PDF\npara ver la vista previa",
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
                                    text = "Cargando vista previa...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Vista previa — Bitmap ya rotado
                        else -> {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Vista previa rotada",
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
                        90  -> "Rotación 90° en sentido horario"
                        180 -> "Rotación de media vuelta (boca abajo)"
                        270 -> "Rotación 90° en sentido antihorario"
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
        if (isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = WarningAmber,
                    trackColor = WarningAmber.copy(alpha = 0.2f)
                )
                Text(
                    text = "Rotando PDF ${degrees}°...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = onExecute,
                enabled = selectedPdf != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningAmber,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.RotateRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rotar PDF ${degrees}°",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}