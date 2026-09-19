package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Extraído de ImageFormatUseCase.invoke() (detekt: CyclomaticComplexMethod,
// disparado al sumar el manejo de BMP/transparencia de la revisión general
// 2026-09-16, cuarta pasada) -- agrupa qué formato/extensión usar y si hace
// falta componer sobre blanco antes de escribir, para que invoke() no tenga
// que repetir esa lógica.
private data class TargetFormat(
    val isBmp: Boolean,
    val compressFormat: Bitmap.CompressFormat,
    val extension: String,
    val quality: Int,
) {
    val needsWhiteBackground: Boolean get() = isBmp || compressFormat == Bitmap.CompressFormat.JPEG
}

class ImageFormatUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val BMP_FILE_HEADER_SIZE = 14
            private const val BMP_INFO_HEADER_SIZE = 40
            private const val BMP_HEADER_SIZE = BMP_FILE_HEADER_SIZE + BMP_INFO_HEADER_SIZE
            private const val BMP_BYTES_PER_PIXEL = 3
        }

        suspend operator fun invoke(
            imageUri: Uri,
            targetType: ConversionType,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap =
                        context.contentResolver.openInputStream(imageUri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_image))

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    val target = resolveTargetFormat(targetType)

                    // Hallazgo real de la revisión general 2026-09-16 (cuarta
                    // pasada): ni JPEG ni el BMP de 24 bits sin comprimir
                    // (resolveTargetFormat/bitmapToBmp) soportan canal alfa --
                    // comprimir un PNG/WebP con transparencia directo a
                    // cualquiera de los dos deja las zonas transparentes/
                    // semitransparentes NEGRAS en el resultado (Android
                    // premultiplica el alfa) en vez de blancas. Se compone sobre
                    // un fondo blanco antes de comprimir/escribir.
                    val bitmapToCompress =
                        if (target.needsWhiteBackground && bitmap.hasAlpha()) {
                            flattenOnWhite(bitmap)
                        } else {
                            bitmap
                        }

                    val outputFile = File(outputDir, "$baseName.${target.extension}")
                    outputFile.outputStream().use { out -> writeImage(out, bitmapToCompress, target) }
                    bitmap.recycle()
                    if (bitmapToCompress !== bitmap) bitmapToCompress.recycle()

                    Timber.d("ImageFormatUseCase: convertido a ${target.extension} — ${outputFile.length() / 1024} KB")

                    ConversionResult.Success(
                        outputFile = outputFile,
                        pageCount = 1,
                        fileSizeKb = (outputFile.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt.
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "ImageFormatUseCase: error")
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la revisión general 2026-09-16 (#41):
                    // BitmapFactory.decodeStream() sin ningún límite de resolución
                    // puede agotar la memoria con una imagen de entrada muy grande
                    // -- OutOfMemoryError no hereda de Exception, así que el catch
                    // de arriba nunca la atrapaba.
                    Timber.e(e, "ImageFormatUseCase: sin memoria decodificando la imagen")
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
                    )
                }
            }

        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // codificador BMP de 24 bits sin comprimir (BITMAPFILEHEADER de 14
        // bytes + BITMAPINFOHEADER de 40 bytes + filas de píxeles BGR de
        // abajo hacia arriba, cada una rellenada a un múltiplo de 4 bytes) --
        // formato BI_RGB estándar, el más simple y ampliamente soportado.
        internal fun bitmapToBmp(bitmap: Bitmap): ByteArray {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return argbPixelsToBmp(width, height, pixels)
        }

        // Extraída de bitmapToBmp() para poder testear el empaquetado de bytes
        // (lo realmente propenso a error de este fix) sin depender de
        // android.graphics.Bitmap -- este proyecto no tiene Robolectric, así
        // que un test unitario no puede construir un Bitmap real.
        internal fun argbPixelsToBmp(
            width: Int,
            height: Int,
            pixels: IntArray,
        ): ByteArray {
            val rowSizeUnpadded = width * BMP_BYTES_PER_PIXEL
            val rowPadding = (4 - rowSizeUnpadded % 4) % 4
            val rowSize = rowSizeUnpadded + rowPadding
            val pixelDataSize = rowSize * height
            val fileSize = BMP_HEADER_SIZE + pixelDataSize

            val buffer =
                java.nio.ByteBuffer
                    .allocate(fileSize)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)

            // BITMAPFILEHEADER (14 bytes)
            buffer.put('B'.code.toByte())
            buffer.put('M'.code.toByte())
            buffer.putInt(fileSize)
            buffer.putShort(0) // reservado 1
            buffer.putShort(0) // reservado 2
            buffer.putInt(BMP_HEADER_SIZE) // offset de los datos de píxeles

            // BITMAPINFOHEADER (40 bytes)
            buffer.putInt(BMP_INFO_HEADER_SIZE)
            buffer.putInt(width)
            buffer.putInt(height) // positivo -> filas de abajo hacia arriba
            buffer.putShort(1) // planos
            buffer.putShort((BMP_BYTES_PER_PIXEL * 8).toShort()) // bits por píxel
            buffer.putInt(0) // BI_RGB, sin compresión
            buffer.putInt(pixelDataSize)
            buffer.putInt(0) // píxeles por metro X
            buffer.putInt(0) // píxeles por metro Y
            buffer.putInt(0) // colores usados
            buffer.putInt(0) // colores importantes

            // Datos de píxeles: de abajo hacia arriba, BGR, fila rellenada
            for (y in height - 1 downTo 0) {
                for (x in 0 until width) {
                    val argb = pixels[y * width + x]
                    buffer.put(argb.toByte()) // B
                    buffer.put((argb shr 8).toByte()) // G
                    buffer.put((argb shr 16).toByte()) // R
                }
                repeat(rowPadding) { buffer.put(0) }
            }

            return buffer.array()
        }

        private fun resolveTargetFormat(targetType: ConversionType): TargetFormat =
            when (targetType) {
                ConversionType.IMAGE_TO_JPG -> TargetFormat(false, Bitmap.CompressFormat.JPEG, "jpg", 90)
                ConversionType.IMAGE_TO_PNG -> TargetFormat(false, Bitmap.CompressFormat.PNG, "png", 100)
                ConversionType.IMAGE_TO_WEBP -> TargetFormat(false, webpFormat(), "webp", 90)
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
                // Android no tiene codificador BMP real (Bitmap.CompressFormat no
                // lo incluye) -- esto antes escribía bytes PNG con extensión
                // ".bmp", que una herramienta externa que valide el formato por
                // contenido real (no por extensión) no reconoce como BMP válido.
                // compressFormat/quality quedan sin uso real para BMP (ver
                // writeImage(), que llama a bitmapToBmp() en su lugar).
                ConversionType.IMAGE_TO_BMP -> TargetFormat(true, Bitmap.CompressFormat.PNG, "bmp", 100)
                else -> TargetFormat(false, Bitmap.CompressFormat.JPEG, "jpg", 90)
            }

        private fun writeImage(
            out: java.io.OutputStream,
            bitmap: Bitmap,
            target: TargetFormat,
        ) {
            if (target.isBmp) {
                out.write(bitmapToBmp(bitmap))
            } else {
                bitmap.compress(target.compressFormat, target.quality, out)
            }
        }

        private fun flattenOnWhite(source: Bitmap): Bitmap {
            val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(flattened)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(source, 0f, 0f, null)
            return flattened
        }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        // WEBP_LOSSLESS/WEBP_LOSSY requieren API 30+; minSdk de la app es 26.
        @Suppress("DEPRECATION")
        private fun webpFormat(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP
            }
    }
