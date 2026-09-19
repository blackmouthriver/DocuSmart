package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.docsmart.R
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import timber.log.Timber

// Backlog UX (pedido explícito del usuario 2026-09-06): tope de páginas
// de una sesión de escaneo de ML Kit -- se mantiene en 10 para la captura
// normal; "Agregar página" en ScanResultScreen reutiliza esta misma
// función con `pageLimit = 1` para sumar páginas extra una por una,
// gateado por anuncio/Premium (ver ScanAddPageButton).
const val SCAN_DEFAULT_PAGE_LIMIT = 10

// Extraído de ScannerScreen (backlog UX 2026-08-30, HU-UX-03) para
// reutilizar la misma configuración del escáner de ML Kit también desde
// el atajo "Capturar con cámara" de Convertir, sin duplicarla.
fun launchDocumentScanner(
    activity: Activity,
    mode: ScannerMode,
    onLaunched: (IntentSender) -> Unit,
    onError: (String) -> Unit,
    pageLimit: Int = SCAN_DEFAULT_PAGE_LIMIT,
) {
    val scannerMode =
        when (mode) {
            ScannerMode.DOCUMENT -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
            ScannerMode.PHOTO -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
        }

    // RF-SCAN-06/07: solo JPEG -- el PDF final (cuando se necesita) lo arma
    // el conversor propio de DocuSmart, no ML Kit.
    val options =
        GmsDocumentScannerOptions
            .Builder()
            .setScannerMode(scannerMode)
            .setPageLimit(pageLimit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()

    GmsDocumentScanning
        .getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { onLaunched(it) }
        .addOnFailureListener { e ->
            Timber.e("Error obteniendo intent del escáner (${e.javaClass.simpleName})")
            onError(e.message ?: "")
        }
}

// Extraído de ConverterScreen (backlog UX 2026-08-30, HU-UX-03) y
// promovido acá (backlog UX #49, 2026-09-17) para que Notas de Modo
// Estudio reutilice el mismo launcher en vez de duplicarlo -- envuelve
// launchDocumentScanner() con el registro de
// ActivityResultContracts.StartIntentSenderForResult() que necesita
// Compose para recibir el resultado del escáner de ML Kit.
@Composable
fun rememberDocumentScannerAction(
    activity: Activity?,
    mode: ScannerMode = ScannerMode.DOCUMENT,
    pageLimit: Int = SCAN_DEFAULT_PAGE_LIMIT,
    onPagesScanned: (List<Uri>) -> Unit,
    onScanError: (String) -> Unit,
): () -> Unit {
    val scannerStartErrorTemplate = stringResource(R.string.scanner_start_error)
    val documentScanLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pages =
                    GmsDocumentScanningResult
                        .fromActivityResultIntent(result.data)
                        ?.pages
                        ?.mapNotNull { it.imageUri } ?: emptyList()
                if (pages.isNotEmpty()) onPagesScanned(pages)
            }
        }
    return {
        activity?.let { act ->
            launchDocumentScanner(
                activity = act,
                mode = mode,
                pageLimit = pageLimit,
                onLaunched = { intentSender ->
                    documentScanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                },
                onError = { message ->
                    onScanError(String.format(scannerStartErrorTemplate, message))
                },
            )
        }
    }
}
