package com.docsmart.features.scanner.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentFilterChipColors
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.features.scanner.domain.QrContactContent
import com.docsmart.features.scanner.domain.QrCrypto
import com.docsmart.features.scanner.domain.QrEventContent
import com.docsmart.features.scanner.domain.QrHistoryEntry
import com.docsmart.features.scanner.domain.QrHistorySource
import com.docsmart.features.scanner.domain.QrHistoryStorage
import com.docsmart.features.scanner.domain.QrWifiContent
import com.docsmart.features.scanner.domain.QrWifiSecurity
import com.docsmart.features.scanner.domain.hasSufficientContrast
import com.docsmart.features.scanner.domain.toQrPayload
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
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
fun QrReaderScreen(
    onBack: () -> Unit = {},
    // HU-44: acceso al Historial de QR desde el banner.
    onHistoryClick: () -> Unit = {},
    viewModel: QrViewModel = hiltViewModel()
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()

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

    // Bug real encontrado 2026-09-14 (repaso general): ni el executor de
    // CameraX ni el detector de ML Kit se cerraban nunca -- CameraX
    // desvincula la cámara al salir de la pantalla (bindToLifecycle) pero
    // no es dueño de este executor creado a mano, y BarcodeScanner
    // mantiene recursos nativos abiertos hasta close(). Cada visita al
    // lector de QR (entrar y salir es una acción trivial de repetir)
    // dejaba un hilo vivo permanentemente filtrado.
    DisposableEffect(Unit) {
        onDispose {
            // Bug real encontrado 2026-09-14 (repaso general): shutdown() solo
            // deja de aceptar tareas nuevas, no espera a que termine la que ya
            // está en curso en el hilo del executor -- sin awaitTermination,
            // scanner.close() podía ejecutarse mientras un análisis en vuelo
            // todavía llamaba a scanner.process(image), una carrera con el
            // recurso nativo que se está cerrando.
            executor.shutdown()
            try {
                executor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            scanner.close()
        }
    }
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
        // Se excluye el inset inferior de systemBars (bug real "línea
        // blanca": este Scaffold lo reservaba por duplicado sobre el que ya
        // reserva MainActivity para DocuSmartBottomBar -- ver StudyScreen.kt
        // para el detalle completo).
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        containerColor = Color.Transparent // fondo animado global (backlog UX 2026-09-06)
    ) { innerPadding ->
        // ── Diálogo: QR protegido con contraseña ──────────────────────────────
        pendingProtectedContent?.let {
            AlertDialog(
                onDismissRequest = { resumeScanning() },
                shape = MaterialTheme.shapes.large,
                icon  = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            // Banner azul con degradado de acento (2026-09-08, pedido
            // explícito del usuario) -- reemplaza el TopAppBar plano de
            // antes, mismo componente que ya usan Estudio/Seguridad/Ajustes.
            DocuSmartTopBanner(
                screenTitle    = stringResource(R.string.qr_reader_title),
                screenSubtitle = stringResource(R.string.qr_reader_subtitle),
                onBack         = onBack,
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = stringResource(R.string.qr_history_title),
                            tint = Color.White
                        )
                    }
                },
                modifier       = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            modifier = Modifier.accentBorder(MaterialTheme.shapes.medium),
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
                                                        // HU-44: se guarda el valor CRUDO leído (con el
                                                        // prefijo `PROTECTED:` intacto si estaba cifrado)
                                                        // -- RNF2, nunca el texto plano de un QR protegido.
                                                        val isProtectedScan = value.startsWith(QrCrypto.PREFIX)
                                                        // Bug real encontrado en la revisión pre-fusión:
                                                        // addOnSuccessListener sin Executor propio corre en
                                                        // el hilo principal (no en el `executor` de
                                                        // CameraX) -- el I/O de SharedPreferences de
                                                        // QrHistoryStorage.save() se saca de ahí con
                                                        // Dispatchers.IO para no sumarle jitter al
                                                        // callback de detección de cada frame escaneado.
                                                        scope.launch(Dispatchers.IO) {
                                                            QrHistoryStorage.save(
                                                                context,
                                                                QrHistoryEntry(
                                                                    id = java.util.UUID.randomUUID().toString(),
                                                                    content = value,
                                                                    typeName = if (isProtectedScan) {
                                                                        "PROTECTED"
                                                                    } else {
                                                                        detectQrContentType(value).name
                                                                    },
                                                                    source = QrHistorySource.SCANNED,
                                                                    createdAtMillis = System.currentTimeMillis()
                                                                )
                                                            )
                                                        }
                                                        if (isProtectedScan) {
                                                            pendingProtectedContent =
                                                                value.removePrefix(QrCrypto.PREFIX)
                                                        } else {
                                                            qrResult = value
                                                            qrType   = detectQrContentType(value)
                                                            DocuSmartAnalytics.logQrScanned(qrType.name)
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
                    // ── AdMob — solo para usuarios free (backlog UX §8),
                    // solo en el resultado detectado, nunca sobre la vista
                    // de cámara en vivo (taparía el área de escaneo) ──────
                    if (!isPremium) {
                        DocuSmartBannerAd(
                            adUnitId  = AdConstants.BANNER_QR_ID,
                            adManager = viewModel.adManager
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Ícono según tipo -- bug real corregido 2026-09-08: URL/
                    // Documento/Email/Texto usaban `DocuBlue`, un azul fijo que
                    // ignoraba el Color de acento elegido en Ajustes (mismo
                    // patrón de bug ya corregido antes en banners y sombras --
                    // ver `AccentGradient.kt`). Imagen/Teléfono se quedan en
                    // verde a propósito, como diferenciación semántica.
                    // Extraído a QrResultDisplay.kt (HU-44): el Historial
                    // reutiliza el mismo mapeo tipo->ícono/color/etiqueta.
                    val (typeIcon, typeColor, typeLabel) = qrContentTypeVisuals(qrType)

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
                        val imagePreviewShape = MaterialTheme.shapes.large
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .accentShadow(imagePreviewShape)
                                .clip(imagePreviewShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .accentBorder(imagePreviewShape)
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
                    val resultCardShape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .accentShadow(resultCardShape)
                            .clip(resultCardShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .accentBorder(resultCardShape)
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
                            // Extraído a QrResultDisplay.kt (HU-44): el
                            // Historial reutiliza los mismos botones para una
                            // entrada leída.
                            QrContentActionButtons(
                                qrType = qrType,
                                content = qrResult ?: "",
                                onCopied = { copiedMsg = true }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { qrResult = null; isScanning = true; copiedMsg = false; imageBitmap = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                            .accentBorder(MaterialTheme.shapes.medium),
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
}

// ── Esquinas decorativas ──────────────────────────────────────────────────────
@Composable
private fun QrCornerDecoration() {
    val color      = MaterialTheme.colorScheme.primary
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

// Chips del selector de tipo (URL/Texto/Email/Tel/Imagen/Doc) -- pedido
// explícito del usuario 2026-09-08: fondo tintado y borde con el Color de
// acento siempre visibles, no solo el contorno neutro por defecto de
// Material3, más marcado cuando el chip está seleccionado.
@Composable
internal fun qrTypeChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor           = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    labelColor               = MaterialTheme.colorScheme.onSurface,
    iconColor                = MaterialTheme.colorScheme.primary,
    selectedContainerColor   = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor       = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
internal fun qrTypeChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled             = true,
    selected            = selected,
    borderColor         = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth         = 1.dp,
    selectedBorderWidth = 1.5.dp
)

// ── Pantalla: Crear QR ────────────────────────────────────────────────────────
// HU-43: ExperimentalLayoutApi por el FlowRow del selector de tipo.
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QrCreatorScreen(
    onBack: () -> Unit = {},
    // Atajo "Crear QR" desde el menú "⋮" de un archivo ya elegido (backlog
    // UX 2026-08-30, HU-UX-01) -- `initialFileType` es "image" o "document",
    // decide qué chip preseleccionar ya que ambos comparten el mismo
    // mecanismo de adjuntar un archivo.
    initialFileUri : String? = null,
    initialFileType: String? = null,
    initialFileName: String? = null,
    // HU-44: acceso al Historial de QR desde el banner.
    onHistoryClick: () -> Unit = {},
    viewModel: QrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()

    // 0=URL, 1=Texto, 2=Email, 3=Teléfono, 4=Imagen, 5=Documento
    var selectedType by remember { mutableIntStateOf(0) }
    var content      by remember { mutableStateOf("") }
    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }

    LaunchedEffect(initialFileUri) {
        if (initialFileUri != null) {
            selectedType = if (initialFileType == "image") 4 else 5
            selectedUri  = Uri.parse(initialFileUri)
            selectedName = initialFileName ?: ""
            content      = initialFileUri
        }
    }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var usePassword  by remember { mutableStateOf(false) }
    var qrBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var savedMsg     by remember { mutableStateOf<String?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    // HU-43 (backlog UX 2026-08-30/09-14): estado de los 3 tipos nuevos --
    // cada uno con sus propios campos (no comparten `content` como URL/
    // Texto/Email/Teléfono), así que cambiar de tipo y volver conserva lo
    // ya escrito sin necesitar lógica extra de reset.
    var wifiSsid       by remember { mutableStateOf("") }
    var wifiPassword   by remember { mutableStateOf("") }
    var wifiShowPass   by remember { mutableStateOf(false) }
    var wifiSecurity   by remember { mutableStateOf(QrWifiSecurity.WPA) }
    var contactName    by remember { mutableStateOf("") }
    var contactPhone   by remember { mutableStateOf("") }
    var contactEmail   by remember { mutableStateOf("") }
    var eventTitle     by remember { mutableStateOf("") }
    var eventLocation  by remember { mutableStateOf("") }
    var eventStart     by remember {
        mutableStateOf(LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0))
    }
    var eventEnd       by remember { mutableStateOf(eventStart.plusHours(1)) }

    // HU-45 (backlog UX 2026-08-30/09-14): color de los módulos (RF1,
    // Negro por defecto -- AC3 de HU-43/mismo criterio de "sin cambios
    // para quien no toca la opción") y logo opcional (RF2).
    var moduleColor by remember { mutableStateOf(QR_DEFAULT_MODULE_COLOR) }
    var logoBitmap  by remember { mutableStateOf<Bitmap?>(null) }

    val types = listOf(
        stringResource(R.string.qr_chip_url),
        stringResource(R.string.qr_chip_text),
        stringResource(R.string.qr_chip_email),
        stringResource(R.string.qr_chip_phone),
        stringResource(R.string.qr_chip_image),
        stringResource(R.string.qr_chip_document),
        stringResource(R.string.qr_chip_wifi),
        stringResource(R.string.qr_chip_contact),
        stringResource(R.string.qr_chip_event)
    )
    val typeIcons = listOf(
        Icons.Rounded.Link,
        Icons.Rounded.TextFields,
        Icons.Rounded.Email,
        Icons.Rounded.Phone,
        Icons.Rounded.Image,
        Icons.Rounded.Description,
        Icons.Rounded.Wifi,
        Icons.Rounded.ContactPage,
        Icons.Rounded.Event
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

    // HU-45 (RF2): selector de logo -- mismo patrón GetContent()/"image/*"
    // que ya usa el chip "Imagen" de arriba, pero acá se decodifica el
    // bitmap real de inmediato (para la vista previa y para pasarlo tal
    // cual a generateQrBitmap) en vez de guardar la Uri como contenido del
    // QR.
    val errorLogoLoad = stringResource(R.string.qr_error_logo_load)
    val logoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching { decodeSampledBitmap(context, uri, LOGO_TARGET_SIZE) }.getOrNull()
                }
                if (decoded != null) {
                    logoBitmap = decoded
                    qrBitmap = null
                    savedMsg = null
                    errorMsg = null
                } else {
                    // Bug real encontrado en la revisión pre-fusión: un
                    // fallo de decodificación (imagen corrupta/formato no
                    // soportado) dejaba `logoBitmap` en null sin ningún
                    // aviso -- la opción de logo simplemente desaparecía
                    // sin explicación.
                    errorMsg = errorLogoLoad
                }
            }
        }
    }

    Scaffold(
        // Se excluye el inset inferior de systemBars (bug real "línea
        // blanca": este Scaffold lo reservaba por duplicado sobre el que ya
        // reserva MainActivity para DocuSmartBottomBar -- ver StudyScreen.kt
        // para el detalle completo).
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        containerColor = Color.Transparent // fondo animado global (backlog UX 2026-09-06)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner azul con degradado de acento (2026-09-08, pedido
            // explícito del usuario) -- reemplaza el TopAppBar plano de
            // antes, mismo componente que ya usan Estudio/Seguridad/Ajustes.
            DocuSmartTopBanner(
                screenTitle    = stringResource(R.string.qr_creator_title),
                screenSubtitle = stringResource(R.string.qr_creator_subtitle),
                onBack         = onBack,
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = stringResource(R.string.qr_history_title),
                            tint = Color.White
                        )
                    }
                }
            )

            // ── AdMob — solo para usuarios free (backlog UX §8) ───────────────
            if (!isPremium) {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_QR_ID,
                    adManager = viewModel.adManager
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Selector de tipo ──────────────────────────────────────────────
            // Pedido explícito del usuario 2026-09-08: los chips sin
            // seleccionar se veían sueltos, sin borde ni fondo propios (solo
            // el contorno gris neutro por defecto de Material3). Ahora
            // siempre llevan un fondo tintado y un borde con el Color de
            // acento, más marcado cuando están seleccionados.
            // HU-43 (2026-09-14): antes eran 2 filas fijas de 3 chips
            // (`take(3)`/`drop(3)`), hardcodeadas para exactamente 6 tipos.
            // Al sumar Wi-Fi/Contacto/Evento (9 en total) se reemplaza por un
            // `FlowRow` que ajusta solo el número de filas necesarias --
            // mismo bug de overflow sin scroll que HU-41 encontró en los
            // chips de modo de color del Escáner, evitado acá desde el
            // diseño en vez de parchearlo después.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.indices.forEach { index ->
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
                                Text(types[index], style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        colors    = qrTypeChipColors(),
                        border    = qrTypeChipBorder(selectedType == index)
                    )
                }
            }

            // ── Entrada según tipo ────────────────────────────────────────────
            when (selectedType) {
                4 -> {
                    // Imagen
                    val imagePickerShape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .accentShadow(imagePickerShape)
                            .clip(imagePickerShape)
                            .background(
                                if (selectedUri != null)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .accentBorder(imagePickerShape)
                            .clickable { imageLauncher.launch("image/*") }
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
                    val documentPickerShape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .accentShadow(documentPickerShape)
                            .clip(documentPickerShape)
                            .background(
                                if (selectedUri != null)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .accentBorder(documentPickerShape)
                            .clickable { documentLauncher.launch("*/*") }
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
                6 -> {
                    // HU-43: Wi-Fi
                    QrWifiForm(
                        ssid = wifiSsid,
                        onSsidChange = { wifiSsid = it; qrBitmap = null; savedMsg = null },
                        password = wifiPassword,
                        onPasswordChange = { wifiPassword = it; qrBitmap = null; savedMsg = null },
                        showPassword = wifiShowPass,
                        onShowPasswordToggle = { wifiShowPass = !wifiShowPass },
                        security = wifiSecurity,
                        onSecurityChange = { wifiSecurity = it; qrBitmap = null; savedMsg = null }
                    )
                }
                7 -> {
                    // HU-43: Contacto
                    QrContactForm(
                        name = contactName,
                        onNameChange = { contactName = it; qrBitmap = null; savedMsg = null },
                        phone = contactPhone,
                        onPhoneChange = { contactPhone = it; qrBitmap = null; savedMsg = null },
                        email = contactEmail,
                        onEmailChange = { contactEmail = it; qrBitmap = null; savedMsg = null }
                    )
                }
                8 -> {
                    // HU-43: Evento de calendario
                    QrEventForm(
                        title = eventTitle,
                        onTitleChange = { eventTitle = it; qrBitmap = null; savedMsg = null },
                        location = eventLocation,
                        onLocationChange = { eventLocation = it; qrBitmap = null; savedMsg = null },
                        start = eventStart,
                        onStartChange = { eventStart = it; qrBitmap = null; savedMsg = null },
                        end = eventEnd,
                        onEndChange = { eventEnd = it; qrBitmap = null; savedMsg = null }
                    )
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
                                // Bug real encontrado 2026-09-14 (repaso
                                // general): estos placeholders estaban
                                // hardcodeados en español/formato colombiano,
                                // sin pasar por el sistema de 12 idiomas
                                // que ya usa el resto de la pantalla.
                                when (selectedType) {
                                    0    -> stringResource(R.string.qr_placeholder_url)
                                    1    -> stringResource(R.string.qr_placeholder_text)
                                    2    -> stringResource(R.string.qr_placeholder_email)
                                    else -> stringResource(R.string.qr_placeholder_phone)
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
                        shape    = MaterialTheme.shapes.large,
                        // Pedido explícito del usuario 2026-09-08: borde con
                        // el Color de acento siempre visible (antes solo se
                        // notaba al enfocar el campo, el contorno normal era
                        // el gris neutro por defecto de Material3).
                        colors   = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            focusedBorderColor   = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // ── Diseño (HU-45: color de módulos + logo) ────────────────────────
            QrDesignSection(
                selectedColor = moduleColor,
                onColorSelected = { moduleColor = it; qrBitmap = null; savedMsg = null },
                hasSufficientContrast = hasSufficientContrast(moduleColor, android.graphics.Color.WHITE),
                logoBitmap = logoBitmap,
                onPickLogo = { logoLauncher.launch("image/*") },
                onRemoveLogo = { logoBitmap = null; qrBitmap = null; savedMsg = null }
            )

            // ── Contraseña ────────────────────────────────────────────────────
            val passwordCardShape = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .accentShadow(passwordCardShape)
                    .clip(passwordCardShape)
                    .background(
                        if (usePassword)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .accentBorder(passwordCardShape)
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
                            // Pedido explícito del usuario 2026-09-08: ícono
                            // y título siempre con el Color de acento (antes
                            // el ícono se apagaba a gris con la contraseña
                            // desactivada).
                            Icon(Icons.Rounded.Lock, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Column {
                                Text(stringResource(R.string.qr_protect_password),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.qr_protect_password_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked         = usePassword,
                            onCheckedChange = { usePassword = it; if (!it) password = "" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                checkedBorderColor = MaterialTheme.colorScheme.primary
                            )
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            // HU-43: Wi-Fi exige SSID (y contraseña salvo red abierta);
            // Contacto exige al menos el nombre; Evento exige título y que
            // el fin no sea anterior al inicio.
            val hasContent = when (selectedType) {
                4, 5 -> selectedUri != null
                6    -> wifiSsid.isNotBlank() && (wifiSecurity == QrWifiSecurity.NONE || wifiPassword.isNotBlank())
                7    -> contactName.isNotBlank()
                8    -> eventTitle.isNotBlank() && !eventEnd.isBefore(eventStart)
                else -> content.isNotBlank()
            }

            val errorSelectImage      = stringResource(R.string.qr_error_select_image)
            val errorSelectDocument   = stringResource(R.string.qr_error_select_document)
            val errorEmptyContent     = stringResource(R.string.qr_error_empty_content)
            val errorPasswordShort    = stringResource(R.string.qr_error_password_short)
            val errorLowContrast      = stringResource(R.string.qr_error_low_contrast)
            val errorWifiIncomplete   = stringResource(R.string.qr_error_wifi_incomplete)
            val errorContactRequired  = stringResource(R.string.qr_error_contact_name_required)
            val errorEventTitle       = stringResource(R.string.qr_error_event_title_required)
            val errorEventEndBefore   = stringResource(R.string.qr_error_event_end_before_start)
            val savedDownloadsMsg     = stringResource(R.string.general_saved_downloads)
            val shareQrChooserTitle   = stringResource(R.string.qr_share_chooser_title)

            Button(
                onClick = {
                    if (!hasContent) {
                        errorMsg = when (selectedType) {
                            4    -> errorSelectImage
                            5    -> errorSelectDocument
                            6    -> errorWifiIncomplete
                            7    -> errorContactRequired
                            8    -> if (eventTitle.isBlank()) errorEventTitle else errorEventEndBefore
                            else -> errorEmptyContent
                        }
                        return@Button
                    }
                    if (usePassword && password.length < 4) {
                        errorMsg = errorPasswordShort
                        return@Button
                    }
                    // HU-45/AC1: en vez de generar un QR probablemente
                    // ilegible, se bloquea la generación y se pide elegir
                    // otro color -- mismo criterio que las validaciones de
                    // arriba (contenido vacío, contraseña corta).
                    if (!hasSufficientContrast(moduleColor, android.graphics.Color.WHITE)) {
                        errorMsg = errorLowContrast
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
                            6    -> QrWifiContent(wifiSsid, wifiPassword, wifiSecurity).toQrPayload()
                            7    -> QrContactContent(contactName, contactPhone, contactEmail).toQrPayload()
                            8    -> QrEventContent(eventTitle, eventLocation, eventStart, eventEnd).toQrPayload()
                            else -> content
                        }
                        val finalContent = if (usePassword && password.isNotBlank())
                            "${QrCrypto.PREFIX}${QrCrypto.encrypt(rawContent, password)}"
                        else rawContent
                        qrBitmap     = generateQrBitmap(finalContent, moduleColor = moduleColor, logo = logoBitmap)
                        isGenerating = false
                        // HU-43: Wi-Fi/Contacto/Evento no están en el
                        // QrContentType del Lector (namespace distinto, ver
                        // QrContentType.kt) -- alcanza con un literal para
                        // la analítica, que solo necesita el nombre.
                        val createdContentTypeName = when (selectedType) {
                            0    -> QrContentType.URL.name
                            2    -> QrContentType.EMAIL.name
                            3    -> QrContentType.PHONE.name
                            4    -> QrContentType.IMAGE.name
                            5    -> QrContentType.DOCUMENT.name
                            6    -> "WIFI"
                            7    -> "CONTACT"
                            8    -> "EVENT"
                            else -> QrContentType.TEXT.name
                        }
                        DocuSmartAnalytics.logQrCreated(createdContentTypeName, usePassword)
                        // HU-44: se guarda `finalContent` -- ya incluye el
                        // prefijo `PROTECTED:` + cifrado cuando usePassword
                        // está activo, nunca el texto plano -- RNF2. Bug real
                        // encontrado en la revisión pre-fusión: guardar acá
                        // `createdContentTypeName` (el tipo real, ej. "URL")
                        // hacía que la fila del Historial mostrara el
                        // ciphertext crudo sin enmascarar y sin ícono de
                        // candado -- mismo criterio que ya usa el Lector
                        // (`typeName = "PROTECTED"` cuando el QR está
                        // cifrado), para que "Contenido protegido con
                        // contraseña" se muestre en vez del contenido.
                        QrHistoryStorage.save(
                            context,
                            QrHistoryEntry(
                                id = java.util.UUID.randomUUID().toString(),
                                content = finalContent,
                                typeName = if (usePassword && password.isNotBlank()) {
                                    "PROTECTED"
                                } else {
                                    createdContentTypeName
                                },
                                source = QrHistorySource.CREATED,
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                    }
                },
                enabled  = hasContent,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .accentBorder(MaterialTheme.shapes.medium),
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
                val qrResultCardShape = MaterialTheme.shapes.large
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .accentShadow(qrResultCardShape)
                        .clip(qrResultCardShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .accentBorder(qrResultCardShape)
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
                                            DownloadsSaver.saveFile(context, file, "image/png")
                                            savedMsg = savedDownloadsMsg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                                    .accentBorder(MaterialTheme.shapes.medium),
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
                                modifier = Modifier.weight(1f)
                                    .accentBorder(MaterialTheme.shapes.medium),
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
            // Bug real encontrado 2026-09-14 (repaso general): el
            // InputStream de la conexión HTTP nunca se cerraba -- cada QR de
            // tipo Imagen escaneado dejaba un socket/stream filtrado.
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Timber.e(e, "loadBitmapFromUrl: error")
            null
        }
    }

internal fun openDocumentExternally(context: Context, uriString: String, chooserTitle: String) {
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

// HU-45 (backlog UX 2026-08-30/09-14): `moduleColor` personaliza el color
// de los módulos (RF1, ya validado contra el fondo blanco antes de llegar
// acá -- ver `hasSufficientContrast` y el chequeo en el botón "Generar").
// `logo` superpone una imagen al centro (RF2); cuando hay logo se sube el
// nivel de corrección de errores a H (~30% de tolerancia a daño/oclusión,
// bastante más que el ~5% que tapa un logo de este tamaño) para que el QR
// siga siendo legible -- RNF1.
internal suspend fun generateQrBitmap(
    content: String,
    moduleColor: Int = QR_DEFAULT_MODULE_COLOR,
    logo: Bitmap? = null
): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val size = 512
            val hints = if (logo != null) {
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
            } else {
                emptyMap()
            }
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            // ARGB_8888 (antes RGB_565, sin canal alfa) -- necesario para
            // poder dibujar el logo encima con un Canvas normal sin perder
            // su transparencia si el PNG del logo la tiene.
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y,
                        if (bitMatrix[x, y]) moduleColor
                        else android.graphics.Color.WHITE)
                }
            }
            logo?.let { overlayQrLogo(bitmap, it) }
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "generateQrBitmap: error")
            null
        }
    }

// Logo centrado con su propio fondo blanco (mismo margen de "quiet zone"
// que usan los generadores de QR con logo estándar) para no perder
// contraste contra los módulos que quedan justo alrededor.
private fun overlayQrLogo(bitmap: Bitmap, logo: Bitmap) {
    val canvas = Canvas(bitmap)
    val logoSize = (bitmap.width * 0.22f).toInt()
    val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
    val left = (bitmap.width - logoSize) / 2f
    val top = (bitmap.height - logoSize) / 2f
    val padding = logoSize * 0.1f
    val backgroundPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
    }
    canvas.drawRoundRect(
        left - padding, top - padding, left + logoSize + padding, top + logoSize + padding,
        16f, 16f, backgroundPaint
    )
    canvas.drawBitmap(scaledLogo, left, top, Paint().apply { isAntiAlias = true })
    if (scaledLogo !== logo) scaledLogo.recycle()
}

// El logo termina reescalado a un cuadrado pequeño (`overlayQrLogo`), así
// que decodificarlo a su resolución de cámara/galería original (a veces
// 12-108 MP) desperdicia memoria sin ningún beneficio visual -- bug real
// encontrado en la revisión pre-fusión, riesgo de OutOfMemoryError en
// dispositivos con poca RAM. Mismo patrón estándar de Android (decodificar
// primero solo los bounds, calcular `inSampleSize`, volver a decodificar
// ya reducido) para no cargar más que [targetSize] px de lado.
private const val LOGO_TARGET_SIZE = 512

private fun decodeSampledBitmap(context: Context, uri: Uri, targetSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null

    var sampleSize = 1
    var width = bounds.outWidth
    var height = bounds.outHeight
    while (width / 2 >= targetSize || height / 2 >= targetSize) {
        width /= 2
        height /= 2
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}


internal suspend fun saveQrToFile(context: Context, bitmap: Bitmap): File? =
    withContext(Dispatchers.IO) {
        try {
            val dir  = File(context.cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "QR_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (e: Exception) { Timber.e(e, "saveQrToFile"); null }
    }


internal fun shareQrImage(context: Context, file: File, chooserTitle: String) {
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

internal fun copyToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cb.setPrimaryClip(android.content.ClipData.newPlainText("QR", text))
}

internal fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (e: Exception) { Timber.e(e, "openUrl") }
}
