package com.docsmart.features.security.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.security.domain.PdfPasswordMessages

@Composable
fun PdfPasswordScreen(
    onBack   : () -> Unit = {},
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPasswordMessages = PdfPasswordMessages(
        readError            = stringResource(R.string.pdf_pw_read_error),
        emptyFile             = stringResource(R.string.pdf_pw_empty_file),
        protectSuccess        = stringResource(R.string.pdf_pw_protect_success),
        protectGenerateError  = stringResource(R.string.pdf_pw_protect_generate_error),
        protectError          = stringResource(R.string.pdf_pw_protect_error),
        removeSuccess         = stringResource(R.string.pdf_pw_remove_success),
        removeGenerateError   = stringResource(R.string.pdf_pw_remove_generate_error),
        removeError           = stringResource(R.string.pdf_pw_remove_error)
    )
    val wrongPasswordMessage      = stringResource(R.string.pdf_pw_wrong_password)
    val wrongPasswordRetryMessage = stringResource(R.string.pdf_pw_wrong_password_retry)
    val saveDownloadsSuccessTemplate = stringResource(R.string.security_save_downloads_success)
    val saveDownloadsErrorMessage    = stringResource(R.string.security_save_downloads_error)
    val defaultDocumentName          = stringResource(R.string.pdf_pw_default_document_name)

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                top    = 24.dp, bottom = 100.dp,
                start  = 20.dp, end    = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    DocuSmartTopBanner(
                        screenTitle    = stringResource(R.string.security_pdf_password),
                        screenSubtitle = stringResource(R.string.pdf_pw_screen_subtitle),
                        modifier       = Modifier.weight(1f)
                    )
                }
            }

            // ── Selector de modo ──────────────────────────────────────────────
            item {
                if (uiState.pdfPasswordMode == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text       = stringResource(R.string.security_what_to_do),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )

                        // Tarjeta Proteger
                        ModeCard(
                            icon        = Icons.Rounded.Lock,
                            title       = stringResource(R.string.pdf_pw_mode_protect_title),
                            description = stringResource(R.string.pdf_pw_mode_protect_desc),
                            color       = MaterialTheme.colorScheme.primary,
                            onClick     = { viewModel.setPdfPasswordMode(PdfPasswordMode.PROTECT) }
                        )

                        // Tarjeta Quitar
                        ModeCard(
                            icon        = Icons.Rounded.LockOpen,
                            title       = stringResource(R.string.pdf_pw_mode_remove_title),
                            description = stringResource(R.string.pdf_pw_mode_remove_desc),
                            color       = MaterialTheme.colorScheme.error,
                            onClick     = { viewModel.setPdfPasswordMode(PdfPasswordMode.REMOVE) }
                        )
                    }
                }
            }

            // ── Formulario Proteger ───────────────────────────────────────────
            if (uiState.pdfPasswordMode == PdfPasswordMode.PROTECT) {
                item {
                    ProtectPdfForm(
                        uiState   = uiState,
                        context   = context,
                        defaultDocumentName = defaultDocumentName,
                        onProtect = { uri, password, fileName ->
                            viewModel.protectPdfWithPassword(
                                context, uri, password, fileName,
                                pdfPasswordMessages, wrongPasswordMessage
                            )
                        },
                        onCancel  = {
                            viewModel.setPdfPasswordMode(null)
                            viewModel.dismissPdfResult()
                        },
                        onSaveToDownloads = { file ->
                            viewModel.savePdfToDownloads(
                                context, file,
                                saveDownloadsSuccessTemplate, saveDownloadsErrorMessage
                            )
                        }
                    )
                }
            }

            // ── Formulario Quitar ─────────────────────────────────────────────
            if (uiState.pdfPasswordMode == PdfPasswordMode.REMOVE) {
                item {
                    RemovePdfPasswordForm(
                        uiState  = uiState,
                        context  = context,
                        defaultDocumentName = defaultDocumentName,
                        onRemove = { uri, password, fileName ->
                            viewModel.removePdfPassword(
                                context, uri, password, fileName,
                                pdfPasswordMessages, wrongPasswordRetryMessage
                            )
                        },
                        onCancel = {
                            viewModel.setPdfPasswordMode(null)
                            viewModel.dismissPdfResult()
                        },
                        onSaveToDownloads = { file ->
                            viewModel.savePdfToDownloads(
                                context, file,
                                saveDownloadsSuccessTemplate, saveDownloadsErrorMessage
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Tarjeta de modo ───────────────────────────────────────────────────────────
@Composable
private fun ModeCard(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    title      : String,
    description: String,
    color      : androidx.compose.ui.graphics.Color,
    onClick    : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = color.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {}
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Formulario proteger PDF ───────────────────────────────────────────────────
@Composable
private fun ProtectPdfForm(
    uiState          : SecurityUiState,
    context          : android.content.Context,
    defaultDocumentName: String,
    onProtect        : (Uri, String, String) -> Unit,
    onCancel         : () -> Unit,
    onSaveToDownloads: (java.io.File) -> Unit
) {
    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var fileName     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var confirmPass  by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            context.contentResolver.query(
                it,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0)?.removeSuffix(".pdf") ?: defaultDocumentName
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Título sección
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Lock, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(
                text       = stringResource(R.string.pdf_pw_protect_section_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        // Selector PDF
        Card(
            modifier  = Modifier.fillMaxWidth().clickable { pdfLauncher.launch("application/pdf") },
            shape     = MaterialTheme.shapes.large,
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = if (selectedUri != null) fileName.ifBlank { stringResource(R.string.pdf_pw_selected_placeholder) }
                        else stringResource(R.string.pdf_pw_tap_to_select),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedUri != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedUri != null) {
                        Text(
                            text  = stringResource(R.string.pdf_pw_ready_to_protect),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    if (selectedUri != null) Icons.Rounded.CheckCircle
                    else Icons.Rounded.FolderOpen,
                    null,
                    tint = if (selectedUri != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Campo contraseña
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(stringResource(R.string.pdf_pw_new_password_label)) },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon  = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff
                        else Icons.Rounded.Visibility, null
                    )
                }
            },
            singleLine = true,
            shape      = MaterialTheme.shapes.medium
        )

        // Confirmar contraseña
        OutlinedTextField(
            value         = confirmPass,
            onValueChange = { confirmPass = it },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(stringResource(R.string.pdf_pw_confirm_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            isError       = confirmPass.isNotEmpty() && password != confirmPass,
            supportingText = {
                if (confirmPass.isNotEmpty() && password != confirmPass)
                    Text(stringResource(R.string.pdf_pw_passwords_dont_match),
                        color = MaterialTheme.colorScheme.error)
            },
            singleLine = true,
            shape      = MaterialTheme.shapes.medium
        )

        // Error
        uiState.pdfPasswordError?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium,
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Error, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Text(it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Resultado exitoso
        uiState.pdfOutputFile?.let { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium,
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Text(
                            text       = stringResource(R.string.pdf_pw_protected_success_title),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text  = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick  = { onSaveToDownloads(file) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pdf_pw_save_downloads_button))
                    }
                }
            }
        }

        // Botones
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.general_cancel)) }

            Button(
                onClick  = {
                    if (selectedUri != null && password.isNotBlank() && password == confirmPass) {
                        onProtect(selectedUri!!, password, fileName.ifBlank { defaultDocumentName })
                    }
                },
                enabled  = selectedUri != null && password.isNotBlank() &&
                        password == confirmPass && !uiState.isPdfProcessing,
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.medium
            ) {
                if (uiState.isPdfProcessing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_pw_protect_button))
                }
            }
        }
    }
}

// ── Formulario quitar contraseña ──────────────────────────────────────────────
@Composable
private fun RemovePdfPasswordForm(
    uiState          : SecurityUiState,
    context          : android.content.Context,
    defaultDocumentName: String,
    onRemove         : (Uri, String, String) -> Unit,
    onCancel         : () -> Unit,
    onSaveToDownloads: (java.io.File) -> Unit
) {
    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var fileName     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            context.contentResolver.query(
                it,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0)?.removeSuffix(".pdf") ?: defaultDocumentName
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.LockOpen, null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp))
            Text(
                text       = stringResource(R.string.pdf_pw_remove_section_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        // Selector PDF
        Card(
            modifier  = Modifier.fillMaxWidth().clickable { pdfLauncher.launch("application/pdf") },
            shape     = MaterialTheme.shapes.large,
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = if (selectedUri != null) fileName.ifBlank { stringResource(R.string.pdf_pw_selected_placeholder) }
                        else stringResource(R.string.pdf_pw_tap_to_select_protected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedUri != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedUri != null) {
                        Text(
                            text  = stringResource(R.string.pdf_pw_ready),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Icon(
                    if (selectedUri != null) Icons.Rounded.CheckCircle
                    else Icons.Rounded.FolderOpen,
                    null,
                    tint     = if (selectedUri != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Contraseña actual
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(stringResource(R.string.pdf_pw_current_password_label)) },
            placeholder   = { Text(stringResource(R.string.pdf_pw_current_password_placeholder)) },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon  = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff
                        else Icons.Rounded.Visibility, null
                    )
                }
            },
            singleLine = true,
            shape      = MaterialTheme.shapes.medium
        )

        // Aviso
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = MaterialTheme.shapes.medium,
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
                Text(
                    text  = stringResource(R.string.pdf_pw_remove_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Error
        uiState.pdfPasswordError?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium,
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Error, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Text(it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Resultado exitoso
        uiState.pdfOutputFile?.let { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = MaterialTheme.shapes.medium,
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Text(
                            text       = stringResource(R.string.pdf_pw_removed_success_title),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text  = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick  = { onSaveToDownloads(file) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pdf_pw_save_downloads_button))
                    }
                }
            }
        }

        // Botones
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.general_cancel)) }

            Button(
                onClick  = {
                    if (selectedUri != null && password.isNotBlank()) {
                        onRemove(selectedUri!!, password, fileName.ifBlank { defaultDocumentName })
                    }
                },
                enabled  = selectedUri != null && password.isNotBlank() && !uiState.isPdfProcessing,
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.medium,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (uiState.isPdfProcessing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_pw_remove_button))
                }
            }
        }
    }
}