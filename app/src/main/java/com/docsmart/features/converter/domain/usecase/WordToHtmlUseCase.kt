package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

class WordToHtmlUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        wordUri : Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val paragraphs = mutableListOf<Pair<String, Boolean>>() // texto, esHeading

            context.contentResolver.openInputStream(wordUri)?.use { input ->
                val zip   = ZipInputStream(input)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml       = zip.readBytes().toString(Charsets.UTF_8)
                        val paraRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
                        paraRegex.findAll(xml).forEach { match ->
                            val paraXml   = match.value
                            val isHeading = paraXml.contains(Regex(
                                "w:val=\"(Heading|heading|Title|title|H[123456])"
                            ))
                            val text = paraXml
                                .replace(Regex("<w:rPr>.*?</w:rPr>", RegexOption.DOT_MATCHES_ALL), "")
                                .replace(Regex("<w:pPr>.*?</w:pPr>", RegexOption.DOT_MATCHES_ALL), "")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                                .replace(Regex("\\s+"), " ").trim()
                            if (text.isNotBlank()) paragraphs.add(Pair(text, isHeading))
                        }
                        break
                    }
                    entry = zip.nextEntry
                }
                zip.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Word")

            if (paragraphs.isEmpty())
                return@withContext ConversionResult.Error("El documento no contiene texto")

            val sb = StringBuilder()
            sb.appendLine("""<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Documento Word</title>
<style>
  body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.7; color: #222; }
  h1, h2 { color: #1D4ED8; border-bottom: 1px solid #dbeafe; padding-bottom: 6px; }
  p { margin: 8px 0; }
</style>
</head><body>""")

            paragraphs.forEach { (text, isHeading) ->
                val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                if (isHeading) sb.appendLine("<h2>$escaped</h2>")
                else           sb.appendLine("<p>$escaped</p>")
            }
            sb.appendLine("</body></html>")

            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.html")
            outputFile.writeText(sb.toString())

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "WordToHtmlUseCase: error")
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}