package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.features.pdftools.domain.usecase.PageNumberFormat

@Composable
fun NumberPagesScreen(
    selectedPdf: Uri?,
    format: PageNumberFormat,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onFormatChange: (PageNumberFormat) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_number_pages),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_number_pages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Selector PDF ──────────────────────────────
        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_number_pages_ready),
            accentColor = IndigoAccent
        )

        // ── Formato de numeración ──────────────────────
        NumberPagesFormatCard(format, onFormatChange)

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
            progressText = stringResource(R.string.pdf_number_pages_progress),
            buttonLabel = stringResource(R.string.pdf_number_pages_execute),
            buttonIcon = Icons.Rounded.FormatListNumbered,
            onExecute = onExecute,
            accentColor = IndigoAccent
        )
    }
}

@Composable
private fun NumberPagesFormatCard(
    format: PageNumberFormat,
    onFormatChange: (PageNumberFormat) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pdf_number_pages_format_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormatChip(
                    selected = format == PageNumberFormat.NUMBER_ONLY,
                    label = stringResource(R.string.pdf_number_pages_format_number_only),
                    onClick = { onFormatChange(PageNumberFormat.NUMBER_ONLY) },
                    modifier = Modifier.weight(1f)
                )
                FormatChip(
                    selected = format == PageNumberFormat.NUMBER_OF_TOTAL,
                    label = stringResource(R.string.pdf_number_pages_format_number_of_total),
                    onClick = { onFormatChange(PageNumberFormat.NUMBER_OF_TOTAL) },
                    modifier = Modifier.weight(1f)
                )
                FormatChip(
                    selected = format == PageNumberFormat.PAGE_OF_TOTAL,
                    label = stringResource(R.string.pdf_number_pages_format_page_of_total),
                    onClick = { onFormatChange(PageNumberFormat.PAGE_OF_TOTAL) },
                    modifier = Modifier.weight(1f)
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
                    text = stringResource(
                        when (format) {
                            PageNumberFormat.NUMBER_ONLY     -> R.string.pdf_number_pages_example_number_only
                            PageNumberFormat.NUMBER_OF_TOTAL -> R.string.pdf_number_pages_example_number_of_total
                            PageNumberFormat.PAGE_OF_TOTAL   -> R.string.pdf_number_pages_example_page_of_total
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FormatChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = modifier,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = IndigoAccent.copy(alpha = 0.2f),
            selectedLabelColor = IndigoAccent
        )
    )
}
