package com.docsmart.features.agenda.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.features.agenda.domain.ReminderPreset
import com.docsmart.features.agenda.domain.agendaDateFormatter
import com.docsmart.features.agenda.domain.agendaTimeFormatter
import com.docsmart.features.agenda.domain.reminderTriggerMillis
import com.docsmart.features.agenda.presentation.AgendaEventDraft
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// HU-65: editor de un evento de Agenda (crear o editar, según si
// `draft.id` ya existe). El selector de fecha/hora reutiliza el mismo
// patrón que QrEventForm/QrDateTimeRow (HU-43) -- Material3 DatePicker/
// TimePicker sobre LocalDateTime, con la misma conversión a/desde
// ZoneOffset.UTC que exige DatePickerState.selectedDateMillis -- pero
// trabaja en la zona horaria REAL del dispositivo al convertir hacia/desde
// epoch millis (a diferencia del truco interno UTC del propio picker, que
// es solo un detalle de implementación de ese componente).
@Composable
fun AgendaEventEditorDialog(
    draft: AgendaEventDraft,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDateTimeChange: (Long) -> Unit,
    onReminderChange: (Int?) -> Unit,
    onLinkDocumentClick: () -> Unit,
    onUnlinkDocument: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isEditing = draft.id != null
    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Media-Alta): borrado directo y permanente (no hay papelera para
    // eventos de Agenda) con un solo toque, en la misma fila que
    // "Cancelar"/"Guardar" -- mismo patrón de confirmación ya usado en
    // Carpeta Segura/Papelera de documentos.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.agenda_delete_confirm_title)) },
            text = { Text(stringResource(R.string.agenda_delete_confirm_body, draft.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            },
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isEditing) R.string.agenda_editor_title_edit else R.string.agenda_editor_title_new,
                        ),
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.agenda_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = draft.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.agenda_field_description)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                AgendaDateTimeRow(
                    label = stringResource(R.string.agenda_field_datetime),
                    value = draft.dateTimeMillis.toLocalDateTime(),
                    onValueChange = { onDateTimeChange(it.toEpochMillis()) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.agenda_field_reminder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ReminderPreset.ALL.forEach { minutes ->
                            FilterChip(
                                selected = draft.reminderMinutesBefore == minutes,
                                onClick = { onReminderChange(minutes) },
                                label = { Text(reminderOptionLabel(minutes), maxLines = 1) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ),
                            )
                        }
                    }
                    // Hallazgo real 2026-09-17: schedule() descarta en
                    // silencio un disparo ya pasado -- se avisa acá antes
                    // de guardar (ej. evento "en 3h" + "1 día antes").
                    ReminderPastWarning(draft.dateTimeMillis, draft.reminderMinutesBefore)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (draft.documentId != null) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                            contentDescription = null,
                            tint =
                                if (draft.documentId != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Text(
                            text =
                                stringResource(
                                    if (draft.documentId != null) {
                                        R.string.agenda_document_linked
                                    } else {
                                        R.string.agenda_document_not_linked
                                    },
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (draft.documentId != null) {
                        IconButton(onClick = onUnlinkDocument) {
                            Icon(
                                imageVector = Icons.Rounded.LinkOff,
                                contentDescription = stringResource(R.string.agenda_unlink_document),
                            )
                        }
                    } else {
                        IconButton(onClick = onLinkDocumentClick) {
                            Icon(
                                imageVector = Icons.Rounded.InsertDriveFile,
                                contentDescription = stringResource(R.string.agenda_link_document_title),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEditing) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.general_delete), color = ErrorRed)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
                        Button(onClick = onSave, enabled = draft.title.isNotBlank()) {
                            Text(stringResource(R.string.general_save))
                        }
                    }
                }
            }
        }
    }
}

// Hallazgo real de la auditoría general 2026-09-17: ReminderScheduler.
// schedule() descarta en silencio cualquier disparo ya pasado (defensa
// correcta), pero ninguna UI avisaba antes de guardar -- la fila quedaba
// en Room con reminderMinutesBefore seteado y el usuario creía tener un
// recordatorio que en realidad nunca se programó en AlarmManager.
// Alcanzable con cualquier combinación fecha+antelación cuyo resultado ya
// pasó (ej. evento "en 3 horas" + "1 día antes").
@Composable
private fun ReminderPastWarning(
    dateTimeMillis: Long,
    reminderMinutesBefore: Int?,
) {
    if (reminderMinutesBefore == null ||
        reminderTriggerMillis(dateTimeMillis, reminderMinutesBefore) > System.currentTimeMillis()
    ) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = WarningAmber,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.agenda_reminder_already_past),
            style = MaterialTheme.typography.bodySmall,
            color = WarningAmber,
        )
    }
}

@Composable
private fun reminderOptionLabel(minutes: Int?): String =
    when (minutes) {
        null -> stringResource(R.string.agenda_reminder_none)
        ReminderPreset.AT_TIME -> stringResource(R.string.agenda_reminder_at_time)
        ReminderPreset.MINUTES_15 -> stringResource(R.string.agenda_reminder_15_min)
        ReminderPreset.HOUR_1 -> stringResource(R.string.agenda_reminder_1_hour)
        ReminderPreset.DAY_1 -> stringResource(R.string.agenda_reminder_1_day)
        else -> minutes.toString()
    }

private fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaDateTimeRow(
    label: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Ronda 16: idioma y preferencia 12/24h del sistema en vez de "d MMM yyyy"/
    // "HH:mm" fijos (el TimePicker también usaba is24Hour = true siempre).
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val dateFormat = remember(locale) { agendaDateFormatter(locale) }
    val timeFormat = remember(locale, is24Hour) { agendaTimeFormatter(locale, is24Hour) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                Text(value.format(dateFormat), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
                Text(value.format(timeFormat), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(LocalDateTime.of(newDate, value.toLocalTime()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.general_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.general_cancel)) }
            },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = is24Hour)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.general_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            onValueChange(value.withHour(state.hour).withMinute(state.minute))
                            showTimePicker = false
                        }) { Text(stringResource(R.string.general_accept)) }
                    }
                }
            }
        }
    }
}
