package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class PdfToImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            // ── Copiar al cache ───────────────────────
            val cacheFile = File(context.cacheDir, "temp_convert.pdf")
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFiles = mutableListOf<File>()

            val fileDescriptor = ParcelFileDescriptor.open(
                cacheFile, ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fileDescriptor)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val outputFile = File(outputDir, "${baseName}_pagina${i + 1}.jpg")
                outputFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
                outputFiles.add(outputFile)
            }

            renderer.close()
            fileDescriptor.close()

            if (outputFiles.isEmpty()) {
                return@withContext ConversionResult.Error("No se pudieron extraer páginas")
            }

            // Retornar el primer archivo como resultado principal
            ConversionResult.Success(
                outputFile = outputFiles.first(),
                pageCount = outputFiles.size,
                fileSizeKb = outputFiles.sumOf { it.length() / 1024 }.toInt(),
                extraFiles = outputFiles.drop(1)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo PDF a imagen")
            ConversionResult.Error("Error: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}