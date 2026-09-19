package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorImage
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow

@Composable
fun WatermarkPdfScreen(
    selectedPdf: Uri?,
    watermarkText: String,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onWatermarkTextChange: (String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_watermark),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pdf_watermark_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Selector PDF ──────────────────────────────
        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_watermark_ready),
            accentColor = ColorImage,
        )

        // ── Texto de marca de agua ─────────────────────
        WatermarkTextCard(watermarkText, onWatermarkTextChange)

        // ── Nombre del archivo ────────────────────────
        if (selectedPdf != null) {
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange,
            )
        }

        // ── Progreso o botón ──────────────────────────
        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null && watermarkText.isNotBlank(),
            progressText = stringResource(R.string.pdf_watermark_progress),
            buttonLabel = stringResource(R.string.pdf_watermark_execute),
            buttonIcon = Icons.Rounded.BrandingWatermark,
            onExecute = onExecute,
            accentColor = ColorImage,
        )
    }
}

@Composable
private fun WatermarkTextCard(
    watermarkText: String,
    onWatermarkTextChange: (String) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.pdf_watermark_text_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = watermarkText,
                onValueChange = onWatermarkTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.pdf_watermark_text_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorImage,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(thickness = 0.5.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.pdf_watermark_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
