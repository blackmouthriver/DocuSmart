package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.SuccessGreen

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Comprimir PDF",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Reduce el tamaño de tu PDF manteniendo la legibilidad",
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
                        SuccessGreen
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.large
                )
                .background(
                    if (selectedPdf != null)
                        SuccessGreen.copy(alpha = 0.1f)
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
                        SuccessGreen
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (selectedPdf != null)
                            "PDF listo para comprimir"
                        else
                            "Seleccionar PDF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedPdf != null)
                            SuccessGreen
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

        // ── Control de calidad ────────────────────────
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nivel de compresión",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val (levelText, levelColor) = when {
                        quality >= 80 -> "Alta calidad" to SuccessGreen
                        quality >= 60 -> "Balanceado" to MaterialTheme.colorScheme.primary
                        quality >= 40 -> "Alta compresión" to MaterialTheme.colorScheme.tertiary
                        else -> "Máxima compresión" to MaterialTheme.colorScheme.error
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = levelColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = levelText,
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                            modifier = Modifier.padding(
                                horizontal = 8.dp, vertical = 4.dp
                            )
                        )
                    }
                }

                Slider(
                    value = quality.toFloat(),
                    onValueChange = { onQualityChange(it.toInt()) },
                    valueRange = 20f..100f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Más pequeño",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$quality%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mejor calidad",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(thickness = 0.5.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            quality >= 80 -> "Reducción estimada: 10-20%"
                            quality >= 60 -> "Reducción estimada: 30-50%"
                            quality >= 40 -> "Reducción estimada: 50-70%"
                            else -> "Reducción estimada: 70-85%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    color = SuccessGreen,
                    trackColor = SuccessGreen.copy(alpha = 0.2f)
                )
                Text(
                    text = "Comprimiendo PDF...",
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
                    containerColor = SuccessGreen,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Compress,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comprimir PDF",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}