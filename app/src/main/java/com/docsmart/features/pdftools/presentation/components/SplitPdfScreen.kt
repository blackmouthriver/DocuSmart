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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow

@Composable
fun SplitPdfScreen(
    selectedPdf: Uri?,
    fromPage: Int,
    toPage: Int,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onFromPageChange: (Int) -> Unit,
    onToPageChange: (Int) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_split),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_split_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Selector PDF ──────────────────────────────
        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_split_selected)
        )

        // ── Rango de páginas ──────────────────────────
        if (selectedPdf != null) {
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pdf_split_range_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Desde página
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_split_from_page),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (fromPage > 1) onFromPageChange(fromPage - 1)
                                    },
                                    // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Remove,
                                        contentDescription = stringResource(R.string.pdf_split_from_page_decrease),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = fromPage.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(40.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { onFromPageChange(fromPage + 1) },
                                    // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.pdf_split_from_page_increase),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Hasta página
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_split_to_page),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (toPage > fromPage) onToPageChange(toPage - 1)
                                    },
                                    // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Remove,
                                        contentDescription = stringResource(R.string.pdf_split_to_page_decrease),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = toPage.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(40.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { onToPageChange(toPage + 1) },
                                    // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.pdf_split_to_page_increase),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    val pageCount = (toPage - fromPage + 1).coerceAtLeast(0)
                    // Hallazgo real de la revisión general 2026-09-16 (#22):
                    // el color de error usaba ">=" -- un rango válido de una
                    // sola página (fromPage == toPage) se pintaba en rojo
                    // igual que un rango realmente inválido (fromPage >
                    // toPage). Corregido a ">" estricto.
                    val isInvalidRange = fromPage > toPage
                    Text(
                        text  = stringResource(R.string.pdf_split_summary, pageCount, fromPage, toPage),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInvalidRange)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Nombre del archivo ────────────────────
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange
            )
        }

        // ── Progreso o botón ──────────────────────────
        PdfProcessingFooter(
            isProcessing = isProcessing,
            // Hallazgo real #22: el botón quedaba habilitado con un rango
            // inválido (Desde > Hasta) -- el use case lo corregía en
            // silencio en vez de avisar. Ahora el botón mismo lo bloquea.
            enabled = selectedPdf != null && fromPage <= toPage,
            progressText = stringResource(R.string.pdf_split_progress),
            buttonLabel = stringResource(R.string.pdf_split),
            buttonIcon = Icons.Rounded.CallSplit,
            onExecute = onExecute
        )
    }
}