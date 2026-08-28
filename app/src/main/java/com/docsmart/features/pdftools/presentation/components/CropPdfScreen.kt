package com.docsmart.features.pdftools.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.docsmart.core.ui.theme.PremiumGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private const val MAX_MARGIN_PERCENT = 40

@Composable
fun CropPdfScreen(
    selectedPdf: Uri?,
    marginPercent: Int,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onMarginChange: (Int) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPdf) {
        if (selectedPdf == null) {
            originalBitmap = null
            return@LaunchedEffect
        }
        isLoadingPreview = true
        originalBitmap = withContext(Dispatchers.IO) { loadFirstPage(context, selectedPdf) }
        isLoadingPreview = false
    }

    val croppedBitmap = remember(originalBitmap, marginPercent) {
        originalBitmap?.let { bmp ->
            val marginX = (bmp.width * marginPercent / 100f).toInt()
            val marginY = (bmp.height * marginPercent / 100f).toInt()
            val width = (bmp.width - 2 * marginX).coerceAtLeast(1)
            val height = (bmp.height - 2 * marginY).coerceAtLeast(1)
            Bitmap.createBitmap(bmp, marginX, marginY, width, height)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_crop),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_crop_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Selector PDF ──────────────────────────────
        CropSelectZone(selectedPdf, onSelectPdf)

        // ── Vista previa + control de margen ───────────
        CropPreviewCard(
            selectedPdf = selectedPdf,
            isLoadingPreview = isLoadingPreview,
            croppedBitmap = croppedBitmap,
            marginPercent = marginPercent,
            onMarginChange = onMarginChange
        )

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
                    color = PremiumGold,
                    trackColor = PremiumGold.copy(alpha = 0.2f)
                )
                Text(
                    text = stringResource(R.string.pdf_crop_progress),
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
                    containerColor = PremiumGold,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Crop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pdf_crop_execute),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CropSelectZone(selectedPdf: Uri?, onSelectPdf: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (selectedPdf != null) 1.dp else 1.5.dp,
                color = if (selectedPdf != null)
                    PremiumGold
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (selectedPdf != null)
                    PremiumGold.copy(alpha = 0.1f)
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
                    PremiumGold
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(
                        if (selectedPdf != null) R.string.pdf_crop_ready
                        else R.string.pdf_tools_select_pdf_prompt
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedPdf != null)
                        PremiumGold
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
private fun CropPreviewCard(
    selectedPdf: Uri?,
    isLoadingPreview: Boolean,
    croppedBitmap: Bitmap?,
    marginPercent: Int,
    onMarginChange: (Int) -> Unit
) {
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
                text = stringResource(R.string.pdf_crop_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    selectedPdf == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Crop,
                                contentDescription = null,
                                tint = PremiumGold.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.pdf_crop_preview_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    isLoadingPreview || croppedBitmap == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = PremiumGold)
                            Text(
                                text = stringResource(R.string.pdf_crop_loading_preview),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Image(
                            bitmap = croppedBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.pdf_crop_preview_desc),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            Slider(
                value = marginPercent.toFloat(),
                onValueChange = { onMarginChange(it.toInt()) },
                valueRange = 0f..MAX_MARGIN_PERCENT.toFloat(),
                steps = MAX_MARGIN_PERCENT - 1,
                colors = SliderDefaults.colors(
                    thumbColor = PremiumGold,
                    activeTrackColor = PremiumGold
                )
            )
            Text(
                text = stringResource(R.string.pdf_crop_margin_label, marginPercent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun loadFirstPage(context: android.content.Context, pdfUri: Uri): Bitmap? {
    return try {
        val file = File(context.cacheDir, "crop_preview_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        fd.close()
        file.delete()
        bmp
    } catch (e: Exception) {
        Timber.e(e, "CropPdfScreen: error generando vista previa")
        null
    }
}
