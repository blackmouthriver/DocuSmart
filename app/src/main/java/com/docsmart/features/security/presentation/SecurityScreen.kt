package com.docsmart.features.security.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.SmartBlue
import timber.log.Timber

@Composable
fun SecurityScreen(
    onBack       : () -> Unit = {},
    onPdfPassword: () -> Unit = {},
    viewModel    : SecurityViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current

    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null as FragmentActivity?
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val incorrectPinMessage    = stringResource(R.string.security_pin_incorrect)
    val biometricPromptTitle   = stringResource(R.string.security_biometric_title)
    val biometricPromptSubtitle = stringResource(R.string.security_biometric_subtitle)
    val usePinLabel            = stringResource(R.string.security_biometric_use_pin)
    val biometricErrorTemplate = stringResource(R.string.security_biometric_error)
    val biometricNotRecognized = stringResource(R.string.security_biometric_not_recognized)
    val fileProtectedSuccess   = stringResource(R.string.security_file_protected_success)
    val fileProtectError       = stringResource(R.string.security_file_protect_error)
    val fileProtectedOriginalKept = stringResource(R.string.security_file_protected_original_kept)

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        if (uiState.screenState == SecurityScreenState.UNLOCKED) {
            uiState.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.dismissError()
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState.screenState) {
                SecurityScreenState.LOCKED -> {
                    PinUnlockScreen(
                        hasPin               = uiState.hasPin,
                        isBiometricAvailable = uiState.isBiometricAvailable,
                        isBiometricEnabled   = uiState.isBiometricEnabled,
                        error                = uiState.error,
                        onPinEntered         = { pin -> viewModel.verifyPin(pin, incorrectPinMessage) },
                        onBiometricClick     = {
                            activity?.let {
                                viewModel.authenticateWithBiometric(
                                    activity             = it,
                                    promptTitle          = biometricPromptTitle,
                                    promptSubtitle       = biometricPromptSubtitle,
                                    usePinLabel          = usePinLabel,
                                    errorTemplate        = biometricErrorTemplate,
                                    notRecognizedMessage = biometricNotRecognized
                                )
                            } ?: Timber.e("FragmentActivity es null")
                        },
                        onSetupPin = { viewModel.goToSetupPin() },
                        onBack     = onBack
                    )
                }
                SecurityScreenState.SETUP_PIN -> {
                    SetupPinScreen(
                        onPinSet = { pin -> viewModel.setupPin(pin) },
                        onBack   = { viewModel.goToLocked() }
                    )
                }
                SecurityScreenState.UNLOCKED -> {
                    SecureFolderContent(
                        uiState           = uiState,
                        onBack            = onBack,
                        onDeleteFile      = { file -> viewModel.deleteFile(file) },
                        onRestoreFile     = { file -> viewModel.restoreFile(file, context) },
                        onChangePinClick  = { viewModel.goToSetupPin() },
                        onToggleBiometric = { viewModel.toggleBiometric() },
                        onImportFile      = { uri ->
                            viewModel.importFileToSecure(
                                context, uri,
                                fileProtectedSuccess, fileProtectError, fileProtectedOriginalKept
                            )
                        },
                        onImportLocalFile = { file -> viewModel.importLocalFile(file, fileProtectedSuccess, fileProtectError) }
                    )
                }
            }
        }
    }
}

// ── Pantalla de desbloqueo con PIN ────────────────────────────────────────────
@Composable
private fun PinUnlockScreen(
    hasPin               : Boolean,
    isBiometricAvailable : Boolean,
    isBiometricEnabled   : Boolean,
    error                : String?,
    onPinEntered         : (String) -> Unit,
    onBiometricClick     : () -> Unit,
    onSetupPin           : () -> Unit,
    onBack               : () -> Unit
) {
    var pin = remember { mutableStateOf("") }
    val pinLength = 4

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(DocuBlue, SmartBlue, IndigoAccent)
                )
            )
    ) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Rounded.ArrowBack, stringResource(R.string.general_back), tint = Color.White)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier            = Modifier.align(Alignment.Center).padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.15f), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null,
                    tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Text(
                text       = stringResource(if (hasPin) R.string.security_enter_pin else R.string.security_setup_pin_title),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )

            if (!hasPin) {
                Text(
                    text      = stringResource(R.string.security_setup_pin_desc),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onSetupPin,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = DocuBlue
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.security_configure_pin_button), fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(pinLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (index < pin.value.length) Color.White
                                    else Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }

                error?.let {
                    Text(it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.errorContainer)
                }

                NumericKeypad(
                    onDigit = { digit ->
                        if (pin.value.length < pinLength) {
                            pin.value += digit
                            if (pin.value.length == pinLength) {
                                onPinEntered(pin.value)
                                pin.value = ""
                            }
                        }
                    },
                    onDelete = {
                        if (pin.value.isNotEmpty()) pin.value = pin.value.dropLast(1)
                    }
                )

                if (isBiometricAvailable && isBiometricEnabled) {
                    TextButton(onClick = onBiometricClick) {
                        Icon(Icons.Rounded.Fingerprint, null,
                            tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.security_use_biometric), color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Teclado numérico ──────────────────────────────────────────────────────────
@Composable
private fun NumericKeypad(
    onDigit : (String) -> Unit,
    onDelete: () -> Unit
) {
    val keys = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("","0","⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key -> NumericKeypadKey(key, onDigit, onDelete) }
            }
        }
    }
}

@Composable
private fun NumericKeypadKey(
    key     : String,
    onDigit : (String) -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (key.isNotEmpty()) Color.White.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(enabled = key.isNotEmpty()) {
                if (key == "⌫") onDelete() else onDigit(key)
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            key == "⌫" -> Icon(
                Icons.Rounded.Backspace, stringResource(R.string.security_delete_desc),
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
            key.isNotEmpty() -> Text(
                key,
                fontSize   = 24.sp,
                fontWeight = FontWeight.Medium,
                color      = Color.White
            )
        }
    }
}

// ── Pantalla de configuración de PIN ─────────────────────────────────────────
@Composable
private fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onBack  : () -> Unit
) {
    var pin         = remember { mutableStateOf("") }
    var confirmPin  = remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    val pinLength    = 4
    val pinsDontMatchMessage = stringResource(R.string.security_pins_dont_match)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(DocuBlue, SmartBlue, IndigoAccent))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.general_back), tint = Color.White)
            }

            Icon(Icons.Rounded.LockOpen, null,
                tint = Color.White, modifier = Modifier.size(56.dp))

            Text(
                text       = stringResource(if (isConfirming) R.string.security_confirm_pin else R.string.security_create_pin),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )

            Text(
                text = stringResource(
                    if (isConfirming) R.string.security_confirm_pin_desc
                    else R.string.security_create_pin_desc
                ),
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            val currentPin = if (isConfirming) confirmPin.value else pin.value
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(pinLength) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (index < currentPin.length) Color.White
                                else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
            }

            error?.let {
                Text(it,
                    color = MaterialTheme.colorScheme.errorContainer,
                    style = MaterialTheme.typography.bodySmall)
            }

            NumericKeypad(
                onDigit = { digit ->
                    if (isConfirming) {
                        if (confirmPin.value.length < pinLength) {
                            confirmPin.value += digit
                            if (confirmPin.value.length == pinLength) {
                                if (confirmPin.value == pin.value) {
                                    onPinSet(pin.value)
                                } else {
                                    error = pinsDontMatchMessage
                                    confirmPin.value = ""
                                }
                            }
                        }
                    } else {
                        if (pin.value.length < pinLength) {
                            pin.value += digit
                            if (pin.value.length == pinLength) {
                                isConfirming = true
                                error        = null
                            }
                        }
                    }
                },
                onDelete = {
                    error = null
                    if (isConfirming) {
                        if (confirmPin.value.isNotEmpty())
                            confirmPin.value = confirmPin.value.dropLast(1)
                    } else {
                        if (pin.value.isNotEmpty())
                            pin.value = pin.value.dropLast(1)
                    }
                }
            )
        }
    }
}

// ── Contenido de la carpeta segura ────────────────────────────────────────────
@Composable
private fun SecureFolderContent(
    uiState          : SecurityUiState,
    onBack           : () -> Unit,
    onDeleteFile     : (java.io.File) -> Unit,
    onRestoreFile    : (java.io.File) -> Unit,
    onChangePinClick : () -> Unit,
    onToggleBiometric: () -> Unit,
    onImportFile     : (Uri) -> Unit,
    onImportLocalFile: (java.io.File) -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }

    // OpenDocument (no GetContent) porque devuelve un Uri de documento que sí soporta
    // DocumentsContract.deleteDocument en la mayoría de proveedores — necesario para
    // poder borrar el original al proteger un archivo (RF-SEC-05).
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportFile(it) } }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.security_protect_file_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.security_import_source_question),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showImportDialog = false
                            fileLauncher.launch(arrayOf("*/*"))
                        },
                        shape  = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier              = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(stringResource(R.string.security_from_device),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.security_browse_system_files),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (uiState.appFiles.isNotEmpty()) {
                        Text(stringResource(R.string.security_from_library),
                            style    = MaterialTheme.typography.titleSmall,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp))
                        LazyColumn(
                            modifier            = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(uiState.appFiles) { _, file ->
                                Card(
                                    modifier  = Modifier.fillMaxWidth().clickable {
                                        showImportDialog = false
                                        onImportLocalFile(file)
                                    },
                                    shape     = MaterialTheme.shapes.medium,
                                    colors    = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(1.dp)
                                ) {
                                    Row(
                                        modifier              = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.InsertDriveFile, null,
                                            tint     = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name,
                                                style    = MaterialTheme.typography.bodySmall,
                                                color    = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1)
                                            Text("${file.length() / 1024} KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.security_no_library_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(R.string.general_back),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(4.dp))
                DocuSmartTopBanner(
                    screenTitle    = stringResource(R.string.security_secure_folder),
                    screenSubtitle = stringResource(R.string.security_files_protected_count, uiState.secureFiles.size),
                    modifier       = Modifier.weight(1f)
                )
            }
        }

        item {
            Button(
                onClick  = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Rounded.AddCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.security_protect_new_file), style = MaterialTheme.typography.labelLarge)
            }
        }

        item {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.security_config_section),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface)

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChangePinClick() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Pin, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.security_change_pin),
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color    = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Rounded.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    if (uiState.isBiometricAvailable) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Fingerprint, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp))
                            Text(stringResource(R.string.security_biometric_unlock),
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color    = MaterialTheme.colorScheme.onSurface)
                            Switch(
                                checked         = uiState.isBiometricEnabled,
                                onCheckedChange = { onToggleBiometric() }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.security_protected_files_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface)
        }

        if (uiState.secureFiles.isEmpty()) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = MaterialTheme.shapes.large,
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, null,
                            tint = PremiumGold, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.security_no_protected_files),
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Text(stringResource(R.string.security_no_protected_files_hint),
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            itemsIndexed(uiState.secureFiles) { _, file ->
                SecureFileItem(
                    file      = file,
                    onDelete  = { onDeleteFile(file) },
                    onRestore = { onRestoreFile(file) }
                )
            }
        }
    }
}

// ── Item de archivo seguro ────────────────────────────────────────────────────
@Composable
private fun SecureFileItem(
    file     : java.io.File,
    onDelete : () -> Unit,
    onRestore: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PremiumGold.copy(alpha = 0.15f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null,
                    tint = PremiumGold, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1)
                Text("${file.length() / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded          = showMenu,
                    onDismissRequest  = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text         = { Text(stringResource(R.string.security_restore)) },
                        leadingIcon  = { Icon(Icons.Rounded.DriveFileMove, null) },
                        onClick      = { showMenu = false; onRestore() }
                    )
                    DropdownMenuItem(
                        text         = { Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon  = {
                            Icon(Icons.Rounded.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}