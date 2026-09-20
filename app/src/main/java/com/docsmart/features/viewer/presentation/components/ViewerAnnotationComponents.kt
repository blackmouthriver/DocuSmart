package com.docsmart.features.viewer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.features.viewer.presentation.AnnotationMode
import com.docsmart.features.viewer.presentation.MAX_NOTE_LENGTH

// HU-46: barra de herramientas de anotación -- aparece debajo de la barra
// superior al tocar "Anotar". Elegir un color arma el modo Resaltar con ese
// color; "Nota" arma el modo Nota; "Listo" cierra la barra y vuelve al
// zoom/pan normal (AnnotationMode.NONE).
@Composable
fun ViewerAnnotationToolbar(
    mode: AnnotationMode,
    selectedColor: Int,
    highlightColors: List<Int>,
    onColorSelected: (Int) -> Unit,
    onNoteSelected: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.viewer_annotate_toolbar_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Hallazgo real de la revisión general 2026-09-16 (#15): los
                // 4 círculos de color de resaltado no tenían
                // contentDescription ni semántica de selección -- mismo
                // criterio y mismo fix ya aplicado al selector de color del
                // Creador de QR (hallazgo #7).
                val highlightColorNames =
                    listOf(
                        stringResource(R.string.viewer_highlight_color_yellow),
                        stringResource(R.string.viewer_highlight_color_green),
                        stringResource(R.string.viewer_highlight_color_pink),
                        stringResource(R.string.viewer_highlight_color_blue),
                    )
                highlightColors.forEachIndexed { index, colorArgb ->
                    val isSelected = mode == AnnotationMode.HIGHLIGHT && colorArgb == selectedColor
                    val colorName = highlightColorNames.getOrElse(index) { "" }
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(colorArgb))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onColorSelected(colorArgb) },
                                )
                                .semantics { contentDescription = colorName },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = mode == AnnotationMode.NOTE,
                    onClick = onNoteSelected,
                    leadingIcon = {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text(stringResource(R.string.viewer_annotate_note_chip)) },
                )
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.viewer_annotate_done))
                }
            }
        }
    }
}

// HU-46 RF2: diálogo para escribir el texto de una nota nueva, tras tocar
// un punto del documento en modo Nota.
@Composable
fun ViewerNoteInputDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Hallazgo real de la revisión general 2026-09-16: con `remember`
    // simple, rotar el dispositivo (o que el sistema recree la Activity por
    // memoria baja) recreaba la composición desde cero y perdía el texto
    // escrito sin ningún aviso -- `rememberSaveable` sobrevive ambos casos.
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
        title = { Text(stringResource(R.string.viewer_annotate_note_dialog_title)) },
        text = {
            val atLimit = text.length >= MAX_NOTE_LENGTH
            OutlinedTextField(
                value = text,
                // Bug real: al PEGAR un texto más largo que el tope, el `if` descartaba el
                // pegado completo (el campo no cambiaba y el usuario no veía nada). Se recorta
                // al límite para conservar al menos los primeros MAX_NOTE_LENGTH caracteres.
                onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                placeholder = { Text(stringResource(R.string.viewer_annotate_note_dialog_placeholder)) },
                // Hallazgo real de la revisión general 2026-09-16 (#18): al
                // llegar al límite, onValueChange simplemente dejaba de
                // aceptar más texto -- el teclado "no respondía" sin ningún
                // aviso visible de por qué. El contador (siempre visible,
                // no solo al límite) explica el tope de antemano, e isError
                // resalta el campo cuando ya se alcanzó.
                supportingText = {
                    Text(
                        stringResource(R.string.viewer_annotate_note_char_count, text.length, MAX_NOTE_LENGTH),
                        color =
                            if (atLimit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                },
                isError = atLimit,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.general_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        },
    )
}

// HU-46 AC1: ver/eliminar una anotación ya existente (tap sobre un
// resaltado o una nota, fuera del modo de dibujo).
@Composable
fun ViewerAnnotationDetailDialog(
    annotation: AnnotationEntity,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isNote = annotation.type == AnnotationType.NOTE
    // Hallazgo real de la revisión general 2026-09-16 (#17): "Eliminar" acá
    // borraba de una sola vez, a diferencia de eliminar el documento
    // completo, que sí pide confirmación -- una nota o resaltado se pierde
    // para siempre sin que un toque accidental tenga forma de deshacerse.
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.viewer_annotate_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (isNote) {
                            R.string.viewer_annotate_delete_confirm_note_body
                        } else {
                            R.string.viewer_annotate_delete_confirm_highlight_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isNote) Icons.Rounded.EditNote else Icons.Rounded.HighlightAlt, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (isNote) {
                        R.string.viewer_annotate_note_detail_title
                    } else {
                        R.string.viewer_annotate_highlight_detail_title
                    },
                ),
            )
        },
        text = {
            if (isNote) {
                Text(annotation.text, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.viewer_annotate_highlight_detail_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        },
    )
}

// HU-46 RNF2: al compartir un documento con anotaciones, elegir
// explícitamente entre la copia aplanada o el original sin tocar.
@Composable
fun ViewerShareChoiceDialog(
    isFlattening: Boolean,
    onShareWithAnnotations: () -> Unit,
    onShareOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isFlattening) onDismiss() },
        title = { Text(stringResource(R.string.viewer_share_choice_title)) },
        text = {
            if (isFlattening) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.viewer_share_choice_preparing))
                }
            } else {
                Text(stringResource(R.string.viewer_share_choice_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onShareWithAnnotations, enabled = !isFlattening) {
                Text(stringResource(R.string.viewer_share_choice_with_annotations))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShareOriginal, enabled = !isFlattening) {
                    Text(stringResource(R.string.viewer_share_choice_original))
                }
                IconButton(onClick = onDismiss, enabled = !isFlattening) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.general_cancel))
                }
            }
        },
    )
}
