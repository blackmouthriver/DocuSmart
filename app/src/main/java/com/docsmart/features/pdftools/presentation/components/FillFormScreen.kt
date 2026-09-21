package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorZip
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.features.pdftools.domain.usecase.FormFieldInfo

@Composable
fun FillFormScreen(
    selectedPdf: Uri?,
    formFields: List<FormFieldInfo>,
    formFieldValues: Map<String, String>,
    formFieldsDetected: Boolean,
    isProcessing: Boolean,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSelectPdf: () -> Unit,
    onDetectFields: (Uri) -> Unit,
    onFieldValueChange: (String, String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(selectedPdf) {
        if (selectedPdf != null) onDetectFields(selectedPdf)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_fill_form),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pdf_fill_form_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PdfSelectZone(
            selectedPdf = selectedPdf,
            onSelectPdf = onSelectPdf,
            readyText = stringResource(R.string.pdf_fill_form_ready),
        )

        if (selectedPdf != null) {
            FillFormFieldsCard(formFields, formFieldValues, formFieldsDetected, onFieldValueChange)

            if (formFields.isNotEmpty()) {
                OutputFileNameField(
                    fileName = fileName,
                    onFileNameChange = onFileNameChange,
                )
            }
        }

        PdfProcessingFooter(
            isProcessing = isProcessing,
            enabled = canExecuteFillForm(selectedPdf != null, formFields.size),
            progressText = stringResource(R.string.pdf_fill_form_progress),
            buttonLabel = stringResource(R.string.pdf_fill_form_execute),
            buttonIcon = Icons.Rounded.Checklist,
            onExecute = onExecute,
        )
    }
}

@Composable
private fun FillFormFieldsCard(
    formFields: List<FormFieldInfo>,
    formFieldValues: Map<String, String>,
    formFieldsDetected: Boolean,
    onFieldValueChange: (String, String) -> Unit,
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
            when {
                !formFieldsDetected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ColorZip)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.pdf_fill_form_detecting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                formFields.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.pdf_fill_form_no_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.pdf_fill_form_fields_title, formFields.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    formFields.forEach { field ->
                        OutlinedTextField(
                            value = formFieldValues[field.name] ?: field.currentValue,
                            onValueChange = { onFieldValueChange(field.name, it) },
                            label = { Text(text = field.name, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ColorZip,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
