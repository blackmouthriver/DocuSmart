package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
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
    activity  : Activity,
    mode      : ScannerMode,
    onLaunched: (IntentSender) -> Unit,
    onError   : (String) -> Unit,
    pageLimit : Int = SCAN_DEFAULT_PAGE_LIMIT
) {
    val scannerMode = when (mode) {
        ScannerMode.DOCUMENT -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
        ScannerMode.PHOTO -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
    }

    // RF-SCAN-06/07: solo JPEG -- el PDF final (cuando se necesita) lo arma
    // el conversor propio de DocuSmart, no ML Kit.
    val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(scannerMode)
        .setPageLimit(pageLimit)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()

    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { onLaunched(it) }
        .addOnFailureListener { e ->
            Timber.e(e, "Error obteniendo intent del escáner")
            onError(e.message ?: "")
        }
}
