package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorImage

@Composable
fun ExtractImagesPdfScreen(
    selectedPdf: Uri?,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_extract_images),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pdf_extract_images_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_extract_images_ready),
            accentColor = ColorImage,
        )

        if (selectedPdf != null) {
            OutputFileNameField(
                fileName = fileName,
                onFileNameChange = onFileNameChange,
                showPdfSuffix = false,
            )
        }

        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = selectedPdf != null,
            progressText = stringResource(R.string.pdf_extract_images_progress),
            buttonLabel = stringResource(R.string.pdf_extract_images),
            buttonIcon = Icons.Rounded.Image,
            onExecute = onExecute,
            accentColor = ColorImage,
        )
    }
}
