package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ui.theme.rememberBannerGradient
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import timber.log.Timber

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onScanComplete: (List<Uri>) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
    scanSessionViewModel: ScanSessionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    val scannerStartErrorTemplate = stringResource(R.string.scanner_start_error)

    // Hallazgo real de la revisión adversarial de seguridad sobre el fix
    // S1 de la auditoría general 2026-09-17 (quinta pasada): "Escanear
    // otro" reutiliza esta misma pantalla haciendo `onBack()` desde
    // ScanResultScreen -- eso saca a ScanResultScreen de la pila SIN
    // limpiar la sesión (a propósito, para no perder lo ya escaneado). Si
    // el usuario cancela ESTE nuevo intento de captura, `onBack()` de acá
    // ya no vuelve a ScanResultScreen (quedó fuera de la pila) sino más
    // atrás -- la sesión anterior queda huérfana en el singleton
    // `ScanSessionManager`, sin ninguna pantalla que la muestre, lista
    // para mezclarse con la próxima sesión real. Se limpia acá, mismo
    // criterio que "Volver al inicio" en ScanResultScreen (no-op seguro
    // si no había nada que limpiar).
    val scannerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    GmsDocumentScanningResult
                        .fromActivityResultIntent(result.data)

                // RF-SCAN-06/07: se dejó de pedir RESULT_FORMAT_PDF -- ML Kit
                // solo hace el recorte/corrección de perspectiva y devuelve las
                // páginas como imágenes; el PDF final lo arma el conversor
                // propio de DocuSmart (ConvertImageToPdfUseCase, ya usado en
                // Conversión), que es el mismo paso donde ahora se puede
                // ajustar brillo/contraste/escala de cada página antes de
                // generarlo. Antes, pedir también PDF hacía que esa rama
                // siempre "ganara" y las páginas nunca se usaran de verdad.
                val pages = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()

                Timber.d("Escáner: ${pages.size} páginas")

                if (pages.isNotEmpty()) {
                    viewModel.onScanComplete(pages, isPdf = false)
                    onScanComplete(pages)
                } else {
                    scanSessionViewModel.clearSession()
                    onBack()
                }
            } else {
                Timber.d("Escáner: cancelado")
                scanSessionViewModel.clearSession()
                onBack()
            }
        }

    // H1 (auditoría, corrección real): `LaunchedEffect(Unit)` se vuelve a
    // ejecutar cada vez que se recompone esta pantalla desde cero, y
    // rotar el dispositivo mientras el escáner de ML Kit está en pantalla
    // (una Activity externa) recrea esta Activity y, con ella, toda la
    // composición -- sin ningún flag que sobreviva esa recreación,
    // `launchDocumentScanner()` se disparaba una segunda vez, abriendo el
    // picker de ML Kit por partida doble. `rememberSaveable` (a diferencia
    // de `remember`) persiste en el Bundle de `onSaveInstanceState` y se
    // restaura ANTES de que este `LaunchedEffect` vuelva a correr, así que
    // sobrevive la rotación igual que lo haría un flag en el ViewModel.
    var hasLaunchedScanner by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasLaunchedScanner) return@LaunchedEffect
        if (activity == null) {
            scanSessionViewModel.clearSession()
            onBack()
            return@LaunchedEffect
        }
        hasLaunchedScanner = true
        launchDocumentScanner(
            activity = activity,
            mode = uiState.selectedMode,
            onLaunched = { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build(),
                )
            },
            onError = { message ->
                val error = String.format(scannerStartErrorTemplate, message)
                Timber.e("Error al iniciar el escáner")
                viewModel.onError(error)
            },
        )
    }

    // ── UI mientras carga el escáner ──────────────────
    // Bug real corregido 2026-09-04 (backlog UX §7, HU-UX-06): fondo fijo
    // en tonos de azul, ignorando el "Color de acento" elegido en Ajustes.
    val bannerGradient = rememberBannerGradient()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(brush = Brush.linearGradient(colors = bannerGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraLarge,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                text = stringResource(R.string.scanner_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Text(
                text = stringResource(R.string.scanner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            if (uiState.error != null) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                ) {
                    Text(stringResource(R.string.general_back))
                }
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
