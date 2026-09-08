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
        // Bug real corregido 2026-09-08: el archivo de caché tenía un
        // nombre fijo ("temp_convert.pdf", no único como en el resto de los
        // use cases de esta app) y nunca se borraba -- quedaba en disco
        // después de cada conversión, y dos conversiones de este tipo a la
        // vez competían por el mismo archivo. `renderer`/`fileDescriptor`
        // tampoco se cerraban si algo fallaba a mitad del loop (ej.
        // `OutOfMemoryError` al renderizar una página a 2x, que ni siquiera
        // hereda de `Exception` y no la atrapa el catch de más abajo).
        var cacheFile: File? = null
        try {
            // ── Copiar al cache ───────────────────────
            cacheFile = File(context.cacheDir, "temp_convert_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFiles = mutableListOf<File>()

            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
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
                }
            }

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
        } finally {
            cacheFile?.delete()
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}