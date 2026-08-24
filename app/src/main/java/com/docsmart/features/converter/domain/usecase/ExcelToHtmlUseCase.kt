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

class ExcelToHtmlUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        excelUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            var sharedXml = ""
            var sheet1Xml = ""

            context.contentResolver.openInputStream(excelUri)?.use { input ->
                val zip   = ZipInputStream(input)
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "xl/sharedStrings.xml"     -> sharedXml = zip.readBytes().toString(Charsets.UTF_8)
                        "xl/worksheets/sheet1.xml" -> sheet1Xml = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
                zip.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Excel")

            // Parsear shared strings
            val sharedStrings = mutableListOf<String>()
            Regex("<t(?:\\s[^>]*)?>([^<]*)</t>").findAll(sharedXml).forEach { m ->
                sharedStrings.add(m.groupValues[1]
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim())
            }

            // Parsear filas
            val rows      = mutableListOf<List<String>>()
            val rowRegex  = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            val cellRegex = Regex("<c[^>]*>(.*?)</c>",     RegexOption.DOT_MATCHES_ALL)
            val vRegex    = Regex("<v>([^<]*)</v>")

            rowRegex.findAll(sheet1Xml).forEach { rowMatch ->
                val cells = mutableListOf<String>()
                cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                    val cellXml  = cellMatch.value
                    val typeAttr = Regex("""t="([^"]*)"""").find(cellXml)?.groupValues?.get(1) ?: ""
                    val vValue   = vRegex.find(cellXml)?.groupValues?.get(1)?.trim() ?: ""
                    val display  = when (typeAttr) {
                        "s"              -> { val idx = vValue.toIntOrNull() ?: -1; if (idx in sharedStrings.indices) sharedStrings[idx] else "" }
                        "b"              -> if (vValue == "1") "TRUE" else "FALSE"
                        "str","inlineStr"-> vValue
                        else             -> vValue
                    }
                    cells.add(display)
                }
                if (cells.any { it.isNotBlank() }) rows.add(cells)
            }

            if (rows.isEmpty())
                return@withContext ConversionResult.Error("La hoja de cálculo no tiene datos")

            // Generar HTML con tabla
            val sb = StringBuilder()
            sb.appendLine("""<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Hoja de cálculo</title>
<style>
  body { font-family: Arial, sans-serif; padding: 20px; }
  table { border-collapse: collapse; width: 100%; font-size: 13px; }
  th { background: #1D4ED8; color: white; padding: 8px 12px; text-align: left; }
  td { border: 1px solid #e2e8f0; padding: 6px 12px; }
  tr:nth-child(even) td { background: #f8fafc; }
</style>
</head><body><table>""")

            rows.forEachIndexed { index, row ->
                val tag = if (index == 0) "th" else "td"
                sb.append("<tr>")
                row.forEach { cell ->
                    val escaped = cell.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    sb.append("<$tag>$escaped</$tag>")
                }
                sb.appendLine("</tr>")
            }
            sb.appendLine("</table></body></html>")

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
            Timber.e(e, "ExcelToHtmlUseCase: error")
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}