package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.docsmart.R
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

        // Bug real reportado por testers 2026-09-11: el plan gratuito
        // incrustaba el bitmap a la misma cantidad de píxeles que puntos
        // tiene la página (595x842 = A4 a 72pt/in) -- un multiplicador de
        // 1x, equivalente a ~72 DPI (~67 DPI real descontando el margen),
        // demasiado bajo para leer texto con nitidez, sin importar qué tan
        // buena fuera la cámara del teléfono. Subido a un piso decente
        // (BASE_MULTIPLIER x2 ≈ 144 DPI) para que ningún usuario reciba un
        // documento que se vea roto; "Alta resolución" Premium (backlog UX
        // #33) sube proporcionalmente (x4 ≈ 288 DPI, calidad de impresión).
        //
        // Feedback real de testers 2026-09-13: 144 DPI seguía viéndose
        // borroso/pixelado al leer texto en pantallas modernas -- 144 DPI es
        // apenas el piso de "legible", no "nítido". Se sube el piso gratuito
        // a BASE_MULTIPLIER x3 ≈ 216 DPI (decisión explícita del usuario:
        // solo se sube el piso gratuito, Premium se mantiene en x4 ≈ 288 DPI
        // como diferenciador). El tamaño en puntos del recuadro de dibujo no
        // cambia (misma página, mismo layout) en ningún caso -- solo cuántos
        // píxeles reales del bitmap se conservan dentro de ese recuadro.
        private const val BASE_MULTIPLIER     = 3
        private const val HIGH_RES_MULTIPLIER = 4
    }

    suspend operator fun invoke(
        imageUris: List<Uri>,
        fileName: String = generateFileName(),
        highResolution: Boolean = false
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                return@withContext ConversionResult.Error(
                    context.getString(R.string.converter_error_no_images_selected)
                )
            }

            Timber.d("Convirtiendo ${imageUris.size} imágenes a PDF (highResolution=$highResolution)")

            val pdfDocument = PdfDocument()
            // Hallazgo real de la auditoría general 2026-09-17 (B6):
            // pdfDocument.close() solo se llamaba en los 2 caminos felices
            // (0 páginas / éxito) -- si algo lanzaba entre medio (ej.
            // FileOutputStream falla por disco lleno), el catch de abajo no
            // lo cerraba, mismo patrón de fuga ya corregido en el resto de
            // Herramientas PDF/Convertidor.
            try {
                val paint = Paint().apply {
                    isAntiAlias    = true
                    isFilterBitmap = true
                }

                // Bug real encontrado 2026-09-14 (repaso general): antes se
                // reportaba pageCount = imageUris.size (el original) sin
                // importar cuántas páginas se generaron de verdad -- si TODAS
                // las imágenes fallaban al decodificar, el resultado igual
                // llegaba como Success con 0 páginas reales (el header/xref de
                // un PdfDocument vacío ya pesa > 0 bytes, así que el chequeo de
                // abajo tampoco lo detectaba).
                var pageCount = 0
                imageUris.forEachIndexed { index, uri ->
                    val bitmap = loadBitmapFromUri(uri)
                    if (bitmap == null) {
                        Timber.w("No se pudo cargar imagen $index: $uri")
                        return@forEachIndexed
                    }

                    if (drawImagePage(pdfDocument, paint, bitmap, pageCount + 1, highResolution, index)) {
                        pageCount++
                        Timber.d("Página $pageCount generada")
                    }
                }

                if (pageCount == 0) {
                    return@withContext ConversionResult.Error(
                        context.getString(R.string.converter_error_no_images_loaded)
                    )
                }

                val outputDir = File(context.filesDir, "converted").apply {
                    if (!exists()) mkdirs()
                }
                val outputFile = File(outputDir, "$fileName.pdf")

                FileOutputStream(outputFile).use { stream ->
                    pdfDocument.writeTo(stream)
                    stream.flush()
                }

                Timber.d("PDF guardado: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

                if (outputFile.length() == 0L) {
                    return@withContext ConversionResult.Error(
                        context.getString(R.string.converter_error_generate_pdf_failed)
                    )
                }

                ConversionResult.Success(
                    outputFile = outputFile,
                    pageCount  = pageCount,
                    fileSizeKb = (outputFile.length() / 1024).toInt()
                )
            } finally {
                pdfDocument.close()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error en conversión: ${e.message}")
            ConversionResult.Error(
                message = String.format(
                    context.getString(R.string.converter_error_generic_format),
                    e.message ?: context.getString(R.string.converter_error_unknown)
                ),
            )
        } catch (e: OutOfMemoryError) {
            // Defensa en profundidad además del catch de loadBitmapFromUri:
            // el reescalado (embedBitmapForDrawRect) o el propio canvas
            // también pueden agotar la memoria con imágenes de alta
            // resolución -- OutOfMemoryError no hereda de Exception.
            Timber.e(e, "Sin memoria durante la conversión")
            ConversionResult.Error(
                context.getString(R.string.converter_error_generic_format)
                    .let { String.format(it, context.getString(R.string.converter_error_unknown)) }
            )
        }
    }

    // Hallazgo real de la revisión de corrección 2026-09-16: el catch de
    // loadBitmapFromUri solo cubre la decodificación -- embedBitmapForDrawRect
    // puede reescalar hasta HIGH_RES_MULTIPLIER (4x) el recuadro de la
    // página, y sin un try propio acá esa falta de memoria abortaba TODO el
    // lote en vez de saltarse solo esta imagen (mismo criterio que
    // loadBitmapFromUri). Extraído de invoke() además para bajar la
    // complejidad ciclomática (detekt).
    private fun drawImagePage(
        pdfDocument: PdfDocument,
        paint: Paint,
        bitmap: Bitmap,
        pageNumber: Int,
        highResolution: Boolean,
        index: Int
    ): Boolean = try {
        val drawRect = pageDrawRect(bitmap.width, bitmap.height)
        val embeddedBitmap = embedBitmapForDrawRect(bitmap, drawRect, highResolution)

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(embeddedBitmap, null, drawRect, paint)
        pdfDocument.finishPage(page)

        if (embeddedBitmap != bitmap) embeddedBitmap.recycle()
        true
    } catch (e: OutOfMemoryError) {
        Timber.e(e, "Sin memoria procesando imagen $index, se salta")
        false
    } finally {
        bitmap.recycle()
    }

    // Bug real reportado por testers 2026-09-11: fotos tomadas en vertical
    // aparecían "acostadas" (rotadas 90°) en el PDF generado. Causa: muchas
    // cámaras graban el buffer de píxeles crudo en horizontal y solo marcan
    // la orientación real en el tag EXIF -- sin leerlo, BitmapFactory
    // entrega el bitmap tal cual vino del sensor. Se corrige rotando el
    // bitmap decodificado según ese tag antes de incrustarlo en la página.
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            rotateBitmapForOrientation(rawBitmap, orientation)
        } catch (e: Exception) {
            Timber.e("Error cargando imagen: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            // Hallazgo real de la revisión general 2026-09-16: una sola
            // imagen muy grande en el lote no debe tirar abajo TODA la
            // conversión -- OutOfMemoryError no hereda de Exception, así
            // que el catch de arriba nunca la atrapaba. Se trata igual que
            // cualquier otra imagen que no se pudo cargar: se salta.
            Timber.e(e, "Sin memoria decodificando imagen, se salta")
            null
        }
    }

    private fun rotateBitmapForOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
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

    /** Bitmap que se incrusta dentro de [drawRect]: en modo estándar,
     *  [BASE_MULTIPLIER] píxeles por punto del recuadro; en alta resolución,
     *  [HIGH_RES_MULTIPLIER] -- nunca se agranda más allá de lo que la
     *  cámara ya capturó. */
    private fun embedBitmapForDrawRect(bitmap: Bitmap, drawRect: RectF, highResolution: Boolean): Bitmap {
        val multiplier = if (highResolution) HIGH_RES_MULTIPLIER else BASE_MULTIPLIER
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