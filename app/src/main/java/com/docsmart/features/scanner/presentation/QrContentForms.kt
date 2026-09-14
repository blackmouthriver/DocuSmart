package com.docsmart.features.scanner.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.docsmart.R
import com.docsmart.features.scanner.domain.QrWifiSecurity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// HU-43 (backlog UX 2026-08-30/09-14): formularios de los 3 tipos de
// contenido nuevos del Creador de QR (Wi-Fi/Contacto/Evento), extraídos a
// este archivo aparte para no seguir inflando QrScreen.kt -- mismo motivo
// ya documentado ahí para las secciones de ScanResultScreen.kt. Reutilizan
// qrTypeChipColors()/qrTypeChipBorder() (QrScreen.kt, ahora `internal`)
// para que los chips nuevos se vean idénticos a los 6 tipos ya existentes.

@Composable
fun QrWifiForm(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordToggle: () -> Unit,
    security: QrWifiSecurity,
    onSecurityChange: (QrWifiSecurity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_wifi_ssid)) },
            leadingIcon = { Icon(Icons.Rounded.Wifi, null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.qr_wifi_security_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrWifiSecurity.entries.forEach { option ->
                    FilterChip(
                        selected = security == option,
                        onClick = { onSecurityChange(option) },
                        label = { Text(stringResource(option.labelRes())) },
                        colors = qrTypeChipColors(),
                        border = qrTypeChipBorder(security == option)
                    )
                }
            }
        }
        // Una red abierta ("Ninguna") no tiene contraseña que pedir.
        if (security != QrWifiSecurity.NONE) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.qr_label_wifi_password)) },
                leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = onShowPasswordToggle) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = qrOutlinedFieldColors()
            )
        }
    }
}

private fun QrWifiSecurity.labelRes(): Int = when (this) {
    QrWifiSecurity.WPA  -> R.string.qr_wifi_security_wpa
    QrWifiSecurity.WEP  -> R.string.qr_wifi_security_wep
    QrWifiSecurity.NONE -> R.string.qr_wifi_security_none
}

@Composable
fun QrContactForm(
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_contact_name)) },
            leadingIcon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_contact_phone)) },
            leadingIcon = { Icon(Icons.Rounded.Phone, null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_contact_email)) },
            leadingIcon = { Icon(Icons.Rounded.Email, null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
    }
}

@Composable
fun QrEventForm(
    title: String,
    onTitleChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    start: LocalDateTime,
    onStartChange: (LocalDateTime) -> Unit,
    end: LocalDateTime,
    onEndChange: (LocalDateTime) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_event_title)) },
            leadingIcon = { Icon(Icons.Rounded.Title, null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.qr_label_event_location)) },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = qrOutlinedFieldColors()
        )
        QrDateTimeRow(
            label = stringResource(R.string.qr_label_event_start),
            value = start,
            onValueChange = onStartChange
        )
        QrDateTimeRow(
            label = stringResource(R.string.qr_label_event_end),
            value = end,
            onValueChange = onEndChange
        )
    }
}

// Mismo borde/color de acento que ya usan los OutlinedTextField existentes
// de QrCreatorScreen (URL/Texto/Email/Teléfono) -- ver QrScreen.kt:1058-1061.
@Composable
private fun qrOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    focusedBorderColor = MaterialTheme.colorScheme.primary
)

private val QR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val QR_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Fecha + hora de un evento (RF1 de HU-43): dos botones independientes
// (fecha/hora) en vez de un único selector combinado -- así se puede
// corregir solo la hora sin tener que volver a elegir el día, y viceversa.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrDateTimeRow(
    label: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                Text(value.format(QR_DATE_FORMAT), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
                Text(value.format(QR_TIME_FORMAT), modifier = Modifier.padding(start = 6.dp))
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
            }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
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
