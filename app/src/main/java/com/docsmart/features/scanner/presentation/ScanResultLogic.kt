package com.docsmart.features.scanner.presentation

import com.docsmart.features.converter.domain.model.ConversionType
import kotlin.math.roundToInt

// Ronda 17: lógica pura extraída de ScanResultScreen.kt (sin Compose ni
// Android) para poder testearla en JVM. Comportamiento idéntico.

internal const val MIME_PDF = "application/pdf"

// Backlog UX #33 (pedido explícito del usuario 2026-09-06): antes el
// Escáner solo podía terminar en PDF -- ahora también puede exportar las
// páginas como imágenes. "Alta resolución" es exclusivo de PDF (JPG/WebP
// ya exportan siempre a la resolución nativa de la cámara, sin reducir
// nada, así que no hay nada que mejorar ahí).
internal enum class ScanExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", MIME_PDF),
    JPG("JPG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp"),
}

internal fun ScanExportFormat.toImageConversionType(): ConversionType? =
    when (this) {
        ScanExportFormat.PDF -> null
        ScanExportFormat.JPG -> ConversionType.IMAGE_TO_JPG
        ScanExportFormat.WEBP -> ConversionType.IMAGE_TO_WEBP
    }

internal fun mimeTypeForExtension(extension: String): String =
    when (extension.lowercase()) {
        "pdf" -> MIME_PDF
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

// El editor de imagen muestra Brillo/Contraste en 0..100 (50 = sin cambios)
// pero `buildColorMatrix()` espera un rango simétrico -100..100 (0 = sin
// cambios).
internal const val SCAN_EDIT_DISPLAY_NEUTRAL = 50f

internal fun displayToInternal(display: Float): Int = ((display - SCAN_EDIT_DISPLAY_NEUTRAL) * 2f).roundToInt()

/**
 * Nombre final del archivo a guardar/compartir: el nombre libre ya saneado
 * ([sanitizedName], resultado de sanitizeOutputFileName) o, si quedó vacío,
 * la plantilla por defecto con el [timestamp].
 */
internal fun resolveScanOutputName(
    sanitizedName: String,
    defaultNameTemplate: String,
    timestamp: String,
): String = sanitizedName.ifBlank { String.format(defaultNameTemplate, timestamp) }
