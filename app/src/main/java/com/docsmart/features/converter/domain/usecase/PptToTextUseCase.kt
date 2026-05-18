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

class PptToTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pptUri  : Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val slideMap = mutableMapOf<Int, String>()

            context.contentResolver.openInputStream(pptUri)?.use { input ->
                val zip   = ZipInputStream(input)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") &&
                        entry.name.endsWith(".xml") &&
                        !entry.name.contains("_rels")
                    ) {
                        val num = entry.name
                            .removePrefix("ppt/slides/slide")
                            .removeSuffix(".xml")
                            .toIntOrNull() ?: 0
                        val xml  = zip.readBytes().toString(Charsets.UTF_8)
                        val text = Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(xml)
                            .mapNotNull { m ->
                                val t = m.value
                                    .replace(Regex("<a:rPr[^/]*/?>|</a:rPr>"), "")
                                    .replace(Regex("<[^>]+>"), "")
                                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                                    .replace(Regex("\\s+"), " ").trim()
                                if (t.isNotBlank()) t else null
                            }.joinToString("\n")
                        if (text.isNotBlank()) slideMap[num] = text
                    }
                    entry = zip.nextEntry
                }
                zip.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo PowerPoint")

            if (slideMap.isEmpty())
                return@withContext ConversionResult.Error("La presentación no contiene texto")

            val sb = StringBuilder()
            slideMap.toSortedMap().forEach { (num, text) ->
                sb.appendLine("=== Diapositiva $num ===")
                sb.appendLine(text)
                sb.appendLine()
            }

            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.txt")
            outputFile.writeText(sb.toString())

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = slideMap.size,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "PptToTextUseCase: error")
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}