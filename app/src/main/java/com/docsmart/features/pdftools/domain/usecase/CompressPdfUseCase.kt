package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CompressPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CompressPdfUseCase"
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        quality: Int = 60,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Iniciando compresión — URI: $pdfUri, calidad: $quality")

            val cacheFile = copyUriToCache(pdfUri)
            if (cacheFile == null) {
                Timber.e("No se pudo copiar el archivo al cache")
                return@withContext PdfToolResult.Error(
                    "No se pudo leer el archivo. Verifica que sea un PDF válido."
                )
            }

            Timber.d("Cache: ${cacheFile.absolutePath}, tamaño: ${cacheFile.length()} bytes")

            if (cacheFile.length() == 0L) {
                return@withContext PdfToolResult.Error(
                    "El archivo PDF está vacío o corrupto."
                )
            }

            val originalSize = cacheFile.length()

            val fileDescriptor = ParcelFileDescriptor.open(
                cacheFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fileDescriptor)

            Timber.d("PDF abierto: ${renderer.pageCount} páginas")

            if (renderer.pageCount == 0) {
                renderer.close()
                fileDescriptor.close()
                return@withContext PdfToolResult.Error("El PDF no contiene páginas.")
            }

            val compressedDocument = PdfDocument()

            val scaleFactor = when {
                quality >= 80 -> 1.5f
                quality >= 60 -> 1.0f
                quality >= 40 -> 0.75f
                else          -> 0.5f
            }

            Timber.d("Factor de escala: $scaleFactor")

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                val width  = (page.width  * scaleFactor).toInt().coerceAtLeast(1)
                val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                Timber.d("Procesando página $i: ${width}x${height}")

                val bitmap = Bitmap.createBitmap(
                    width, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(
                    bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                val compressedBitmap = recompressBitmap(bitmap, quality)

                val pageInfo = PdfDocument.PageInfo.Builder(
                    width, height, i + 1
                ).create()

                val docPage = compressedDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(compressedBitmap, 0f, 0f, null)
                compressedDocument.finishPage(docPage)

                bitmap.recycle()
                compressedBitmap.recycle()
            }

            renderer.close()
            fileDescriptor.close()

            val name = outputFileName ?: "Compressed_q$quality"
            val outputFile = createOutputFile(name)

            Timber.d("Guardando en: ${outputFile.absolutePath}")

            FileOutputStream(outputFile).use { stream ->
                compressedDocument.writeTo(stream)
                stream.flush()
            }
            compressedDocument.close()

            Timber.d("Archivo guardado: ${outputFile.length()} bytes")

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(
                    "Error al generar el PDF comprimido."
                )
            }

            val newSize = outputFile.length()
            val reduction = if (originalSize > 0) {
                ((originalSize - newSize) * 100 / originalSize).toInt()
            } else 0

            Timber.d("Compresión exitosa: $originalSize → $newSize bytes ($reduction%)")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = "Reducido $reduction% — ${newSize / 1024} KB"
            )

        } catch (e: Exception) {
            Timber.e(e, "Error al comprimir: ${e.message}")
            PdfToolResult.Error(
                message = "Error al comprimir: ${e.message ?: "Error desconocido"}",
                cause = e
            )
        }
    }

    private fun recompressBitmap(bitmap: Bitmap, quality: Int): Bitmap {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
        } catch (e: Exception) {
            Timber.e("Error recomprimiendo bitmap: ${e.message}")
            bitmap
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(
                context.cacheDir,
                "compress_${System.currentTimeMillis()}.pdf"
            )
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Timber.e("No se pudo abrir el InputStream del URI")
                return null
            }
            inputStream.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Timber.d("Copiados $bytes bytes al cache")
                    if (bytes == 0L) return null
                }
            }
            file
        } catch (e: Exception) {
            Timber.e(e, "Error copiando URI al cache: ${e.message}")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "${name}_$timestamp.pdf")
    }
}