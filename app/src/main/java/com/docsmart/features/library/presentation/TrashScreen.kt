package com.docsmart.features.library.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import java.util.Locale

/**
 * RF-VIS-07: papelera de reciclaje -- documentos "eliminados" desde
 * Biblioteca/Home/Visor viven acá hasta que se restauran, se eliminan
 * definitivamente, o vence el plazo de retención
 * (`TrashRepository.TRASH_RETENTION_DAYS`).
 */
@Composable
fun TrashScreen(
    onBack   : () -> Unit = {},
    viewModel: TrashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<TrashedItemUi?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    // ── Borrado real que Android no puede hacer sin confirmación del usuario
    // (fotos de MediaStore que la app no creó) -- se lanza el diálogo de
    // sistema y, si vuelve OK, se avisa de vuelta al ViewModel para limpiar
    // la papelera (ver DocumentRepository.DeleteOutcome.NeedsPermission).
    var pendingRequest by remember { mutableStateOf<PendingDeleteRequest?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val request = pendingRequest
        pendingRequest = null
        if (result.resultCode == Activity.RESULT_OK && request != null) {
            when (request) {
                is PendingDeleteRequest.Single -> viewModel.onSingleDeleteConfirmed(request.documentId)
                is PendingDeleteRequest.Bulk   -> viewModel.onBulkDeleteConfirmed(request.documentIds)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pendingDeleteRequest.collect { request ->
            pendingRequest = request
            permissionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    pendingDelete?.let { item ->
        TrashDeleteForeverDialog(
            fileName  = item.document.name,
            onConfirm = {
                viewModel.deleteForever(item.document.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (pendingDeleteAll) {
        TrashDeleteAllDialog(
            count     = uiState.items.size,
            onConfirm = {
                viewModel.deleteAll()
                pendingDeleteAll = false
            },
            onDismiss = { pendingDeleteAll = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        DocuSmartTopBanner(
            screenTitle    = stringResource(R.string.trash_title),
            screenSubtitle = stringResource(R.string.trash_subtitle),
            onBack         = onBack
        )

        if (uiState.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            // H5 (backlog-mejoras-ux-2026-08-30.md §12): a diferencia de
            // Ajustes → Almacenamiento, la Papelera no comunicaba cuánto
            // espacio liberaría "Borrar todo".
            val totalSize = formatTrashSize(uiState.items.sumOf { it.document.sizeBytes })
            Text(
                text  = stringResource(R.string.trash_total_size, totalSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { pendingDeleteAll = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trash_delete_all))
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.items.isEmpty() -> TrashEmptyState()
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.items, key = { it.document.id }) { item ->
                        TrashItemCard(
                            item             = item,
                            onRestore        = { viewModel.restore(item.document.id) },
                            onDeleteForever  = { pendingDelete = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashEmptyState() {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = Icons.Rounded.DeleteSweep,
            contentDescription = null,
            modifier           = Modifier.size(56.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text      = stringResource(R.string.trash_empty_title),
            style     = MaterialTheme.typography.titleMedium,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text      = stringResource(R.string.trash_empty_body),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrashItemCard(
    item           : TrashedItemUi,
    onRestore      : () -> Unit,
    onDeleteForever: () -> Unit
) {
    val doc = item.document
    Card(
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(doc.type.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = doc.type.label.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        color = doc.type.color
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = doc.name,
                        style      = MaterialTheme.typography.bodyMedium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = "${doc.size} · ${doc.date}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text  = if (item.daysRemaining <= 0)
                    stringResource(R.string.trash_deletes_today)
                else
                    stringResource(R.string.trash_days_remaining, item.daysRemaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // H3 (backlog-mejoras-ux-2026-08-30.md §12): antes ambos
                // botones eran OutlinedButton con el mismo peso visual --
                // "Restaurar" (reversible) pasa a relleno/tonal para que
                // destaque más que "Eliminar ahora" (irreversible), en vez
                // de diferenciarse solo por el color de texto.
                FilledTonalButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.trash_restore))
                }
                OutlinedButton(
                    onClick = onDeleteForever,
                    modifier = Modifier.weight(1f),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.trash_delete_forever))
                }
            }
        }
    }
}

@Composable
private fun TrashDeleteAllDialog(
    count    : Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_delete_all_confirm_title)) },
        text  = { Text(stringResource(R.string.trash_delete_all_confirm_body, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text  = stringResource(R.string.general_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

@Composable
private fun TrashDeleteForeverDialog(
    fileName : String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_delete_forever_confirm_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trash_delete_forever_confirm_body, fileName))
                // H2 (backlog-mejoras-ux-2026-08-30.md §12): Android puede
                // pedir un permiso del sistema para borrar fotos que la
                // app no creó (MediaStore) -- se avisa antes de que
                // aparezca, para que no se sienta como un paso inesperado.
                Text(
                    text  = stringResource(R.string.trash_delete_forever_permission_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text  = stringResource(R.string.general_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

// H5 (backlog-mejoras-ux-2026-08-30.md §12): mismo formato que
// DocumentRepository.formatSize(), duplicado acá a propósito -- es
// privado en DocumentRepository y esta pantalla solo necesita el total,
// no vale la pena exponer la función solo para reusar 4 líneas.
private fun formatTrashSize(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else                -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
