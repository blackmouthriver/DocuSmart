package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorPowerPoint

@Composable
fun ComparePdfScreen(
    pdfA: Uri?,
    pdfB: Uri?,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdfA: () -> Unit,
    onSelectPdfB: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_compare),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pdf_compare_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Selectores de los dos documentos a comparar ────
        PdfSelectZone(
            selectedPdf = pdfA,
            onSelectPdf = onSelectPdfA,
            readyText = stringResource(R.string.pdf_compare_ready),
            accentColor = ColorPowerPoint,
            label = stringResource(R.string.pdf_compare_document_a),
        )
        PdfSelectZone(
            selectedPdf = pdfB,
            onSelectPdf = onSelectPdfB,
            readyText = stringResource(R.string.pdf_compare_ready),
            accentColor = ColorPowerPoint,
            label = stringResource(R.string.pdf_compare_document_b),
        )

        // ── Nombre del archivo ────────────────────────
        if (pdfA != null && pdfB != null) {
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange,
            )
        }

        // ── Progreso o botón ──────────────────────────
        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = pdfA != null && pdfB != null,
            progressText = stringResource(R.string.pdf_compare_progress),
            buttonLabel = stringResource(R.string.pdf_compare_execute),
            buttonIcon = Icons.Rounded.CompareArrows,
            onExecute = onExecute,
            accentColor = ColorPowerPoint,
        )
    }
}
