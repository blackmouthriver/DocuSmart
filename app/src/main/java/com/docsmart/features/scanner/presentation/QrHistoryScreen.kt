package com.docsmart.features.scanner.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.scanner.domain.QrCrypto
import com.docsmart.features.scanner.domain.QrHistoryEntry
import com.docsmart.features.scanner.domain.QrHistorySource
import com.docsmart.features.scanner.domain.QrHistoryStorage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HU-44 (backlog UX 2026-08-30/09-14): historial de los últimos códigos QR
 * creados o leídos, accesible desde el Creador y el Lector (RF2). Persistido
 * con `QrHistoryStorage` (SharedPreferences + JSON, sin ViewModel -- mismo
 * patrón que Notas/Progreso de lectura de Estudio).
 */
@Composable
fun QrHistoryScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(QrHistoryStorage.loadAll(context)) }
    var pendingDelete by remember { mutableStateOf<QrHistoryEntry?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var viewingEntry by remember { mutableStateOf<QrHistoryEntry?>(null) }

    fun refresh() { entries = QrHistoryStorage.loadAll(context) }

    pendingDelete?.let { entry ->
        QrHistoryDeleteDialog(
            onConfirm = { QrHistoryStorage.remove(context, entry.id); pendingDelete = null; refresh() },
            onDismiss = { pendingDelete = null }
        )
    }
    if (pendingClearAll) {
        QrHistoryClearAllDialog(
            count = entries.size,
            onConfirm = { QrHistoryStorage.clear(context); pendingClearAll = false; refresh() },
            onDismiss = { pendingClearAll = false }
        )
    }
    viewingEntry?.let { entry ->
        QrHistoryEntryDialog(entry = entry, onDismiss = { viewingEntry = null })
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        DocuSmartTopBanner(
            screenTitle    = stringResource(R.string.qr_history_title),
            screenSubtitle = stringResource(R.string.qr_history_subtitle),
            onBack         = onBack,
            actions = {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { pendingClearAll = true }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.qr_history_clear_all),
                            tint = Color.White
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        if (entries.isEmpty()) {
            QrHistoryEmptyState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.id }) { entry ->
                    QrHistoryRow(
                        entry = entry,
                        onClick = { viewingEntry = entry },
                        onDelete = { pendingDelete = entry }
                    )
                }
            }
        }
    }
}

@Composable
private fun QrHistoryEmptyState() {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            stringResource(R.string.qr_history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.qr_history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val HISTORY_DATE_FORMAT = "d MMM yyyy, HH:mm"

@Composable
private fun QrHistoryRow(entry: QrHistoryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val dateLabel = remember(entry.createdAtMillis) {
        SimpleDateFormat(HISTORY_DATE_FORMAT, Locale.getDefault()).format(Date(entry.createdAtMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .accentShadow(shape, elevation = 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .accentBorder(shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                historyTypeIcon(entry.typeName),
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    historyTypeLabel(entry.typeName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    if (entry.source == QrHistorySource.CREATED) Icons.Rounded.QrCode else Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (entry.source == QrHistorySource.CREATED) {
                        stringResource(R.string.qr_history_source_created)
                    } else {
                        stringResource(R.string.qr_history_source_scanned)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (entry.typeName == "PROTECTED") {
                    stringResource(R.string.qr_history_protected_content)
                } else {
                    entry.content
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.general_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun historyTypeIcon(typeName: String): ImageVector = when (typeName) {
    "URL"       -> Icons.Rounded.Link
    "TEXT"      -> Icons.Rounded.TextFields
    "EMAIL"     -> Icons.Rounded.Email
    "PHONE"     -> Icons.Rounded.Phone
    "IMAGE"     -> Icons.Rounded.Image
    "DOCUMENT"  -> Icons.Rounded.Description
    "WIFI"      -> Icons.Rounded.Wifi
    "CONTACT"   -> Icons.Rounded.ContactPage
    "EVENT"     -> Icons.Rounded.CalendarMonth
    "PROTECTED" -> Icons.Rounded.Lock
    else        -> Icons.Rounded.QrCode
}

@Composable
private fun historyTypeLabel(typeName: String): String = when (typeName) {
    "URL"       -> stringResource(R.string.qr_chip_url)
    "TEXT"      -> stringResource(R.string.qr_chip_text)
    "EMAIL"     -> stringResource(R.string.qr_chip_email)
    "PHONE"     -> stringResource(R.string.qr_chip_phone)
    "IMAGE"     -> stringResource(R.string.qr_chip_image)
    "DOCUMENT"  -> stringResource(R.string.qr_chip_document)
    "WIFI"      -> stringResource(R.string.qr_chip_wifi)
    "CONTACT"   -> stringResource(R.string.qr_chip_contact)
    "EVENT"     -> stringResource(R.string.qr_chip_event)
    "PROTECTED" -> stringResource(R.string.qr_protected_badge)
    else        -> typeName
}

@Composable
private fun QrHistoryDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_history_delete_confirm_title)) },
        text  = { Text(stringResource(R.string.qr_history_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) } }
    )
}

@Composable
private fun QrHistoryClearAllDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_history_clear_all_confirm_title)) },
        text  = { Text(stringResource(R.string.qr_history_clear_all_confirm_body, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) } }
    )
}

// RF2: "volver a generar" (Creador) o "copiar/abrir" (Lector) según el
// origen de la entrada.
@Composable
private fun QrHistoryEntryDialog(entry: QrHistoryEntry, onDismiss: () -> Unit) {
    when (entry.source) {
        QrHistorySource.CREATED -> QrHistoryRegenerateDialog(entry, onDismiss)
        QrHistorySource.SCANNED -> QrHistoryScannedResultDialog(entry, onDismiss)
    }
}

// El contenido guardado (`entry.content`) ya incluye el cifrado si el QR
// original estaba protegido -- regenerar el mismo bitmap no requiere volver
// a pedir la contraseña, reproduce exactamente el QR ya compartido antes.
@Composable
private fun QrHistoryRegenerateDialog(entry: QrHistoryEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val savedMsg = stringResource(R.string.general_saved_downloads)
    val shareTitle = stringResource(R.string.qr_share_chooser_title)

    LaunchedEffect(entry.id) { bitmap = generateQrBitmap(entry.content) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.qr_your_code),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier.size(220.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBitmap = bitmap
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_generated_content_desc),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                statusMsg?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = SuccessGreen) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val currentBitmap = bitmap ?: return@OutlinedButton
                            scope.launch {
                                val file = saveQrToFile(context, currentBitmap)
                                if (file != null) {
                                    DownloadsSaver.saveFile(context, file, "image/png")
                                    statusMsg = savedMsg
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).accentBorder(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        enabled = bitmap != null
                    ) { Text(stringResource(R.string.general_save)) }
                    Button(
                        onClick = {
                            val currentBitmap = bitmap ?: return@Button
                            scope.launch {
                                val file = saveQrToFile(context, currentBitmap)
                                if (file != null) shareQrImage(context, file, shareTitle)
                            }
                        },
                        modifier = Modifier.weight(1f).accentBorder(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        enabled = bitmap != null
                    ) { Text(stringResource(R.string.general_share)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
            }
        }
    }
}

// Entrada leída (Lector): si está protegida, pide la contraseña primero
// (mismo flujo que QrReaderScreen); si no, muestra directo el tipo
// detectado + los botones de acción ya existentes (QrResultDisplay.kt).
@Composable
private fun QrHistoryScannedResultDialog(entry: QrHistoryEntry, onDismiss: () -> Unit) {
    var decrypted by remember(entry.id) {
        mutableStateOf(if (entry.typeName != "PROTECTED") entry.content else null)
    }
    var copied by remember { mutableStateOf(false) }

    val content = decrypted
    if (content == null) {
        QrHistoryPasswordDialog(
            entry = entry,
            onUnlocked = { decrypted = it },
            onDismiss = onDismiss
        )
    } else {
        val qrType = detectQrContentType(content)
        val (typeIcon, typeColor, typeLabel) = qrContentTypeVisuals(qrType)
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(20.dp))
                        Text(typeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 5
                        )
                    }
                    if (copied) {
                        Text(
                            stringResource(R.string.qr_copied_clipboard),
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                    QrContentActionButtons(qrType = qrType, content = content, onCopied = { copied = true })
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.general_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun QrHistoryPasswordDialog(
    entry: QrHistoryEntry,
    onUnlocked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wrongMsg = stringResource(R.string.pdf_pw_wrong_password)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.qr_protected_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.qr_protected_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.qr_password_label)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                        }
                    },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val raw = entry.content.removePrefix(QrCrypto.PREFIX)
                val result = QrCrypto.decrypt(raw, password)
                if (result != null) onUnlocked(result) else error = wrongMsg
            }) { Text(stringResource(R.string.qr_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) } }
    )
}
