package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ConvertImageToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ConvertImageToPdf"
        private const val PAGE_WIDTH  = 595
        private const val PAGE_HEIGHT = 842
    }

    suspend operator fun invoke(
        imageUris: List<Uri>,
        fileName: String = generateFileName()
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                return@withContext ConversionResult.Error(
                    "Debes seleccionar al menos una imagen"
                )
            }

            Timber.d("Convirtiendo ${imageUris.size} imágenes a PDF")

            val pdfDocument = PdfDocument()

            imageUris.forEachIndexed { index, uri ->
                val bitmap = loadBitmapFromUri(uri)
                if (bitmap == null) {
                    Timber.w("No se pudo cargar imagen $index: $uri")
                    return@forEachIndexed
                }

                val scaledBitmap = scaleBitmapToPage(bitmap)

                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, index + 1
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                val left = ((PAGE_WIDTH  - scaledBitmap.width)  / 2f)
                val top  = ((PAGE_HEIGHT - scaledBitmap.height) / 2f)

                val paint = Paint().apply {
                    isAntiAlias    = true
                    isFilterBitmap = true
                }

                canvas.drawBitmap(scaledBitmap, left, top, paint)
                pdfDocument.finishPage(page)

                if (scaledBitmap != bitmap) scaledBitmap.recycle()
                bitmap.recycle()

                Timber.d("Página ${index + 1} generada")
            }

            val outputDir = File(context.filesDir, "converted").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(outputDir, "$fileName.pdf")

            FileOutputStream(outputFile).use { stream ->
                pdfDocument.writeTo(stream)
                stream.flush()
            }
            pdfDocument.close()

            Timber.d("PDF guardado: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            if (outputFile.length() == 0L) {
                return@withContext ConversionResult.Error(
                    "Error al generar el PDF. Intenta de nuevo."
                )
            }

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = imageUris.size,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )

        } catch (e: Exception) {
            Timber.e(e, "Error en conversión: ${e.message}")
            ConversionResult.Error(
                message = "Error al convertir: ${e.message ?: "Error desconocido"}",
            )
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Timber.e("Error cargando imagen: ${e.message}")
            null
        }
    }

    private fun scaleBitmapToPage(bitmap: Bitmap): Bitmap {
        val maxWidth  = PAGE_WIDTH  - 40
        val maxHeight = PAGE_HEIGHT - 40

        val widthRatio  = maxWidth.toFloat()  / bitmap.width
        val heightRatio = maxHeight.toFloat() / bitmap.height
        val ratio = minOf(widthRatio, heightRatio, 1f)

        val newWidth  = (bitmap.width  * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        return if (newWidth == bitmap.width && newHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
    }

    private fun generateFileName(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())
        return "Conversion_$timestamp"
    }
}