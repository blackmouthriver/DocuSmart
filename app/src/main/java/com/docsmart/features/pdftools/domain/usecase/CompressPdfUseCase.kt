package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        pdfUri        : Uri,
        quality       : Int = 60,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            Timber.d("$TAG: iniciando compresión — calidad: $quality")

            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(
                    "No se pudo leer el archivo. Verifica que sea un PDF válido."
                )

            if (cacheFile.length() == 0L)
                return@withContext PdfToolResult.Error("El archivo PDF está vacío o corrupto.")

            val originalSize = cacheFile.length()
            Timber.d("$TAG: tamaño original = ${originalSize / 1024} KB")

            val fileDescriptor = ParcelFileDescriptor.open(
                cacheFile, ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fileDescriptor)

            if (renderer.pageCount == 0) {
                renderer.close()
                fileDescriptor.close()
                return@withContext PdfToolResult.Error("El PDF no contiene páginas.")
            }

            Timber.d("$TAG: ${renderer.pageCount} páginas a comprimir")

            val pdfDocument = renderAndCompressPages(renderer, scaleFactorFor(quality), quality)

            renderer.close()
            fileDescriptor.close()

            val name       = outputFileName ?: "Compressed_q$quality"
            val outputFile = createOutputFile(name)

            FileOutputStream(outputFile).use { stream ->
                pdfDocument.writeTo(stream)
                stream.flush()
            }
            pdfDocument.close()

            if (outputFile.length() == 0L)
                return@withContext PdfToolResult.Error("Error al generar el PDF comprimido.")

            val newSize    = outputFile.length()
            val originalKb = originalSize / 1024
            val newKb      = newSize / 1024
            val reduction  = if (originalSize > 0)
                ((originalSize - newSize) * 100 / originalSize).toInt()
            else 0

            Timber.d("$TAG: $originalKb KB → $newKb KB ($reduction%)")

            val keepOriginal = newSize >= originalSize
            val finalFile = if (keepOriginal) {
                Timber.d("$TAG: comprimido mayor que original — usando original")
                val originalOutput = createOutputFile("${name}_optimizado")
                cacheFile!!.copyTo(originalOutput, overwrite = true)
                originalOutput
            } else {
                outputFile
            }

            PdfToolResult.Success(
                outputFile = finalFile,
                message    = resultMessage(keepOriginal, originalKb, finalFile.length() / 1024, reduction)
            )

        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al comprimir: ${e.message}")
            PdfToolResult.Error(
                message = "Error al comprimir: ${e.message ?: "Error desconocido"}",
                cause   = e
            )
        } finally {
            cacheFile?.delete()
        }
    }

    private fun scaleFactorFor(quality: Int) = when {
        quality >= 80 -> 1.5f
        quality >= 60 -> 1.2f
        quality >= 40 -> 0.9f
        else          -> 0.6f
    }

    private fun resultMessage(keepOriginal: Boolean, originalKb: Long, finalKb: Long, reduction: Int) =
        if (keepOriginal)
            "El PDF ya está optimizado · Tamaño: $originalKb KB"
        else
            "Antes: $originalKb KB → Después: $finalKb KB · Reducción: $reduction%"

    private fun renderAndCompressPages(
        renderer   : PdfRenderer,
        scaleFactor: Float,
        quality    : Int
    ): android.graphics.pdf.PdfDocument {
        val pdfDocument = android.graphics.pdf.PdfDocument()

        for (i in 0 until renderer.pageCount) {
            val page   = renderer.openPage(i)
            val width  = (page.width  * scaleFactor).toInt().coerceAtLeast(1)
            val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val compressed = recompressBitmap(bitmap, quality)

            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo
                .Builder(width, height, i + 1).create()
            val docPage  = pdfDocument.startPage(pageInfo)
            docPage.canvas.drawBitmap(compressed, 0f, 0f, null)
            pdfDocument.finishPage(docPage)

            bitmap.recycle()
            if (compressed !== bitmap) compressed.recycle()

            Timber.d("$TAG: página ${i + 1} procesada")
        }

        return pdfDocument
    }

    private fun recompressBitmap(bitmap: Bitmap, quality: Int): Bitmap {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
        } catch (e: Exception) {
            Timber.e("$TAG: error recomprimiendo: ${e.message}")
            bitmap
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "compress_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    Timber.d("$TAG: copiados $bytes bytes al cache")
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir       = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}