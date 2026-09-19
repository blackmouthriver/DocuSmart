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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Transformación a aplicar a un bitmap para "enderezarlo" según su tag EXIF de
 * orientación: primero se rota `rotationDegrees` (horario) y después se refleja
 * horizontal y/o verticalmente. Función de decisión pura -- se puede testear en
 * JVM, a diferencia de aplicarla (necesita `Matrix`/`Bitmap` reales).
 */
internal data class ExifTransform(
    val rotationDegrees: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
)

/**
 * Ronda 15: las orientaciones EXIF 5 (TRANSPOSE) y 7 (TRANSVERSE) caían en el
 * `else` y se ignoraban (la imagen salía espejada/rotada). Devuelve `null` si
 * no hay nada que transformar (NORMAL/UNDEFINED/valor desconocido).
 */
internal fun exifTransformFor(orientation: Int): ExifTransform? =
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90f, flipHorizontal = false, flipVertical = false)
        ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180f, flipHorizontal = false, flipVertical = false)
        ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270f, flipHorizontal = false, flipVertical = false)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0f, flipHorizontal = true, flipVertical = false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(0f, flipHorizontal = false, flipVertical = true)
        ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90f, flipHorizontal = true, flipVertical = false)
        ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270f, flipHorizontal = true, flipVertical = false)
        else -> null
    }

/** Aplica [exifTransformFor] a [bitmap]; recicla el original si crea uno nuevo. */
internal fun applyExifOrientation(
    bitmap: Bitmap,
    orientation: Int,
): Bitmap {
    val transform = exifTransformFor(orientation) ?: return bitmap
    val matrix = Matrix()
    if (transform.rotationDegrees != 0f) matrix.postRotate(transform.rotationDegrees)
    if (transform.flipHorizontal) matrix.postScale(-1f, 1f)
    if (transform.flipVertical) matrix.postScale(1f, -1f)
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bitmap.recycle()
    return rotated
}

/**
 * Lee el tag EXIF de orientación de [uri]. Ronda 15: un fallo leyendo el EXIF
 * (stream sin metadatos, formato raro) hacía que la imagen completa se
 * descartara como "no se pudo cargar" -- el EXIF es opcional, ante un fallo se
 * usa NORMAL. La cancelación sí se propaga.
 */
internal fun readExifOrientation(
    context: Context,
    uri: Uri,
): Int =
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("Sin orientación EXIF legible: ${e.javaClass.simpleName}")
        ExifInterface.ORIENTATION_NORMAL
    }

class ConvertImageToPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val PAGE_WIDTH = 595
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
            private const val BASE_MULTIPLIER = 3
            private const val HIGH_RES_MULTIPLIER = 4
        }

        suspend operator fun invoke(
            imageUris: List<Uri>,
            fileName: String = generateFileName(),
            highResolution: Boolean = false,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                // Hallazgo real de la auditoría del Convertidor (ronda 14):
                // outputFile se creaba como `val` DENTRO de writePdfDocumentToFile()
                // -- si `pdfDocument.writeTo(stream)` fallaba a mitad de escritura
                // (ej. disco lleno, o un OutOfMemoryError al volcar a bytes un PDF
                // ya renderizado en memoria con muchas páginas de alta resolución),
                // ningún catch de acá abajo tenía forma de referenciar ese File
                // para borrar el .pdf parcial/corrupto que FileOutputStream ya
                // había creado en disco. Mismo patrón ya corregido en
                // PdfToWordUseCase.kt/ExcelToPdfUseCase.kt/PptToPdfUseCase.kt/
                // WordToPdfUseCase.kt: outputFile se declara `var` afuera del try.
                var outputFile: File? = null
                try {
                    if (imageUris.isEmpty()) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_no_images_selected),
                        )
                    }

                    Timber.d("Convirtiendo ${imageUris.size} imágenes a PDF (highResolution=$highResolution)")

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    outputFile = File(outputDir, "$fileName.pdf")

                    buildPdfFromImages(imageUris, outputFile, highResolution)
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): CancellationException
                    // hereda de Exception -- sin este catch específico antes del
                    // genérico de abajo, salir de la pantalla a mitad de la
                    // conversión se registraba como un error de conversión en vez
                    // de propagarse como cancelación real (mismo criterio que
                    // CompressPdfUseCase.kt).
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    // Solo el tipo: CrashlyticsTree reenvía todo >= WARN a Firebase
                    // y e.message puede contener rutas/URIs reales.
                    Timber.e("Error en conversión: ${e.javaClass.simpleName}")
                    outputFile?.delete()
                    ConversionResult.Error(
                        message =
                            String.format(
                                context.getString(R.string.converter_error_generic_format),
                                e.message ?: context.getString(R.string.converter_error_unknown),
                            ),
                    )
                } catch (e: OutOfMemoryError) {
                    // Defensa en profundidad además del catch de loadBitmapFromUri:
                    // el reescalado (embedBitmapForDrawRect) o el propio canvas
                    // también pueden agotar la memoria con imágenes de alta
                    // resolución -- OutOfMemoryError no hereda de Exception.
                    Timber.e(e, "Sin memoria durante la conversión")
                    outputFile?.delete()
                    ConversionResult.Error(
                        context
                            .getString(R.string.converter_error_generic_format)
                            .let { String.format(it, context.getString(R.string.converter_error_unknown)) },
                    )
                }
            }

        // Extraído de invoke() (detekt: CyclomaticComplexMethod, disparado al
        // agregar el catch de CancellationException del hallazgo 1 de la
        // auditoría del Convertidor) -- agrupa la generación real del PDF
        // (páginas + escritura a disco), separado del manejo de errores.
        //
        // Hallazgo real de la auditoría general 2026-09-17 (B6): pdfDocument.close()
        // solo se llamaba en los 2 caminos felices (0 páginas / éxito) -- si algo
        // lanzaba entre medio (ej. FileOutputStream falla por disco lleno), el
        // catch de invoke() no lo cerraba, mismo patrón de fuga ya corregido en
        // el resto de Herramientas PDF/Convertidor.
        //
        // Ronda 15: ahora suspend con ensureActive() por imagen -- sin ningún
        // punto de suspensión, cancelar a mitad del lote no se notaba hasta que
        // TODAS las imágenes terminaban; withContext entonces descartaba el
        // Success ya calculado y el .pdf recién escrito quedaba huérfano.
        private suspend fun buildPdfFromImages(
            imageUris: List<Uri>,
            outputFile: File,
            highResolution: Boolean,
        ): ConversionResult {
            val pdfDocument = PdfDocument()
            try {
                val paint =
                    Paint().apply {
                        isAntiAlias = true
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
                    coroutineContext.ensureActive()
                    val bitmap = loadBitmapFromUri(uri)
                    if (bitmap == null) {
                        Timber.w("No se pudo cargar imagen $index")
                        return@forEachIndexed
                    }

                    if (drawImagePage(pdfDocument, paint, bitmap, pageCount + 1, highResolution, index)) {
                        pageCount++
                        Timber.d("Página $pageCount generada")
                    }
                }

                // detekt: ReturnCount -- un solo `return` con un if/else en vez
                // de 3 returns tempranos separados (0 páginas / PDF vacío /
                // éxito), disparado al agregar el catch de CancellationException
                // del hallazgo 1 de la auditoría del Convertidor.
                return if (pageCount == 0) {
                    ConversionResult.Error(context.getString(R.string.converter_error_no_images_loaded))
                } else {
                    writePdfDocumentToFile(pdfDocument, outputFile, pageCount)
                }
            } finally {
                pdfDocument.close()
            }
        }

        // Extraído de buildPdfFromImages() para bajar ReturnCount/complejidad --
        // escribe el PdfDocument ya renderizado a disco y arma el resultado.
        // outputFile ya viene creado (File object) desde invoke() -- ver el
        // hallazgo de la ronda 14 ahí, que necesita poder referenciarlo para
        // borrarlo si esta escritura falla a mitad de camino.
        private fun writePdfDocumentToFile(
            pdfDocument: PdfDocument,
            outputFile: File,
            pageCount: Int,
        ): ConversionResult {
            FileOutputStream(outputFile).use { stream ->
                pdfDocument.writeTo(stream)
                stream.flush()
            }

            Timber.d("PDF guardado: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            return if (outputFile.length() == 0L) {
                outputFile.delete()
                ConversionResult.Error(context.getString(R.string.converter_error_generate_pdf_failed))
            } else {
                ConversionResult.Success(
                    outputFile = outputFile,
                    pageCount = pageCount,
                    fileSizeKb = (outputFile.length() / 1024).toInt(),
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
            index: Int,
        ): Boolean =
            try {
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
        // internal (no private) para poder testear directamente el fix de
        // CancellationException de la ronda 14 sin pasar por buildPdfFromImages()
        // -- ese método construye un android.graphics.pdf.PdfDocument real, que
        // no se puede cerrar en un test JVM puro sin Robolectric ("Method close
        // ... not mocked"), y ese error de infraestructura enmascararía la
        // CancellationException real que se está verificando acá.
        internal fun loadBitmapFromUri(uri: Uri): Bitmap? {
            return try {
                val rawBitmap =
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: return null

                applyExifOrientation(rawBitmap, readExifOrientation(context, uri))
            } catch (e: CancellationException) {
                // Hallazgo real de la auditoría del Convertidor (ronda 14): el
                // fix de "Hallazgo 1" (CancellationException hereda de
                // Exception, hay que relanzarla antes del catch genérico) se
                // aplicó en invoke(), pero NO se propagó a este método anidado
                // -- si la corrutina se cancelaba (ej. el usuario sale de la
                // pantalla) justo mientras se leía/decodificaba una imagen del
                // lote, este catch(Exception) de abajo la atrapaba primero y la
                // trataba como "esta imagen no se pudo cargar, se salta",
                // dejando que el loop siguiera procesando el resto de
                // imágenes de un trabajo que ya debería haberse detenido.
                throw e
            } catch (e: Exception) {
                Timber.e("Error cargando imagen: ${e.javaClass.simpleName}")
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

        /** Recuadro (en puntos, centrado) donde se dibuja la imagen en la
         *  página -- siempre el mismo, con o sin alta resolución, para que el
         *  layout final no cambie según el plan del usuario. */
        private fun pageDrawRect(
            bitmapWidth: Int,
            bitmapHeight: Int,
        ): RectF {
            val maxWidth = PAGE_WIDTH - MARGIN * 2
            val maxHeight = PAGE_HEIGHT - MARGIN * 2

            val widthRatio = maxWidth.toFloat() / bitmapWidth
            val heightRatio = maxHeight.toFloat() / bitmapHeight
            val ratio = minOf(widthRatio, heightRatio, 1f)

            val drawWidth = bitmapWidth * ratio
            val drawHeight = bitmapHeight * ratio
            val left = (PAGE_WIDTH - drawWidth) / 2f
            val top = (PAGE_HEIGHT - drawHeight) / 2f
            return RectF(left, top, left + drawWidth, top + drawHeight)
        }

        /** Bitmap que se incrusta dentro de [drawRect]: en modo estándar,
         *  [BASE_MULTIPLIER] píxeles por punto del recuadro; en alta resolución,
         *  [HIGH_RES_MULTIPLIER] -- nunca se agranda más allá de lo que la
         *  cámara ya capturó. */
        private fun embedBitmapForDrawRect(
            bitmap: Bitmap,
            drawRect: RectF,
            highResolution: Boolean,
        ): Bitmap {
            val multiplier = if (highResolution) HIGH_RES_MULTIPLIER else BASE_MULTIPLIER
            val targetWidth = (drawRect.width() * multiplier).roundToInt().coerceAtLeast(1)
            val targetHeight = (drawRect.height() * multiplier).roundToInt().coerceAtLeast(1)

            return if (bitmap.width <= targetWidth && bitmap.height <= targetHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }
        }

        private fun generateFileName(): String {
            val timestamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault(),
                ).format(Date())
            return "Conversion_$timestamp"
        }
    }
