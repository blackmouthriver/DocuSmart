package com.docsmart.features.scanner.presentation

import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.features.scanner.domain.parseVCardPayload
import com.docsmart.features.scanner.domain.parseVEventPayload
import com.docsmart.features.scanner.domain.parseWifiPayload
import timber.log.Timber

// Extraído de QrReaderScreen (HU-44: el Historial necesita mostrar el mismo
// "tipo detectado + botones de acción" para una entrada leída, sin duplicar
// el when(qrType) que ya existía inline ahí). Comportamiento idéntico al
// que ya tenía QrReaderScreen -- ningún cambio funcional para el Lector.

@Composable
internal fun qrContentTypeVisuals(qrType: QrContentType): Triple<ImageVector, Color, String> {
    val accentTypeColor = MaterialTheme.colorScheme.primary
    return when (qrType) {
        QrContentType.URL ->
            Triple(Icons.Rounded.Link, accentTypeColor, stringResource(R.string.qr_type_url_detected))
        QrContentType.IMAGE ->
            Triple(Icons.Rounded.Image, SuccessGreen, stringResource(R.string.qr_type_image_detected))
        QrContentType.DOCUMENT ->
            Triple(Icons.Rounded.Description, accentTypeColor, stringResource(R.string.qr_type_document_detected))
        QrContentType.EMAIL ->
            Triple(Icons.Rounded.Email, accentTypeColor, stringResource(R.string.qr_type_email_detected))
        QrContentType.PHONE ->
            Triple(Icons.Rounded.Phone, SuccessGreen, stringResource(R.string.qr_type_phone_detected))
        QrContentType.TEXT ->
            Triple(Icons.Rounded.TextFields, accentTypeColor, stringResource(R.string.qr_type_text_detected))
        QrContentType.WIFI ->
            Triple(Icons.Rounded.Wifi, accentTypeColor, stringResource(R.string.qr_type_wifi_detected))
        QrContentType.CONTACT ->
            Triple(Icons.Rounded.ContactPage, accentTypeColor, stringResource(R.string.qr_type_contact_detected))
        QrContentType.EVENT ->
            Triple(Icons.Rounded.CalendarMonth, accentTypeColor, stringResource(R.string.qr_type_event_detected))
    }
}

@Composable
internal fun QrContentActionButtons(
    qrType: QrContentType,
    content: String,
    onCopied: () -> Unit
) {
    val context = LocalContext.current
    val openDocumentLabel = stringResource(R.string.qr_open_document)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (qrType) {
            QrContentType.URL -> {
                Button(
                    onClick = { openUrl(context, content) },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_open_browser))
                }
                OutlinedButton(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_url))
                }
            }
            QrContentType.IMAGE -> {
                Button(
                    onClick = { openUrl(context, content) },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_open_image))
                }
                OutlinedButton(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_link))
                }
            }
            QrContentType.DOCUMENT -> {
                Button(
                    onClick = { openDocumentExternally(context, content, openDocumentLabel) },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_open_document))
                }
                OutlinedButton(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_path))
                }
            }
            QrContentType.EMAIL -> {
                Button(
                    onClick = { openUrl(context, content) },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Email, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_send_email))
                }
                OutlinedButton(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_email))
                }
            }
            QrContentType.PHONE -> {
                Button(
                    onClick = { openUrl(context, content) },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Phone, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_call))
                }
                OutlinedButton(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_number))
                }
            }
            QrContentType.TEXT -> {
                Button(
                    onClick = { copyToClipboard(context, content); onCopied() },
                    modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_copy_text))
                }
            }
            // Hallazgo real #5: acá es donde antes solo se ofrecía "Copiar
            // texto" con el WIFI:/vCard crudo -- ahora se parsea y se ofrece
            // la acción real (conectar/agregar), igual que ya hace la
            // cámara nativa de Android/iOS con estos mismos formatos.
            // Extraídas a composables propios (detekt: LongMethod).
            QrContentType.WIFI    -> WifiActionButtons(context, content, onCopied)
            QrContentType.CONTACT -> ContactActionButtons(context, content, onCopied)
            QrContentType.EVENT   -> EventActionButtons(context, content, onCopied)
        }
    }
}

@Composable
private fun WifiActionButtons(context: Context, content: String, onCopied: () -> Unit) {
    val wifi = parseWifiPayload(content)
    if (wifi != null && wifi.password.isNotBlank()) {
        Button(
            onClick = { copyToClipboard(context, wifi.password); onCopied() },
            modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.qr_copy_wifi_password))
        }
    }
    OutlinedButton(
        onClick = { copyToClipboard(context, wifi?.ssid ?: content); onCopied() },
        modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.qr_copy_wifi_ssid))
    }
}

@Composable
private fun ContactActionButtons(context: Context, content: String, onCopied: () -> Unit) {
    val contact = parseVCardPayload(content)
    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): el
    // botón quedaba clickeable pero sin hacer nada si el payload no se
    // pudo interpretar -- se deshabilita en vez de simular una acción que
    // no pasa nada.
    Button(
        onClick = { if (contact != null) addContact(context, contact.name, contact.phone, contact.email) },
        enabled = contact != null,
        modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.qr_add_contact))
    }
    OutlinedButton(
        onClick = { copyToClipboard(context, content); onCopied() },
        modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.qr_copy_text))
    }
}

@Composable
private fun EventActionButtons(context: Context, content: String, onCopied: () -> Unit) {
    val event = parseVEventPayload(content)
    // Ver el comentario equivalente en ContactActionButtons más arriba.
    Button(
        onClick = {
            if (event != null) addCalendarEvent(context, event.title, event.location, event.start, event.end)
        },
        enabled = event != null,
        modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.qr_add_calendar_event))
    }
    OutlinedButton(
        onClick = { copyToClipboard(context, content); onCopied() },
        modifier = Modifier.fillMaxWidth().accentBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.qr_copy_text))
    }
}

// Hallazgo real de la auditoría general 2026-09-17 (M6): sin ninguna app
// que maneje el Intent (sin cliente de contactos/calendario configurado en
// el dispositivo), antes no pasaba absolutamente nada visible -- el
// usuario no tenía forma de saber si funcionó o falló.
@Suppress("TooGenericExceptionCaught")
private fun addContact(context: Context, name: String, phone: String, email: String) {
    try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            if (name.isNotBlank())  putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "addContact")
        Toast.makeText(context, context.getString(R.string.qr_action_no_app), Toast.LENGTH_SHORT).show()
    }
}

@Suppress("TooGenericExceptionCaught")
private fun addCalendarEvent(
    context: Context,
    title: String,
    location: String,
    start: java.time.LocalDateTime,
    end: java.time.LocalDateTime
) {
    try {
        val zone = java.time.ZoneId.systemDefault()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            if (title.isNotBlank())    putExtra(CalendarContract.Events.TITLE, title)
            if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(zone).toInstant().toEpochMilli())
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "addCalendarEvent")
        Toast.makeText(context, context.getString(R.string.qr_action_no_app), Toast.LENGTH_SHORT).show()
    }
}
