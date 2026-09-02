package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ImageFormatUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        imageUri  : Uri,
        targetType: ConversionType,
        fileName  : String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext ConversionResult.Error("No se pudo leer la imagen")

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName  = fileName ?: generateTimestamp()

            val (format, extension, quality) = when (targetType) {
                ConversionType.IMAGE_TO_JPG  -> Triple(Bitmap.CompressFormat.JPEG, "jpg",  90)
                ConversionType.IMAGE_TO_PNG  -> Triple(Bitmap.CompressFormat.PNG,  "png",  100)
                ConversionType.IMAGE_TO_WEBP -> Triple(webpFormat(), "webp", 90)
                ConversionType.IMAGE_TO_BMP  -> Triple(Bitmap.CompressFormat.PNG,  "bmp",  100) // BMP vía PNG sin pérdida
                else                         -> Triple(Bitmap.CompressFormat.JPEG, "jpg",  90)
            }

            val outputFile = File(outputDir, "$baseName.$extension")
            outputFile.outputStream().use { out -> bitmap.compress(format, quality, out) }
            bitmap.recycle()

            Timber.d("ImageFormatUseCase: convertido a $extension — ${outputFile.length() / 1024} KB")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "ImageFormatUseCase: error")
            ConversionResult.Error("Error: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    // WEBP_LOSSLESS/WEBP_LOSSY requieren API 30+; minSdk de la app es 26.
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }
}