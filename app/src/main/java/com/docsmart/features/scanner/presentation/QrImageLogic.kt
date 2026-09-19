package com.docsmart.features.scanner.presentation

// Ronda 17: lógica pura de imágenes/caché del Creador de QR, extraída de
// QrScreen.kt para poder testearla en JVM (sin BitmapFactory ni File).

// Los PNG temporales de cacheDir/qr/ se borran pasada 1 hora, sin tocar el
// recién compartido (la app receptora puede estar leyéndolo todavía).
internal const val QR_CACHE_MAX_AGE_MILLIS = 60 * 60 * 1000L

/** true si el archivo temporal de QR es más viejo que [QR_CACHE_MAX_AGE_MILLIS]. */
internal fun isQrCacheFileStale(
    lastModifiedMillis: Long,
    nowMillis: Long,
): Boolean = lastModifiedMillis < nowMillis - QR_CACHE_MAX_AGE_MILLIS

/**
 * Factor `inSampleSize` (potencia de 2) más grande que deja el lado mayor
 * de la imagen todavía >= [targetSize]. Dimensiones no positivas (decodificación
 * fallida) devuelven 1.
 */
internal fun computeSampleSize(
    width: Int,
    height: Int,
    targetSize: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth / 2 >= targetSize || currentHeight / 2 >= targetSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize
}
