package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class PdfToTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            // ── Copiar al cache ───────────────────────
            // Nombre único por llamada (antes fijo: "temp_text.pdf") -- RF-CONV-08
            // puede invocar este use case varias veces en el mismo lote.
            val cacheFile = File.createTempFile("temp_text", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            // ── Extraer texto con iText7 ──────────────
            val sb = StringBuilder()
            val pdfDoc = PdfDocument(PdfReader(cacheFile))

            val pageCount = pdfDoc.numberOfPages
            for (i in 1..pageCount) {
                val pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i))
                sb.appendLine("=== Página $i ===")
                sb.appendLine(pageText)
                sb.appendLine()
            }
            pdfDoc.close()

            val text = sb.toString().trim()
            if (text.isBlank()) {
                return@withContext ConversionResult.Error(
                    "El PDF no contiene texto extraíble. Puede ser un PDF escaneado."
                )
            }

            // ── Guardar como TXT ──────────────────────
            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.txt")
            outputFile.writeText(text)

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = pageCount,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extrayendo texto del PDF")
            ConversionResult.Error("Error: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}