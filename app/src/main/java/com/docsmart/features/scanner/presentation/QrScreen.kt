package com.docsmart.features.scanner.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.docsmart.R
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.features.scanner.domain.QrCrypto
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

// QrContentType y detectQrContentType viven en QrContentType.kt (mismo paquete).

// ImageProxy.image requiere @ExperimentalGetImage. El checker de lint de AGP 8.7.0 no
// reconoce esa anotación como marcador de opt-in válido (bug conocido: @OptIn no la silencia),
// así que se aísla aquí y se suprime puntualmente en vez de desactivar la regla en todo el proyecto.
@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Suppress("UnsafeOptInUsageError")
private fun ImageProxy.toMediaImageOrNull() = image

// ── Pantalla: Leer QR con cámara ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrReaderScreen(onBack: () -> Unit = {}) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    var qrResult     by remember { mutableStateOf<String?>(null) }
    var qrType       by remember { mutableStateOf(QrContentType.TEXT) }
    var isScanning   by remember { mutableStateOf(true) }
    var copiedMsg    by remember { mutableStateOf(false) }
    var imageBitmap  by remember { mutableStateOf<Bitmap?>(null) }

    // ── QR protegido (HU-SEC-09/10) ───────────────────
    var pendingProtectedContent by remember { mutableStateOf<String?>(null) }
    var qrPassword              by remember { mutableStateOf("") }
    var qrPasswordVisible       by remember { mutableStateOf(false) }
    var qrPasswordError         by remember { mutableStateOf<String?>(null) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner  = remember { BarcodeScanning.getClient() }
    val scope    = rememberCoroutineScope()
    val openDocumentLabel     = stringResource(R.string.qr_open_document)
    val wrongQrPasswordMessage = stringResource(R.string.pdf_pw_wrong_password)

    fun resumeScanning() {
        pendingProtectedContent = null
        qrPassword = ""
        qrPasswordError = null
        isScanning = true
    }

    fun tryUnlockQr() {
        val protectedContent = pendingProtectedContent ?: return
        val decrypted = QrCrypto.decrypt(protectedContent, qrPassword)
        if (decrypted != null) {
            qrResult = decrypted
            qrType   = detectQrContentType(decrypted)
            pendingProtectedContent = null
            qrPassword = ""
            qrPasswordError = null
            if (qrType == QrContentType.IMAGE) {
                scope.launch { imageBitmap = loadBitmapFromUrl(decrypted) }
            }
        } else {
            qrPasswordError = wrongQrPasswordMessage
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.qr_reader_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.qr_reader_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        // ── Diálogo: QR protegido con contraseña ──────────────────────────────
        pendingProtectedContent?.let {
            AlertDialog(
                onDismissRequest = { resumeScanning() },
                shape = MaterialTheme.shapes.large,
                icon  = { Icon(Icons.Rounded.Lock, null) },
                title = { Text(stringResource(R.string.qr_protected_title)) },
                text  = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.qr_protected_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = qrPassword,
                            onValueChange = { qrPassword = it; qrPasswordError = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.qr_password_label)) },
                            visualTransformation = if (qrPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { qrPasswordVisible = !qrPasswordVisible }) {
                                    Icon(
                                        if (qrPasswordVisible) Icons.Rounded.VisibilityOff
                                        else Icons.Rounded.Visibility, null
                                    )
                                }
                            },
                            isError = qrPasswordError != null,
                            supportingText = qrPasswordError?.let { msg ->
                                { Text(msg, color = MaterialTheme.colorScheme.error) }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { tryUnlockQr() }) {
                        Text(stringResource(R.string.qr_unlock))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resumeScanning() }) {
                        Text(stringResource(R.string.general_cancel))
                    }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (qrResult == null) {
                if (!hasCameraPermission) {
                    // ── Sin permiso ───────────────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.qr_camera_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                            shape = MaterialTheme.shapes.medium
                        ) { Text(stringResource(R.string.qr_allow_camera_access)) }
                    }
                } else {
                    // ── Vista de cámara ───────────────────────────────────────
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                        if (!isScanning) { imageProxy.close(); return@setAnalyzer }
                                        val mediaImage = imageProxy.toMediaImageOrNull()
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage, imageProxy.imageInfo.rotationDegrees
                                            )
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    barcodes.firstOrNull()?.rawValue?.let { value ->
                                                        isScanning = false
                                                        if (value.startsWith(QrCrypto.PREFIX)) {
                                                            pendingProtectedContent =
                                                                value.removePrefix(QrCrypto.PREFIX)
                                                        } else {
                                                            qrResult = value
                                                            qrType   = detectQrContentType(value)
                                                            // Si es imagen URL, cargarla
                                                            if (qrType == QrContentType.IMAGE) {
                                                                scope.launch {
                                                                    imageBitmap = loadBitmapFromUrl(value)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else imageProxy.close()
                                    }
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview, imageAnalysis
                                        )
                                    } catch (e: Exception) { Timber.e(e, "Error cámara") }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Marco
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(240.dp).border(
                                2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)
                            ))
                            QrCornerDecoration()
                        }
                        // Guía
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(stringResource(R.string.qr_center_in_frame),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            } else {
                // ── Resultado según tipo ──────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Ícono según tipo
                    val (typeIcon, typeColor, typeLabel) = when (qrType) {
                        QrContentType.URL      -> Triple(Icons.Rounded.Link,         DocuBlue,    stringResource(R.string.qr_type_url_detected))
                        QrContentType.IMAGE    -> Triple(Icons.Rounded.Image,        SuccessGreen,stringResource(R.string.qr_type_image_detected))
                        QrContentType.DOCUMENT -> Triple(Icons.Rounded.Description,  DocuBlue,    stringResource(R.string.qr_type_document_detected))
                        QrContentType.EMAIL    -> Triple(Icons.Rounded.Email,        DocuBlue,    stringResource(R.string.qr_type_email_detected))
                        QrContentType.PHONE    -> Triple(Icons.Rounded.Phone,        SuccessGreen,stringResource(R.string.qr_type_phone_detected))
                        QrContentType.TEXT     -> Triple(Icons.Rounded.TextFields,   DocuBlue,    stringResource(R.string.qr_type_text_detected))
                    }

                    Box(
                        modifier = Modifier.size(80.dp).background(
                            typeColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(44.dp))
                    }

                    Text(stringResource(R.string.qr_detected_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)

                    // ── Imagen inline si es tipo imagen ───────────────────────
                    if (qrType == QrContentType.IMAGE) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (imageBitmap != null) {
                                Image(
                                    bitmap = imageBitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.qr_image_content_desc),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.qr_loading_image),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ── Card con contenido y acciones ─────────────────────────
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(typeIcon, null,
                                    tint = typeColor, modifier = Modifier.size(20.dp))
                                Text(typeLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }

                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = qrResult ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp),
                                    maxLines = 3
                                )
                            }

                            if (copiedMsg) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, null,
                                        tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Text(stringResource(R.string.qr_copied_clipboard),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuccessGreen)
                                }
                            }

                            // ── Botones según tipo ────────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (qrType) {
                                    QrContentType.URL -> {
                                        Button(
                                            onClick = { openUrl(context, qrResult ?: "") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.OpenInBrowser, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_open_browser))
                                        }
                                        OutlinedButton(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_url))
                                        }
                                    }
                                    QrContentType.IMAGE -> {
                                        Button(
                                            onClick = { openUrl(context, qrResult ?: "") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.OpenInBrowser, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_open_image))
                                        }
                                        OutlinedButton(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_link))
                                        }
                                    }
                                    QrContentType.DOCUMENT -> {
                                        Button(
                                            onClick = { openDocumentExternally(context, qrResult ?: "", openDocumentLabel) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_open_document))
                                        }
                                        OutlinedButton(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_path))
                                        }
                                    }
                                    QrContentType.EMAIL -> {
                                        Button(
                                            onClick = { openUrl(context, qrResult ?: "") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.Email, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_send_email))
                                        }
                                        OutlinedButton(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_email))
                                        }
                                    }
                                    QrContentType.PHONE -> {
                                        Button(
                                            onClick = { openUrl(context, qrResult ?: "") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.Phone, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_call))
                                        }
                                        OutlinedButton(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_number))
                                        }
                                    }
                                    QrContentType.TEXT -> {
                                        Button(
                                            onClick = { copyToClipboard(context, qrResult ?: ""); copiedMsg = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.qr_copy_text))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { qrResult = null; isScanning = true; copiedMsg = false; imageBitmap = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_scan_another))
                    }
                }
            }
        }
    }
}

// ── Esquinas decorativas ──────────────────────────────────────────────────────
@Composable
private fun QrCornerDecoration() {
    val color      = DocuBlue
    val frameSize  = 240.dp
    val cornerSize = 28.dp
    val stroke     = 4.dp

    Box(modifier = Modifier.size(frameSize)) {
        Box(modifier = Modifier.width(cornerSize).height(stroke).align(Alignment.TopStart).background(color, RoundedCornerShape(topStart = 4.dp)))
        Box(modifier = Modifier.width(stroke).height(cornerSize).align(Alignment.TopStart).background(color, RoundedCornerShape(topStart = 4.dp)))
        Box(modifier = Modifier.width(cornerSize).height(stroke).align(Alignment.TopEnd).background(color, RoundedCornerShape(topEnd = 4.dp)))
        Box(modifier = Modifier.width(stroke).height(cornerSize).align(Alignment.TopEnd).background(color, RoundedCornerShape(topEnd = 4.dp)))
        Box(modifier = Modifier.width(cornerSize).height(stroke).align(Alignment.BottomStart).background(color, RoundedCornerShape(bottomStart = 4.dp)))
        Box(modifier = Modifier.width(stroke).height(cornerSize).align(Alignment.BottomStart).background(color, RoundedCornerShape(bottomStart = 4.dp)))
        Box(modifier = Modifier.width(cornerSize).height(stroke).align(Alignment.BottomEnd).background(color, RoundedCornerShape(bottomEnd = 4.dp)))
        Box(modifier = Modifier.width(stroke).height(cornerSize).align(Alignment.BottomEnd).background(color, RoundedCornerShape(bottomEnd = 4.dp)))
    }
}

// ── Pantalla: Crear QR ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCreatorScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // 0=URL, 1=Texto, 2=Email, 3=Teléfono, 4=Imagen, 5=Documento
    var selectedType by remember { mutableIntStateOf(0) }
    var content      by remember { mutableStateOf("") }
    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var usePassword  by remember { mutableStateOf(false) }
    var qrBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var savedMsg     by remember { mutableStateOf<String?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    val types = listOf(
        stringResource(R.string.qr_chip_url),
        stringResource(R.string.qr_chip_text),
        stringResource(R.string.qr_chip_email),
        stringResource(R.string.qr_chip_phone),
        stringResource(R.string.qr_chip_image),
        stringResource(R.string.qr_chip_document)
    )
    val typeIcons = listOf(
        Icons.Rounded.Link,
        Icons.Rounded.TextFields,
        Icons.Rounded.Email,
        Icons.Rounded.Phone,
        Icons.Rounded.Image,
        Icons.Rounded.Description
    )

    val defaultImageName    = stringResource(R.string.qr_chip_image)
    val defaultDocumentName = stringResource(R.string.pdf_pw_default_document_name)

    // Launchers para seleccionar imagen o documento
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri  = it
            selectedName = it.lastPathSegment?.substringAfterLast("/") ?: defaultImageName
            content      = it.toString()
            qrBitmap     = null
            savedMsg     = null
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri  = it
            selectedName = it.lastPathSegment?.substringAfterLast("/") ?: defaultDocumentName
            content      = it.toString()
            qrBitmap     = null
            savedMsg     = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.qr_creator_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.qr_creator_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Selector de tipo en 2 filas ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.take(3).forEachIndexed { index, label ->
                        FilterChip(
                            selected  = selectedType == index,
                            onClick   = {
                                selectedType = index
                                content      = ""
                                selectedUri  = null
                                selectedName = ""
                                qrBitmap     = null
                                savedMsg     = null
                                errorMsg     = null
                            },
                            label     = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Icon(typeIcons[index], null, modifier = Modifier.size(14.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.drop(3).forEachIndexed { i, label ->
                        val index = i + 3
                        FilterChip(
                            selected  = selectedType == index,
                            onClick   = {
                                selectedType = index
                                content      = ""
                                selectedUri  = null
                                selectedName = ""
                                qrBitmap     = null
                                savedMsg     = null
                                errorMsg     = null
                            },
                            label     = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Icon(typeIcons[index], null, modifier = Modifier.size(14.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Entrada según tipo ────────────────────────────────────────────
            when (selectedType) {
                4 -> {
                    // Imagen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick  = { imageLauncher.launch("image/*") },
                        shape    = MaterialTheme.shapes.large,
                        colors   = CardDefaults.cardColors(
                            containerColor = if (selectedUri != null)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedUri != null) Icons.Rounded.CheckCircle
                                else Icons.Rounded.AddPhotoAlternate,
                                null,
                                tint = if (selectedUri != null) SuccessGreen
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    if (selectedUri != null) selectedName
                                    else stringResource(R.string.qr_select_image),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedUri != null)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (selectedUri != null) stringResource(R.string.qr_image_selected)
                                    else "JPG, PNG, WebP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                5 -> {
                    // Documento
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick  = { documentLauncher.launch("*/*") },
                        shape    = MaterialTheme.shapes.large,
                        colors   = CardDefaults.cardColors(
                            containerColor = if (selectedUri != null)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedUri != null) Icons.Rounded.CheckCircle
                                else Icons.Rounded.FileOpen,
                                null,
                                tint = if (selectedUri != null) SuccessGreen
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    if (selectedUri != null) selectedName
                                    else stringResource(R.string.qr_select_document),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedUri != null)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (selectedUri != null) stringResource(R.string.qr_document_selected)
                                    else "PDF, Word, Excel, PPT, TXT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    // URL, Texto, Email, Teléfono
                    OutlinedTextField(
                        value         = content,
                        onValueChange = { content = it; qrBitmap = null; savedMsg = null },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = {
                            Text(when (selectedType) {
                                0    -> stringResource(R.string.qr_label_url)
                                1    -> stringResource(R.string.qr_label_text)
                                2    -> stringResource(R.string.qr_label_email)
                                else -> stringResource(R.string.qr_label_phone)
                            })
                        },
                        placeholder   = {
                            Text(
                                when (selectedType) {
                                    0    -> "https://ejemplo.com"
                                    1    -> stringResource(R.string.qr_placeholder_text)
                                    2    -> "correo@ejemplo.com"
                                    else -> "+57 300 000 0000"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon   = {
                            Icon(typeIcons[selectedType], null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = when (selectedType) {
                                0    -> KeyboardType.Uri
                                2    -> KeyboardType.Email
                                3    -> KeyboardType.Phone
                                else -> KeyboardType.Text
                            }
                        ),
                        minLines = if (selectedType == 1) 3 else 1,
                        maxLines = if (selectedType == 1) 5 else 1,
                        shape    = MaterialTheme.shapes.large
                    )
                }
            }

            // ── Contraseña ────────────────────────────────────────────────────
            Card(
                shape  = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (usePassword)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(if (usePassword) 0.dp else 2.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Lock, null,
                                tint = if (usePassword) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                            Column {
                                Text(stringResource(R.string.qr_protect_password),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.qr_protect_password_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked         = usePassword,
                            onCheckedChange = { usePassword = it; if (!it) password = "" }
                        )
                    }
                    if (usePassword) {
                        OutlinedTextField(
                            value         = password,
                            onValueChange = { password = it },
                            modifier      = Modifier.fillMaxWidth(),
                            label         = { Text(stringResource(R.string.qr_password_label)) },
                            placeholder   = { Text(stringResource(R.string.qr_password_min_chars)) },
                            leadingIcon   = {
                                Icon(Icons.Rounded.Key, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Rounded.VisibilityOff
                                        else Icons.Rounded.Visibility,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            singleLine = true,
                            shape      = MaterialTheme.shapes.medium
                        )
                    }
                }
            }

            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }

            // ── Botón generar ─────────────────────────────────────────────────
            val hasContent = when (selectedType) {
                4, 5 -> selectedUri != null
                else -> content.isNotBlank()
            }

            val errorSelectImage    = stringResource(R.string.qr_error_select_image)
            val errorSelectDocument = stringResource(R.string.qr_error_select_document)
            val errorEmptyContent   = stringResource(R.string.qr_error_empty_content)
            val errorPasswordShort  = stringResource(R.string.qr_error_password_short)
            val savedDownloadsMsg   = stringResource(R.string.general_saved_downloads)
            val shareQrChooserTitle = stringResource(R.string.qr_share_chooser_title)

            Button(
                onClick = {
                    if (!hasContent) {
                        errorMsg = when (selectedType) {
                            4    -> errorSelectImage
                            5    -> errorSelectDocument
                            else -> errorEmptyContent
                        }
                        return@Button
                    }
                    if (usePassword && password.length < 4) {
                        errorMsg = errorPasswordShort
                        return@Button
                    }
                    errorMsg     = null
                    savedMsg     = null
                    isGenerating = true
                    scope.launch {
                        val rawContent = when (selectedType) {
                            4, 5 -> selectedUri.toString()
                            0    -> if (!content.startsWith("http")) "https://$content" else content
                            2    -> "mailto:$content"
                            3    -> "tel:$content"
                            else -> content
                        }
                        val finalContent = if (usePassword && password.isNotBlank())
                            "${QrCrypto.PREFIX}${QrCrypto.encrypt(rawContent, password)}"
                        else rawContent
                        qrBitmap     = generateQrBitmap(finalContent)
                        isGenerating = false
                    }
                },
                enabled  = hasContent,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_generate), style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── QR generado ───────────────────────────────────────────────────
            qrBitmap?.let { bitmap ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(4.dp),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.qr_your_code),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)

                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_generated_content_desc),
                                modifier = Modifier.fillMaxSize())
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(types[selectedType],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            if (usePassword) {
                                Surface(shape = MaterialTheme.shapes.small,
                                    color = SuccessGreen.copy(alpha = 0.15f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Lock, null,
                                            tint = SuccessGreen, modifier = Modifier.size(12.dp))
                                        Text(stringResource(R.string.qr_protected_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen)
                                    }
                                }
                            }
                        }

                        savedMsg?.let { msg ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null,
                                    tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Text(msg, style = MaterialTheme.typography.labelMedium,
                                    color = SuccessGreen)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val file = saveQrToFile(context, bitmap)
                                        if (file != null) {
                                            saveQrToDownloads(context, file)
                                            savedMsg = savedDownloadsMsg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.general_save))
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        val file = saveQrToFile(context, bitmap)
                                        if (file != null) shareQrImage(context, file, shareQrChooserTitle)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.general_share))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private suspend fun loadBitmapFromUrl(url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout    = 5000
            BitmapFactory.decodeStream(connection.getInputStream())
        } catch (e: Exception) {
            Timber.e(e, "loadBitmapFromUrl: error")
            null
        }
    }

private fun openDocumentExternally(context: Context, uriString: String, chooserTitle: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        Timber.e(e, "openDocumentExternally: error")
    }
}

private suspend fun generateQrBitmap(content: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val size      = 512
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap    = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "generateQrBitmap: error")
            null
        }
    }


private suspend fun saveQrToFile(context: Context, bitmap: Bitmap): File? =
    withContext(Dispatchers.IO) {
        try {
            val dir  = File(context.cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "QR_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (e: Exception) { Timber.e(e, "saveQrToFile"); null }
    }

private fun saveQrToDownloads(context: Context, file: File) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(file).use { it.copyTo(out) }
            }
            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (e: Exception) { Timber.e(e, "saveQrToDownloads") }
}

private fun shareQrImage(context: Context, file: File, chooserTitle: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) { Timber.e(e, "shareQrImage") }
}

private fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cb.setPrimaryClip(android.content.ClipData.newPlainText("QR", text))
}

private fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (e: Exception) { Timber.e(e, "openUrl") }
}
