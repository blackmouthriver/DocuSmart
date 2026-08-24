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
            val slideMap = extractSlideTexts(pptUri)
                ?: return@withContext ConversionResult.Error("No se pudo leer el archivo PowerPoint")

            if (slideMap.isEmpty())
                return@withContext ConversionResult.Error("La presentación no contiene texto")

            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.txt")
            outputFile.writeText(buildOutputText(slideMap))

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

    /** Lee el .pptx como ZIP y extrae el texto de cada `ppt/slides/slideN.xml`. */
    private fun extractSlideTexts(pptUri: Uri): Map<Int, String>? {
        val slideMap = mutableMapOf<Int, String>()
        context.contentResolver.openInputStream(pptUri)?.use { input ->
            val zip   = ZipInputStream(input)
            var entry = zip.nextEntry
            while (entry != null) {
                addSlideTextIfMatch(zip, entry.name, slideMap)
                entry = zip.nextEntry
            }
            zip.close()
        } ?: return null
        return slideMap
    }

    private fun addSlideTextIfMatch(zip: ZipInputStream, entryName: String, slideMap: MutableMap<Int, String>) {
        if (!isSlideXmlEntry(entryName)) return
        val num  = slideNumberFromEntryName(entryName)
        val text = extractTextFromSlideXml(zip.readBytes().toString(Charsets.UTF_8))
        if (text.isNotBlank()) slideMap[num] = text
    }

    private fun isSlideXmlEntry(entryName: String) =
        entryName.startsWith("ppt/slides/slide") &&
            entryName.endsWith(".xml") &&
            !entryName.contains("_rels")

    private fun slideNumberFromEntryName(entryName: String) = entryName
        .removePrefix("ppt/slides/slide")
        .removeSuffix(".xml")
        .toIntOrNull() ?: 0

    /** Extrae el texto plano de los párrafos `<a:p>` de una diapositiva OOXML. */
    private fun extractTextFromSlideXml(xml: String): String =
        Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .mapNotNull { m ->
                val t = m.value
                    .replace(Regex("<a:rPr[^/]*/?>|</a:rPr>"), "")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace(Regex("\\s+"), " ").trim()
                if (t.isNotBlank()) t else null
            }.joinToString("\n")

    private fun buildOutputText(slideMap: Map<Int, String>): String {
        val sb = StringBuilder()
        slideMap.toSortedMap().forEach { (num, text) ->
            sb.appendLine("=== Diapositiva $num ===")
            sb.appendLine(text)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}