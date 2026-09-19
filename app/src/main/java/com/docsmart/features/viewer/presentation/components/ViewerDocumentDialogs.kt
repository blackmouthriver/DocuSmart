package com.docsmart.features.viewer.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.NoteEntity

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
    onDismiss: () -> Unit,
) {
    // Hallazgo real de la revisión general 2026-09-16: `remember` simple
    // perdía el texto tecleado al rotar el dispositivo (o si el sistema
    // recreaba la Activity por memoria baja) -- `rememberSaveable`
    // sobrevive ambos casos.
    var text by rememberSaveable(currentName) { mutableStateOf(currentName) }
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
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (isValid) onConfirm(text.trim()) }, enabled = isValid) {
                Text(stringResource(R.string.general_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        },
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
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_delete_confirm_title)) },
        text = { Text(stringResource(R.string.viewer_delete_confirm_body, fileName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.general_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        },
    )
}

/**
 * Backlog UX #50, RF2: lista de solo lectura de las notas de Modo Estudio
 * vinculadas a este documento -- abrirlas para editar queda fuera de
 * alcance (exigiría navegar hasta la pestaña Notas de Modo Estudio con un
 * id específico), esto solo cumple "permite abrirlas" mostrando el
 * contenido completo acá mismo.
 */
@Composable
fun ViewerLinkedNotesDialog(
    notes: List<NoteEntity>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_linked_notes_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(notes, key = { it.id }) { note ->
                    Column {
                        Text(note.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = note.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        },
    )
}
