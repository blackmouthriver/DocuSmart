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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MergePdfScreen(
    selectedPdfs: List<Uri>,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdfs: () -> Unit,
    onRemovePdf: (Uri) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Unir PDFs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Selecciona 2 o más PDFs para combinarlos en uno solo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Zona de selección ─────────────────────────
        MergePdfSelectZone(selectedPdfs.isEmpty(), onSelectPdfs)

        // ── Lista de PDFs seleccionados ───────────────
        if (selectedPdfs.isNotEmpty()) {
            SelectedPdfsList(selectedPdfs, onRemovePdf)

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
                    text = "Los PDFs se unirán en el orden mostrado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        // ── Progreso o botón ──────────────────────────
        if (isProcessing) {
            MergePdfProgress(selectedPdfs.size)
        } else {
            MergePdfExecuteButton(selectedPdfs.size, onExecute)
        }
    }
}

@Composable
private fun MergePdfSelectZone(isEmpty: Boolean, onSelectPdfs: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isEmpty) 120.dp else 56.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
            .clickable { onSelectPdfs() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (isEmpty) "Toca para seleccionar PDFs" else "Agregar más PDFs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SelectedPdfsList(selectedPdfs: List<Uri>, onRemovePdf: (Uri) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            selectedPdfs.forEachIndexed { index, uri ->
                SelectedPdfRow(index, uri, onRemovePdf)
                if (index < selectedPdfs.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedPdfRow(index: Int, uri: Uri, onRemovePdf: (Uri) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Icon(
            imageVector = Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = uri.lastPathSegment
                ?.substringAfterLast("/")
                ?: "Archivo ${index + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onRemovePdf(uri) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Quitar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MergePdfProgress(count: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
        Text(
            text = "Uniendo $count PDFs...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MergePdfExecuteButton(count: Int, onExecute: () -> Unit) {
    Button(
        onClick = onExecute,
        enabled = count >= 2,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            imageVector = Icons.Rounded.MergeType,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (count < 2) "Selecciona al menos 2 PDFs" else "Unir $count PDFs",
            style = MaterialTheme.typography.labelLarge
        )
    }
}