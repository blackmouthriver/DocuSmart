package com.docsmart.features.viewer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    mode          : AnnotationMode,
    selectedColor : Int,
    highlightColors: List<Int>,
    onColorSelected: (Int) -> Unit,
    onNoteSelected : () -> Unit,
    onDone         : () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text  = stringResource(R.string.viewer_annotate_toolbar_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                highlightColors.forEach { colorArgb ->
                    val isSelected = mode == AnnotationMode.HIGHLIGHT && colorArgb == selectedColor
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(colorArgb))
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(colorArgb) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = mode == AnnotationMode.NOTE,
                    onClick  = onNoteSelected,
                    leadingIcon = {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text(stringResource(R.string.viewer_annotate_note_chip)) }
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
fun ViewerNoteInputDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Hallazgo real de la revisión general 2026-09-16: con `remember`
    // simple, rotar el dispositivo (o que el sistema recree la Activity por
    // memoria baja) recreaba la composición desde cero y perdía el texto
    // escrito sin ningún aviso -- `rememberSaveable` sobrevive ambos casos.
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon    = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
        title   = { Text(stringResource(R.string.viewer_annotate_note_dialog_title)) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { if (it.length <= MAX_NOTE_LENGTH) text = it },
                placeholder   = { Text(stringResource(R.string.viewer_annotate_note_dialog_placeholder)) },
                minLines      = 3,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.general_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}

// HU-46 AC1: ver/eliminar una anotación ya existente (tap sobre un
// resaltado o una nota, fuera del modo de dibujo).
@Composable
fun ViewerAnnotationDetailDialog(
    annotation: AnnotationEntity,
    onDelete  : () -> Unit,
    onDismiss : () -> Unit
) {
    val isNote = annotation.type == AnnotationType.NOTE
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(if (isNote) Icons.Rounded.EditNote else Icons.Rounded.HighlightAlt, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (isNote) R.string.viewer_annotate_note_detail_title
                    else R.string.viewer_annotate_highlight_detail_title
                )
            )
        },
        text = {
            if (isNote) {
                Text(annotation.text, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.viewer_annotate_highlight_detail_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        }
    )
}

// HU-46 RNF2: al compartir un documento con anotaciones, elegir
// explícitamente entre la copia aplanada o el original sin tocar.
@Composable
fun ViewerShareChoiceDialog(
    isFlattening: Boolean,
    onShareWithAnnotations: () -> Unit,
    onShareOriginal       : () -> Unit,
    onDismiss              : () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isFlattening) onDismiss() },
        title = { Text(stringResource(R.string.viewer_share_choice_title)) },
        text  = {
            if (isFlattening) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        }
    )
}
