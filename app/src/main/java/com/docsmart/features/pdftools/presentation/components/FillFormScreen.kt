package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
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
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
    modifier: Modifier = Modifier
) {
    LaunchedEffect(selectedPdf) {
        if (selectedPdf != null) onDetectFields(selectedPdf)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pdf_fill_form),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pdf_fill_form_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FillFormSelectZone(selectedPdf, onSelectPdf)

        if (selectedPdf != null) {
            FillFormFieldsCard(formFields, formFieldValues, formFieldsDetected, onFieldValueChange)

            if (formFields.isNotEmpty()) {
                OutputFileNameField(
                    fileName = fileName,
                    onFileNameChange = onFileNameChange
                )
            }
        }

        if (isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ColorZip,
                    trackColor = ColorZip.copy(alpha = 0.2f)
                )
                Text(
                    text = stringResource(R.string.pdf_fill_form_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = onExecute,
                enabled = selectedPdf != null && formFields.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorZip,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pdf_fill_form_execute),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FillFormSelectZone(selectedPdf: Uri?, onSelectPdf: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (selectedPdf != null) 1.dp else 1.5.dp,
                color = if (selectedPdf != null)
                    ColorZip
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (selectedPdf != null)
                    ColorZip.copy(alpha = 0.1f)
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
                    ColorZip
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(
                        if (selectedPdf != null) R.string.pdf_fill_form_ready
                        else R.string.pdf_tools_select_pdf_prompt
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedPdf != null)
                        ColorZip
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
private fun FillFormFieldsCard(
    formFields: List<FormFieldInfo>,
    formFieldValues: Map<String, String>,
    formFieldsDetected: Boolean,
    onFieldValueChange: (String, String) -> Unit
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
            when {
                !formFieldsDetected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ColorZip)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.pdf_fill_form_detecting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                formFields.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.pdf_fill_form_no_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.pdf_fill_form_fields_title, formFields.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    formFields.forEach { field ->
                        OutlinedTextField(
                            value = formFieldValues[field.name] ?: field.currentValue,
                            onValueChange = { onFieldValueChange(field.name, it) },
                            label = { Text(text = field.name, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorZip,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
