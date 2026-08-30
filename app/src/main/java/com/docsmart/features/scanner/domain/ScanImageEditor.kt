package com.docsmart.features.scanner.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * RF-SCAN-06/RF-SCAN-07: ajuste de brillo/contraste y reescalado sobre una
 * imagen ya escaneada. Google ML Kit Document Scanner no expone ninguno de
 * los dos controles (ver RNF-SCAN-01 en scanner.md) -- se resuelve como un
 * paso de edición propio DESPUÉS de recibir el resultado del escáner, sin
 * tocar la captura en sí.
 */
class ScanImageEditor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun applyAdjustments(
        sourceUri: Uri,
        brightness: Int,
        contrast: Int,
        scalePercent: Int
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val original = loadBitmap(sourceUri) ?: return@withContext null
            val scaled = scaleBitmap(original, scalePercent)
            val adjusted = applyColorAdjustments(scaled, brightness, contrast)

            val outputFile = writeToCache(adjusted)

            if (adjusted !== scaled) adjusted.recycle()
            if (scaled !== original) scaled.recycle()
            original.recycle()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
        } catch (e: Exception) {
            Timber.e(e, "Error aplicando ajustes a la imagen escaneada")
            null
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    private fun scaleBitmap(bitmap: Bitmap, scalePercent: Int): Bitmap {
        if (scalePercent >= SCALE_FULL_PERCENT) return bitmap
        val (width, height) = scaledDimensions(bitmap.width, bitmap.height, scalePercent)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun applyColorAdjustments(bitmap: Bitmap, brightness: Int, contrast: Int): Bitmap {
        if (brightness == 0 && contrast == 0) return bitmap
        val result = Bitmap.createBitmap(
            bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(brightness, contrast))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun writeToCache(bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "scanner_edits").apply { mkdirs() }
        val file = File(dir, "edit_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return file
    }

    companion object {
        private const val SCALE_FULL_PERCENT = 100
        private const val JPEG_QUALITY = 92
    }
}

/**
 * Nuevo ancho/alto tras aplicar un porcentaje de reescalado (RF-SCAN-07).
 * Función pura, sin `Bitmap` real, para poder testearla sin Robolectric.
 */
internal fun scaledDimensions(width: Int, height: Int, scalePercent: Int): Pair<Int, Int> {
    val newWidth = (width * scalePercent / 100f).toInt().coerceAtLeast(1)
    val newHeight = (height * scalePercent / 100f).toInt().coerceAtLeast(1)
    return newWidth to newHeight
}

/**
 * Matriz de color 4x5 (mismo formato que `android.graphics.ColorMatrix` y
 * `androidx.compose.ui.graphics.ColorMatrix` -- se reutiliza tal cual para
 * la vista previa en Compose y para el bake final sobre el bitmap real) que
 * aplica brillo (-100..100, desplazamiento aditivo por canal) y contraste
 * (-100..100, factor de escala anclado al gris medio) sobre una imagen.
 * Función pura -- sin ninguna clase de `android.graphics`, para poder
 * testearla como JVM unit test normal.
 */
internal fun buildColorMatrix(brightness: Int, contrast: Int): FloatArray {
    val contrastFactor = 1f + contrast / 100f
    val brightnessOffset = brightness * 2.55f
    val translate = (1f - contrastFactor) * 128f + brightnessOffset
    return floatArrayOf(
        contrastFactor, 0f, 0f, 0f, translate,
        0f, contrastFactor, 0f, 0f, translate,
        0f, 0f, contrastFactor, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    )
}
