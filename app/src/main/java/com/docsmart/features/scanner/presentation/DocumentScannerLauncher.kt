package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import timber.log.Timber

// Extraído de ScannerScreen (backlog UX 2026-08-30, HU-UX-03) para
// reutilizar la misma configuración del escáner de ML Kit también desde
// el atajo "Capturar con cámara" de Convertir, sin duplicarla.
fun launchDocumentScanner(
    activity  : Activity,
    mode      : ScannerMode,
    onLaunched: (IntentSender) -> Unit,
    onError   : (String) -> Unit
) {
    val scannerMode = when (mode) {
        ScannerMode.DOCUMENT -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
        ScannerMode.PHOTO -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
    }

    // RF-SCAN-06/07: solo JPEG -- el PDF final (cuando se necesita) lo arma
    // el conversor propio de DocuSmart, no ML Kit.
    val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(scannerMode)
        .setPageLimit(10)
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
