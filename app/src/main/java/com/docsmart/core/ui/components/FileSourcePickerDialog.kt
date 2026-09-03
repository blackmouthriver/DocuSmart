package com.docsmart.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R

/**
 * Selector de archivo con dos orígenes: el picker del sistema operativo,
 * o un documento ya indexado por la app (Biblioteca completa -- Downloads/
 * Imágenes de MediaStore + archivos generados por la app, misma fuente que
 * usa la pantalla Biblioteca). Item #15 del backlog UX
 * (`backlog-mejoras-ux-2026-08-30.md` §2): antes Seguridad y Herramientas
 * PDF solo ofrecían el picker del sistema.
 *
 * `filter` decide qué documentos de la biblioteca son válidos para este
 * selector puntual (p.ej. solo PDF en Herramientas PDF) -- por defecto
 * acepta cualquier tipo (Carpeta Segura protege archivos de cualquier
 * formato).
 */
@Composable
fun FileSourcePickerDialog(
    title            : String,
    onDismiss        : () -> Unit,
    onChooseFromDevice: () -> Unit,
    onChooseDocument : (DocumentUiModel) -> Unit,
    filter           : (DocumentUiModel) -> Boolean = { true },
    viewModel        : AppLibraryPickerViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()
    val filtered   = documents.filter(filter)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = MaterialTheme.shapes.large,
        title            = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = stringResource(R.string.filepicker_source_question),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseFromDevice),
                    shape    = MaterialTheme.shapes.medium,
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text  = stringResource(R.string.filepicker_from_device),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text  = stringResource(R.string.filepicker_browse_system_files),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text     = stringResource(R.string.filepicker_from_library),
                    style    = MaterialTheme.typography.titleSmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                when {
                    isLoading -> Box(
                        modifier         = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }

                    filtered.isEmpty() -> Text(
                        text  = stringResource(R.string.filepicker_no_library_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(
                        modifier            = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { document ->
                            Card(
                                modifier  = Modifier.fillMaxWidth().clickable { onChooseDocument(document) },
                                shape     = MaterialTheme.shapes.medium,
                                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(document.type.color.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector        = Icons.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint               = document.type.color,
                                            modifier           = Modifier.size(16.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text     = document.name,
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text  = document.size,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}
