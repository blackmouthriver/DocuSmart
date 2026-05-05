package com.docsmart.features.converter.presentation

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.components.buttons.DocuSmartPrimaryButton
import com.docsmart.core.ui.components.buttons.DocuSmartSecondaryButton
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.presentation.components.ConversionProgress
import com.docsmart.features.converter.presentation.components.ConversionSuccess
import com.docsmart.features.converter.presentation.components.ImagePickerSection

@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.conversionResult) {
        if (uiState.conversionResult is ConversionResult.Success) {
            activity?.let {
                viewModel.adManager.onConversionCompleted(it)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Banner azul con logo ───────────────────
            item {
                DocuSmartTopBanner(
                    screenTitle = "Convertidor",
                    screenSubtitle = "Convierte tus documentos fácilmente"
                )
            }

            val result = uiState.conversionResult
            if (result is ConversionResult.Success) {
                item {
                    ConversionSuccess(
                        result = result,
                        savedToDownloads = uiState.savedToDownloads,
                        onConvertAnother = { viewModel.clearAll() },
                        onSaveToDownloads = { viewModel.saveToDownloads(context) }
                    )
                }
            }

            if (result !is ConversionResult.Success) {
                item {
                    ImagePickerSection(
                        selectedImages = uiState.selectedImages,
                        onImagesSelected = { viewModel.onImagesSelected(it) },
                        onRemoveImage = { viewModel.removeImage(it) }
                    )
                }

                if (uiState.selectedImages.isNotEmpty()) {
                    item {
                        FileNameField(
                            fileName = uiState.fileName,
                            onFileNameChange = { viewModel.onFileNameChange(it) }
                        )
                    }
                    item {
                        if (uiState.isConverting) {
                            ConversionProgress(
                                totalImages = uiState.selectedImages.size
                            )
                        } else {
                            ActionButtons(
                                onConvert = { viewModel.convertToPdf() },
                                onClear = { viewModel.clearAll() }
                            )
                        }
                    }
                }

                if (uiState.selectedImages.isEmpty()) {
                    item { TipCard() }
                }
            }
        }
    }
}

@Composable
private fun FileNameField(
    fileName: String,
    onFileNameChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Nombre del archivo",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = fileName,
            onValueChange = onFileNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Ej: Contrato_2024",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Text(
                    text = ".pdf",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Si lo dejas vacío se generará un nombre automático",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtons(
    onConvert: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DocuSmartPrimaryButton(
            text = "Convertir a PDF",
            onClick = onConvert,
            leadingIcon = Icons.Rounded.PictureAsPdf
        )
        DocuSmartSecondaryButton(
            text = "Limpiar selección",
            onClick = onClear,
            leadingIcon = Icons.Rounded.Clear
        )
    }
}

@Composable
private fun TipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
                .copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cómo funciona",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1. Toca el área para seleccionar imágenes\n" +
                            "2. Escribe el nombre del archivo (opcional)\n" +
                            "3. Toca \"Convertir a PDF\"\n" +
                            "4. Guarda en Descargas o comparte",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}