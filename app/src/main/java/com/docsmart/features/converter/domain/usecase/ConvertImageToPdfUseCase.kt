package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.roundToInt

class ConvertImageToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PAGE_WIDTH  = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 20

        // "Alta resolución" (premium, backlog UX #33): el PDF siempre midió
        // la página en puntos (595x842 = A4 a 72pt/in), pero antes también
        // se usaban esos mismos números como tamaño en PÍXELES del bitmap
        // incrustado -- eso limitaba cualquier escaneo, sin importar la
        // cámara del teléfono, a un equivalente de ~72 DPI (~67 DPI real
        // descontando el margen). Ahora el tamaño en puntos del recuadro de
        // dibujo se mantiene igual siempre (misma página, mismo layout) y
        // solo cambia cuántos píxeles reales del bitmap se conservan dentro
        // de ese recuadro -- este multiplicador define esa densidad extra
        // para usuarios Premium (x3 ≈ 216 DPI, calidad de impresión).
        private const val HIGH_RES_MULTIPLIER = 3
    }

    suspend operator fun invoke(
        imageUris: List<Uri>,
        fileName: String = generateFileName(),
        highResolution: Boolean = false
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                return@withContext ConversionResult.Error(
                    "Debes seleccionar al menos una imagen"
                )
            }

            Timber.d("Convirtiendo ${imageUris.size} imágenes a PDF (highResolution=$highResolution)")

            val pdfDocument = PdfDocument()
            val paint = Paint().apply {
                isAntiAlias    = true
                isFilterBitmap = true
            }

            imageUris.forEachIndexed { index, uri ->
                val bitmap = loadBitmapFromUri(uri)
                if (bitmap == null) {
                    Timber.w("No se pudo cargar imagen $index: $uri")
                    return@forEachIndexed
                }

                val drawRect = pageDrawRect(bitmap.width, bitmap.height)
                val embeddedBitmap = embedBitmapForDrawRect(bitmap, drawRect, highResolution)

                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, index + 1
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(embeddedBitmap, null, drawRect, paint)
                pdfDocument.finishPage(page)

                if (embeddedBitmap != bitmap) embeddedBitmap.recycle()
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

    /** Recuadro (en puntos, centrado) donde se dibuja la imagen en la
     *  página -- siempre el mismo, con o sin alta resolución, para que el
     *  layout final no cambie según el plan del usuario. */
    private fun pageDrawRect(bitmapWidth: Int, bitmapHeight: Int): RectF {
        val maxWidth  = PAGE_WIDTH  - MARGIN * 2
        val maxHeight = PAGE_HEIGHT - MARGIN * 2

        val widthRatio  = maxWidth.toFloat()  / bitmapWidth
        val heightRatio = maxHeight.toFloat() / bitmapHeight
        val ratio = minOf(widthRatio, heightRatio, 1f)

        val drawWidth  = bitmapWidth  * ratio
        val drawHeight = bitmapHeight * ratio
        val left = (PAGE_WIDTH  - drawWidth)  / 2f
        val top  = (PAGE_HEIGHT - drawHeight) / 2f
        return RectF(left, top, left + drawWidth, top + drawHeight)
    }

    /** Bitmap que se incrusta dentro de [drawRect]: en modo estándar, tantos
     *  píxeles como puntos tiene el recuadro (el comportamiento histórico);
     *  en alta resolución, [HIGH_RES_MULTIPLIER] veces más -- nunca se
     *  agranda más allá de lo que la cámara ya capturó. */
    private fun embedBitmapForDrawRect(bitmap: Bitmap, drawRect: RectF, highResolution: Boolean): Bitmap {
        val multiplier = if (highResolution) HIGH_RES_MULTIPLIER else 1
        val targetWidth  = (drawRect.width()  * multiplier).roundToInt().coerceAtLeast(1)
        val targetHeight = (drawRect.height() * multiplier).roundToInt().coerceAtLeast(1)

        return if (bitmap.width <= targetWidth && bitmap.height <= targetHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
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