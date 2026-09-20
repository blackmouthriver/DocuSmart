package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ExcelToHtmlUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(
            excelUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                try {
                    var sharedXml = ""
                    var workbookXml = ""
                    var relsXml = ""
                    val worksheetXmlByPath = mutableMapOf<String, String>()

                    // Hallazgo real #38: ConversionType declara .xls como origen
                    // soportado, pero este parser solo entiende el ZIP interno de
                    // .xlsx -- sin este chequeo, un .xls real fallaba con "hoja
                    // vacía" en vez de avisar que el formato en sí no está
                    // soportado.
                    if (isLegacyOle2Uri(context, excelUri)) {
                        // Hallazgo real de la auditoría general 2026-09-17/18
                        // (décima ronda, Alta -- C1): un .xlsx protegido con
                        // contraseña de Office tiene la MISMA firma OLE2 que un
                        // .xls legado real -- sin distinguirlos, el usuario veía
                        // "guardalo como .xlsx" sobre un archivo que YA es .xlsx.
                        if (isPasswordProtectedOfficeUri(context, excelUri)) {
                            return@withContext ConversionResult.Error(
                                context.getString(R.string.converter_error_password_protected),
                            )
                        }
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_legacy_format_unsupported),
                        )
                    }

                    context.contentResolver.openInputStream(excelUri)?.use { input ->
                        val zip = ZipInputStream(input)
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when {
                                entry.name == "xl/sharedStrings.xml" ->
                                    sharedXml = zip.readEntrySafely().toString(Charsets.UTF_8)
                                entry.name == "xl/workbook.xml" ->
                                    workbookXml = zip.readEntrySafely().toString(Charsets.UTF_8)
                                entry.name == "xl/_rels/workbook.xml.rels" ->
                                    relsXml = zip.readEntrySafely().toString(Charsets.UTF_8)
                                entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") ->
                                    worksheetXmlByPath[entry.name] = zip.readEntrySafely().toString(Charsets.UTF_8)
                            }
                            entry = zip.nextEntry
                        }
                        zip.close()
                    } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_excel))

                    val sheet1Xml = resolveFirstVisibleSheetXml(workbookXml, relsXml, worksheetXmlByPath)

                    // Parsear shared strings
                    val sharedStrings = mutableListOf<String>()
                    Regex("<t(?:\\s[^>]*)?>([^<]*)</t>").findAll(sharedXml).forEach { m ->
                        sharedStrings.add(
                            m.groupValues[1]
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .trim(),
                        )
                    }

                    // Parsear filas
                    val rows = mutableListOf<List<String>>()
                    val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                    val cellRegex = Regex("<c[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                    val vRegex = Regex("<v>([^<]*)</v>")

                    rowRegex.findAll(sheet1Xml).forEach { rowMatch ->
                        val cells = mutableListOf<String>()
                        cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                            val cellXml = cellMatch.value
                            val typeAttr = Regex("""t="([^"]*)"""").find(cellXml)?.groupValues?.get(1) ?: ""
                            val vValue =
                                vRegex
                                    .find(cellXml)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim() ?: ""
                            val display =
                                when (typeAttr) {
                                    "s" -> {
                                        val idx =
                                            vValue.toIntOrNull() ?: -1
                                        if (idx in sharedStrings.indices) sharedStrings[idx] else ""
                                    }
                                    "b" -> if (vValue == "1") "TRUE" else "FALSE"
                                    "str", "inlineStr" -> vValue
                                    else -> vValue
                                }
                            cells.add(display)
                        }
                        if (cells.any { it.isNotBlank() }) rows.add(cells)
                    }

                    if (rows.isEmpty()) {
                        return@withContext ConversionResult.Error(context.getString(R.string.converter_error_empty_spreadsheet))
                    }

                    // Hallazgo real de la revisión general 2026-09-16 (cuarta
                    // pasada, #26, latente -- EXCEL_TO_HTML está oculto de la
                    // grilla hoy): `lang="es"` y el título quedaban hardcodeados
                    // en español pese al idioma configurado -- este es el
                    // CONTENIDO real del HTML que el usuario recibe. `lang` usa
                    // el idioma activo de la app (no el del documento fuente,
                    // imposible de detectar acá).
                    val htmlLang = Locale.getDefault().language
                    val htmlTitle = context.getString(R.string.converter_html_title_excel)
                    // Generar HTML con tabla
                    val sb = StringBuilder()
                    sb.appendLine(
                        """<!DOCTYPE html>
<html lang="$htmlLang"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$htmlTitle</title>
<style>
  body { font-family: Arial, sans-serif; padding: 20px; }
  table { border-collapse: collapse; width: 100%; font-size: 13px; }
  th { background: #1D4ED8; color: white; padding: 8px 12px; text-align: left; }
  td { border: 1px solid #e2e8f0; padding: 6px 12px; }
  tr:nth-child(even) td { background: #f8fafc; }
</style>
</head><body><table>""",
                    )

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

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    val outputFile = File(outputDir, "$baseName.html")
                    outputFile.writeText(sb.toString())

                    ConversionResult.Success(
                        outputFile = outputFile,
                        pageCount = 1,
                        fileSizeKb = (outputFile.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt.
                    throw e
                } catch (e: Exception) {
                    Timber.e("ExcelToHtmlUseCase: error: ${e.javaClass.simpleName}")
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception, así que el
                    // catch de arriba nunca la atrapaba con un .xlsx grande.
                    Timber.e(e, "ExcelToHtmlUseCase: sin memoria convirtiendo el documento")
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
                    )
                }
            }

        // Hallazgo real de la revisión general 2026-09-16 (#37): "sheet1.xml"
        // no está garantizado por la spec OOXML como la primera hoja visible en
        // orden de pestañas -- ese orden real vive en <sheets> de workbook.xml,
        // y el nombre de archivo físico se resuelve vía workbook.xml.rels (los
        // r:id no son 1:1 con el número del archivo sheetN.xml, sobre todo
        // después de reordenar/borrar hojas en Excel, que no renombra los
        // archivos internos). Si algo de esto falla o falta, cae de vuelta a
        // sheet1.xml y, en último caso, a cualquier hoja encontrada.
        private fun resolveFirstVisibleSheetXml(
            workbookXml: String,
            relsXml: String,
            worksheetXmlByPath: Map<String, String>,
        ): String {
            val firstSheetRid = firstVisibleSheetRid(workbookXml)
            val target = firstSheetRid?.let { rid -> relationshipTargetFor(relsXml, rid) }
            val resolvedPath = target?.let { "xl/" + it.removePrefix("/xl/").removePrefix("xl/") }

            return resolvedPath?.let(worksheetXmlByPath::get)
                ?: worksheetXmlByPath["xl/worksheets/sheet1.xml"]
                ?: worksheetXmlByPath.values.firstOrNull()
                ?: ""
        }

        // Hallazgo real de la revisión de correctitud adversarial de este mismo
        // lote (2026-09-16): resolveFirstVisibleSheetXml() tomaba el primer
        // <sheet> del XML sin importar su atributo `state` -- un workbook con
        // una hoja oculta (state="hidden"/"veryHidden") antes que la primera
        // hoja visible (ej. una hoja "RawData" oculta seguida de "Reporte", la
        // que el usuario ve como primera pestaña en Excel) convertía la hoja
        // oculta en vez de la que el usuario realmente ve primero. La ausencia
        // del atributo `state` significa visible (spec OOXML).
        private fun firstVisibleSheetRid(workbookXml: String): String? =
            Regex("<sheet\\s[^>]*>")
                .findAll(workbookXml)
                .firstOrNull { tag ->
                    !tag.value.contains("state=\"hidden\"") && !tag.value.contains("state=\"veryHidden\"")
                }?.let { tag -> Regex("r:id=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1) }

        // Id y Target pueden aparecer en cualquier orden dentro de <Relationship
        // .../> -- se prueban ambos órdenes en vez de asumir uno solo.
        private fun relationshipTargetFor(
            relsXml: String,
            rid: String,
        ): String? {
            val ridPattern = Regex.escape(rid)
            val idThenTarget = Regex("<Relationship\\s[^>]*Id=\"$ridPattern\"[^>]*Target=\"([^\"]+)\"")
            val targetThenId = Regex("<Relationship\\s[^>]*Target=\"([^\"]+)\"[^>]*Id=\"$ridPattern\"")
            return idThenTarget.find(relsXml)?.groupValues?.get(1)
                ?: targetThenId.find(relsXml)?.groupValues?.get(1)
        }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
