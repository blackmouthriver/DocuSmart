package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow

@Composable
fun CompressPdfScreen(
    selectedPdf: Uri?,
    quality: Int,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onQualityChange: (Int) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_compress),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pdf_compress_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Selector PDF ──────────────────────────────
        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_compress_ready),
            accentColor = SuccessGreen,
        )

        // ── Control de calidad ────────────────────────
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.pdf_compress_level_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val (levelTextRes, levelColor) =
                        when {
                            quality >= 80 ->
                                R.string.pdf_compress_level_high_quality to
                                    SuccessGreen
                            quality >= 60 ->
                                R.string.pdf_compress_level_balanced to
                                    MaterialTheme.colorScheme.primary
                            quality >= 40 ->
                                R.string.pdf_compress_level_high_compression to
                                    MaterialTheme.colorScheme.tertiary
                            else ->
                                R.string.pdf_compress_level_max_compression to
                                    MaterialTheme.colorScheme.error
                        }
                    val levelText = stringResource(levelTextRes)
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = levelColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = levelText,
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                            modifier =
                                Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp,
                                ),
                        )
                    }
                }

                Slider(
                    value = quality.toFloat(),
                    onValueChange = { onQualityChange(it.toInt()) },
                    valueRange = 20f..100f,
                    steps = 7,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.pdf_compress_smaller),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$quality%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.pdf_compress_better_quality),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                        text =
                            stringResource(
                                when {
                                    quality >= 80 -> R.string.pdf_compress_reduction_10_20
                                    quality >= 60 -> R.string.pdf_compress_reduction_30_50
                                    quality >= 40 -> R.string.pdf_compress_reduction_50_70
                                    else -> R.string.pdf_compress_reduction_70_85
                                },
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
            enabled = selectedPdf != null,
            progressText = stringResource(R.string.pdf_compress_progress),
            buttonLabel = stringResource(R.string.pdf_compress),
            buttonIcon = Icons.Rounded.Compress,
            onExecute = onExecute,
            accentColor = SuccessGreen,
        )
    }
}
