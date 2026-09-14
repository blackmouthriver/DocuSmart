package com.docsmart.features.scanner.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.TextFields
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
        }
    }
}
