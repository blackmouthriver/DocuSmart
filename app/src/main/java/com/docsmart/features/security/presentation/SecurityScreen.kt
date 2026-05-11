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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.SmartBlue
import timber.log.Timber

@Composable
fun SecurityScreen(
    onBack: () -> Unit = {},
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null as FragmentActivity?
    }

    // ── Mostrar mensajes de éxito ─────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState.screenState) {
                SecurityScreenState.LOCKED -> {
                    PinUnlockScreen(
                        hasPin = uiState.hasPin,
                        isBiometricAvailable = uiState.isBiometricAvailable,
                        isBiometricEnabled = uiState.isBiometricEnabled,
                        error = uiState.error,
                        onPinEntered = { pin -> viewModel.verifyPin(pin) },
                        onBiometricClick = {
                            activity?.let {
                                viewModel.authenticateWithBiometric(it)
                            } ?: Timber.e("FragmentActivity es null")
                        },
                        onSetupPin = { viewModel.goToSetupPin() },
                        onBack = onBack
                    )
                }
                SecurityScreenState.SETUP_PIN -> {
                    SetupPinScreen(
                        onPinSet = { pin -> viewModel.setupPin(pin) },
                        onBack = { viewModel.goToLocked() }
                    )
                }
                SecurityScreenState.UNLOCKED -> {
                    SecureFolderContent(
                        uiState = uiState,
                        onBack = onBack,
                        onDeleteFile = { file -> viewModel.deleteFile(file) },
                        onRestoreFile = { file -> viewModel.restoreFile(file, context) },
                        onChangePinClick = { viewModel.goToSetupPin() },
                        onToggleBiometric = { viewModel.toggleBiometric() },
                        onImportFile = { uri -> viewModel.importFileToSecure(context, uri) },
                        onImportLocalFile = { file -> viewModel.importLocalFile(file) } // ← nuevo
                    )
                }
            }
        }
    }
}

// ── Pantalla de desbloqueo con PIN ────────────────────
@Composable
private fun PinUnlockScreen(
    hasPin: Boolean,
    isBiometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    error: String?,
    onPinEntered: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onSetupPin: () -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
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
        // ── Botón volver ──────────────────────────────
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Color.White.copy(alpha = 0.15f),
                        MaterialTheme.shapes.extraLarge
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = if (hasPin) "Ingresa tu PIN" else "Configura tu PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (!hasPin) {
                Text(
                    text = "Protege tus archivos con un PIN de 4 dígitos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onSetupPin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = DocuBlue
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Configurar PIN", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(pinLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (index < pin.length) Color.White
                                    else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.errorContainer
                    )
                }

                NumericKeypad(
                    onDigit = { digit ->
                        if (pin.length < pinLength) {
                            pin += digit
                            if (pin.length == pinLength) {
                                onPinEntered(pin)
                                pin = ""
                            }
                        }
                    },
                    onDelete = {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                )

                if (isBiometricAvailable && isBiometricEnabled) {
                    TextButton(onClick = onBiometricClick) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Usar biometría", color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Teclado numérico ──────────────────────────────────
@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
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
                        if (key == "⌫") {
                            Icon(
                                imageVector = Icons.Rounded.Backspace,
                                contentDescription = "Borrar",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Pantalla de configuración de PIN ──────────────────
@Composable
private fun SetupPinScreen(
    onPinSet: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLength = 4

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(DocuBlue, SmartBlue, IndigoAccent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }

            Icon(
                imageVector = Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = if (isConfirming) "Confirma tu PIN" else "Crea tu PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = if (isConfirming)
                    "Ingresa el PIN nuevamente para confirmar"
                else
                    "Elige un PIN de 4 dígitos para proteger tu carpeta",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            val currentPin = if (isConfirming) confirmPin else pin
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
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.errorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            NumericKeypad(
                onDigit = { digit ->
                    if (isConfirming) {
                        if (confirmPin.length < pinLength) {
                            confirmPin += digit
                            if (confirmPin.length == pinLength) {
                                if (confirmPin == pin) {
                                    onPinSet(pin)
                                } else {
                                    error = "Los PINs no coinciden"
                                    confirmPin = ""
                                }
                            }
                        }
                    } else {
                        if (pin.length < pinLength) {
                            pin += digit
                            if (pin.length == pinLength) {
                                isConfirming = true
                                error = null
                            }
                        }
                    }
                },
                onDelete = {
                    error = null
                    if (isConfirming) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    } else {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                }
            )
        }
    }
}

// ── Contenido de la carpeta segura ────────────────────
@Composable
private fun SecureFolderContent(
    uiState: SecurityUiState,
    onBack: () -> Unit,
    onDeleteFile: (java.io.File) -> Unit,
    onRestoreFile: (java.io.File) -> Unit,
    onChangePinClick: () -> Unit,
    onToggleBiometric: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onImportLocalFile: (java.io.File) -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportFile(it) }
    }

    // ── Diálogo de selección de origen ───────────────
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "Proteger archivo",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿De dónde quieres traer el archivo?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Opción 1: Desde el dispositivo ─
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImportDialog = false
                                fileLauncher.launch("*/*")
                            },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Desde el dispositivo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Explorar archivos del sistema",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── Opción 2: Desde biblioteca app ─
                    if (uiState.appFiles.isNotEmpty()) {
                        Text(
                            text = "Desde la biblioteca de DocuSmart",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(uiState.appFiles) { _, file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showImportDialog = false
                                            onImportLocalFile(file)
                                        },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${file.length() / 1024} KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No hay archivos en la biblioteca de la app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp, bottom = 100.dp,
            start = 20.dp, end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(4.dp))
                DocuSmartTopBanner(
                    screenTitle = "Carpeta Segura",
                    screenSubtitle = "${uiState.secureFiles.size} archivos protegidos",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Proteger nuevo archivo",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // ── Configuración ─────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChangePinClick() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Cambiar PIN",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    if (uiState.isBiometricAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Desbloqueo biométrico",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = uiState.isBiometricEnabled,
                                onCheckedChange = { onToggleBiometric() }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Archivos protegidos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (uiState.secureFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = PremiumGold,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No hay archivos protegidos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Toca \"Proteger nuevo archivo\" para agregar archivos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(uiState.secureFiles) { _, file ->
                SecureFileItem(
                    file = file,
                    onDelete = { onDeleteFile(file) },
                    onRestore = { onRestoreFile(file) }
                )
            }
        }
    }
}

// ── Item de archivo seguro ────────────────────────────
@Composable
private fun SecureFileItem(
    file: java.io.File,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        PremiumGold.copy(alpha = 0.15f),
                        MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${file.length() / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Restaurar") },
                        leadingIcon = {
                            Icon(Icons.Rounded.DriveFileMove, null)
                        },
                        onClick = {
                            showMenu = false
                            onRestore()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Eliminar",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete, null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}