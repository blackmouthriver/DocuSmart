package com.docsmart.features.viewer.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.docsmart.R

/**
 * RF-VIS-06: diálogo de renombrar propio del Visor, localizado desde el
 * día uno vía `stringResource()` -- a diferencia de `RenameDocumentDialog`
 * en `core/ui/components/DocuSmartDocumentItem.kt` (usado por Biblioteca/
 * Home), que tiene sus textos en español fijo. No se reutiliza ese
 * composable para no heredar ese hueco de i18n en una pantalla nueva.
 */
@Composable
fun ViewerRenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    val isValid = text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.viewer_rename_label)) },
                isError = !isValid,
                supportingText = {
                    if (!isValid) Text(stringResource(R.string.viewer_rename_empty_error))
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (isValid) onConfirm(text.trim()) }, enabled = isValid) {
                Text(stringResource(R.string.general_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

/**
 * RF-VIS-06: primer diálogo de confirmación de borrado del proyecto --
 * ni Biblioteca ni Home lo tienen hoy (eliminan directo al tocar el ítem
 * del menú contextual, ver `DocumentContextMenu`), documentado como hueco
 * preexistente fuera de alcance de esta HU en
 * `docs/requirements/visor-biblioteca.md`.
 */
@Composable
fun ViewerDeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_delete_confirm_title)) },
        text = { Text(stringResource(R.string.viewer_delete_confirm_body, fileName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.general_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}
