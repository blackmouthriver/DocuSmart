package com.docsmart.features.converter.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult

// RF-CONV-08: resumen de una conversión por lotes (N archivos → N salidas).
// A diferencia de ConversionSuccess (un solo archivo, un botón "Compartir"),
// acá cada fila puede haber tenido éxito o fallado de forma independiente --
// un archivo corrupto en el lote no debe impedir ver el resultado de los demás.
@Composable
fun BatchConversionSuccess(
    items               : List<BatchConversionItem>,
    savedToDownloads    : Boolean,
    onConvertAnother    : () -> Unit,
    onSaveAllToDownloads: () -> Unit,
    modifier            : Modifier = Modifier
) {
    val shareLabel = stringResource(R.string.converter_share)
    val successCount = items.count { it.result is ConversionResult.Success }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraLarge
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text  = stringResource(R.string.converter_batch_success_title, successCount, items.size),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    BatchResultRow(item = item, shareLabel = shareLabel)
                }
            }

            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (savedToDownloads) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text  = stringResource(R.string.converter_saved_to_downloads),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (successCount > 0) {
                    Button(
                        onClick  = onSaveAllToDownloads,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = MaterialTheme.shapes.medium,
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = stringResource(R.string.converter_batch_save_all),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                TextButton(
                    onClick  = onConvertAnother,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text  = stringResource(R.string.converter_batch_convert_another),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchResultRow(item: BatchConversionItem, shareLabel: String) {
    val context = LocalContext.current
    val result = item.result

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (result) {
                is ConversionResult.Success -> {
                    val (fileIcon, fileColor) = formatIconForExtension(result.outputFile.extension)
                    Icon(fileIcon, null, tint = fileColor, modifier = Modifier.size(20.dp))
                }
                else -> Icon(
                    Icons.Rounded.ErrorOutline, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = item.originalFileName,
                    style    = MaterialTheme.typography.labelLarge,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text  = when (result) {
                        is ConversionResult.Success -> result.outputFile.name
                        is ConversionResult.Error   -> result.message
                        else                        -> ""
                    },
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (result is ConversionResult.Error)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (result is ConversionResult.Success) {
                IconButton(onClick = { shareFile(context, result.outputFile, shareLabel) }) {
                    Icon(
                        Icons.Rounded.Share, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
