package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        imageUri: Uri,
        targetType: ConversionType,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext ConversionResult.Error("No se pudo leer la imagen")

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()

            val (format, extension, mimeQuality) = when (targetType) {
                ConversionType.IMAGE_TO_JPG ->
                    Triple(Bitmap.CompressFormat.JPEG, "jpg", 90)
                ConversionType.IMAGE_TO_PNG ->
                    Triple(Bitmap.CompressFormat.PNG, "png", 100)
                else ->
                    Triple(Bitmap.CompressFormat.JPEG, "jpg", 90)
            }

            val outputFile = File(outputDir, "$baseName.$extension")
            outputFile.outputStream().use { out ->
                bitmap.compress(format, mimeQuality, out)
            }
            bitmap.recycle()

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo formato de imagen")
            ConversionResult.Error("Error: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}