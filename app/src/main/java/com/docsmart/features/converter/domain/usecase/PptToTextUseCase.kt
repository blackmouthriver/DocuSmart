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

class PptToTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pptUri  : Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            // Hallazgo real #38: ConversionType declara .ppt (OLE2, pre-
            // Office 2007) como origen soportado, pero este parser solo
            // entiende el ZIP interno de .pptx -- sin este chequeo, un
            // .ppt real fallaba con "sin texto" en vez de avisar que el
            // formato en sí no está soportado.
            if (isLegacyOle2Uri(context, pptUri)) {
                // Hallazgo real de la auditoría general 2026-09-17/18
                // (décima ronda, Alta -- C1): ver el mismo hallazgo en
                // ExcelToHtmlUseCase.kt.
                if (isPasswordProtectedOfficeUri(context, pptUri)) {
                    return@withContext ConversionResult.Error(
                        context.getString(R.string.converter_error_password_protected)
                    )
                }
                return@withContext ConversionResult.Error(
                    context.getString(R.string.converter_error_legacy_format_unsupported)
                )
            }
            val slideMap = extractSlideTexts(pptUri)
                ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_ppt))

            if (slideMap.isEmpty())
                return@withContext ConversionResult.Error(
                    context.getString(R.string.converter_error_empty_presentation)
                )

            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.txt")
            outputFile.writeText(buildOutputText(slideMap))

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = slideMap.size,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: CancellationException) {
            // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
            // en ConvertImageToPdfUseCase.kt.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "PptToTextUseCase: error")
            ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), e.message ?: "")
            )
        } catch (e: OutOfMemoryError) {
            // Hallazgo real de la auditoría general 2026-09-17 (quinta
            // pasada): OutOfMemoryError no hereda de Exception, así que el
            // catch de arriba nunca la atrapaba con un .pptx grande.
            Timber.e(e, "PptToTextUseCase: sin memoria convirtiendo el documento")
            ConversionResult.Error(
                String.format(
                    context.getString(R.string.converter_error_generic_format),
                    context.getString(R.string.converter_error_unknown)
                )
            )
        }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (B5): envolvía
    // directamente el InputStream del content resolver en ZipInputStream --
    // mismo bug real ya diagnosticado y corregido en PptToPdfUseCase
    // (testers 2026-09-11, "La presentación no contiene texto" con .pptx
    // que sí tenían texto real: ciertas URIs de Storage Access Framework
    // entregan un stream que ZipInputStream no puede leer directo, aunque
    // el mismo archivo desde disco sí funciona). Se lee el archivo completo
    // a memoria primero (ya con el límite de readBoundedBytes(), revisión
    // de seguridad 2026-09-16) y se envuelve en un ByteArrayInputStream
    // simple antes de pasarlo a ZipInputStream.
    /** Lee el .pptx como ZIP y extrae el texto de cada `ppt/slides/slideN.xml`. */
    private fun extractSlideTexts(pptUri: Uri): Map<Int, String>? {
        val bytes = context.contentResolver.openInputStream(pptUri)?.use { it.readBoundedBytes() }
            ?: return null
        val slideMap = mutableMapOf<Int, String>()
        java.io.ByteArrayInputStream(bytes).use { byteStream ->
            ZipInputStream(byteStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    addSlideTextIfMatch(zip, entry.name, slideMap)
                    entry = zip.nextEntry
                }
            }
        }
        return slideMap
    }

    private fun addSlideTextIfMatch(zip: ZipInputStream, entryName: String, slideMap: MutableMap<Int, String>) {
        if (!isSlideXmlEntry(entryName)) return
        val num  = slideNumberFromEntryName(entryName)
        val text = extractTextFromSlideXml(zip.readEntrySafely().toString(Charsets.UTF_8))
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

    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada,
    // #26, latente -- PPT_TO_TXT está oculto de la grilla hoy):
    // hardcodeado en español pese al idioma configurado -- este es el
    // CONTENIDO real del .txt que el usuario recibe.
    private fun buildOutputText(slideMap: Map<Int, String>): String {
        val sb = StringBuilder()
        slideMap.toSortedMap().forEach { (num, text) ->
            sb.appendLine(context.getString(R.string.converter_pdf_slide_label, num))
            sb.appendLine(text)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}