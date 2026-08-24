package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class PdfToHtmlUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pdfUri  : Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = File(context.cacheDir, "pdftohtml_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            // Extraer todo el texto ANTES de cerrar
            val pdfDoc     = PdfDocument(PdfReader(cacheFile))
            val totalPages = pdfDoc.numberOfPages
            val pageTexts  = mutableListOf<Pair<Int, String>>()

            for (i in 1..totalPages) {
                val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)).trim()
                if (text.isNotBlank()) pageTexts.add(Pair(i, text))
            }
            pdfDoc.close() // ← cerrar DESPUÉS de extraer todo

            if (pageTexts.isEmpty())
                return@withContext ConversionResult.Error(
                    "El PDF no contiene texto extraíble."
                )

            // Generar HTML
            val sb = StringBuilder()
            sb.appendLine("""<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Documento PDF</title>
<style>
  body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #333; }
  .page { border-bottom: 2px solid #e0e0e0; padding-bottom: 24px; margin-bottom: 24px; }
  .page-num { color: #999; font-size: 12px; margin-bottom: 8px; }
  p { margin: 8px 0; }
</style>
</head><body>""")

            pageTexts.forEach { (pageNum, text) ->
                sb.appendLine("<div class=\"page\">")
                sb.appendLine("<div class=\"page-num\">Página $pageNum</div>")
                text.split("\n").forEach { line ->
                    val escaped = line.trim()
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    if (escaped.isNotBlank()) sb.appendLine("<p>$escaped</p>")
                }
                sb.appendLine("</div>")
            }
            sb.appendLine("</body></html>")

            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.html")
            outputFile.writeText(sb.toString())

            Timber.d("PdfToHtmlUseCase: html creado — ${outputFile.length() / 1024} KB")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = totalPages,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "PdfToHtmlUseCase: error — ${e.message}")
            ConversionResult.Error("Error al convertir: ${e.message}")
        } finally {
            cacheFile?.delete()
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}