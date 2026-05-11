package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.pdftools.domain.model.PdfToolResult
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

class RotatePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RotatePdfUseCase"
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        degrees: Int = 90,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        try {
            val cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(
                    "No se pudo leer el PDF. Verifica que sea un archivo válido."
                )

            val fileDescriptor   = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer         = PdfRenderer(fileDescriptor)
            val rotatedDocument  = PdfDocument()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                // Guardar dimensiones ANTES de cerrar la página
                val pageWidth  = page.width * 2
                val pageHeight = page.height * 2

                val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(
                    rotatedBitmap.width, rotatedBitmap.height, i + 1
                ).create()

                val docPage = rotatedDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(rotatedBitmap, 0f, 0f, null)
                rotatedDocument.finishPage(docPage)
                rotatedBitmap.recycle()
            }

            renderer.close()
            fileDescriptor.close()

            val name       = outputFileName ?: "Rotated_${degrees}deg"
            val outputFile = createOutputFile(name)

            FileOutputStream(outputFile).use { rotatedDocument.writeTo(it) }
            rotatedDocument.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error("Error al generar el PDF rotado.")
            }

            Timber.d("$TAG: rotación exitosa ${degrees}° — ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = "PDF rotado ${degrees}° correctamente"
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al rotar PDF")
            PdfToolResult.Error("Error al rotar PDF: ${e.message ?: "Error desconocido"}", e)
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "rotate_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
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
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
