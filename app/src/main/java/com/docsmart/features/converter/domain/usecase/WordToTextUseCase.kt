package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class WordToTextUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Cualquier fallo leyendo/parseando el .docx debe verse igual para quien
    // llama: un mensaje de error, no un crash de la conversión completa.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        wordUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Alta -- C1): un .docx protegido con contraseña de
            // Office tiene la MISMA firma OLE2 que un .doc legado real --
            // sin distinguirlos, extractLegacyDocBlocks() (HWPFDocument)
            // no encuentra el stream "WordDocument" esperado y termina
            // lanzando una excepción cruda de Apache POI, mostrada tal
            // cual al usuario. Se chequea ANTES de intentar leer.
            if (isPasswordProtectedOfficeUri(context, wordUri)) {
                return@withContext ConversionResult.Error(
                    context.getString(R.string.converter_error_password_protected)
                )
            }

            val (text, pageCount) = readWordText(wordUri)
                ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_word))

            if (text.isBlank()) {
                return@withContext ConversionResult.Error(
                    context.getString(R.string.converter_error_empty_word_document)
                )
            }

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.txt")
            outputFile.writeText(text)

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = pageCount.coerceAtLeast(1),
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Word a texto")
            ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), e.message ?: "")
            )
        } catch (e: OutOfMemoryError) {
            // Hallazgo real de la auditoría general 2026-09-17 (quinta
            // pasada): OutOfMemoryError no hereda de Exception, así que el
            // catch de arriba nunca la atrapaba con un .docx/.doc grande.
            Timber.e(e, "WordToTextUseCase: sin memoria convirtiendo el documento")
            ConversionResult.Error(
                String.format(
                    context.getString(R.string.converter_error_generic_format),
                    context.getString(R.string.converter_error_unknown)
                )
            )
        }
    }

    // Extraído de invoke() -- además de mantener la complejidad ciclomática
    // bajo el límite de detekt, agrupa toda la lectura OLE2/XWPF en un solo
    // lugar (mismo patrón ya usado en WordToPdfUseCase/WordToHtmlUseCase).
    private fun readWordText(wordUri: Uri): Pair<String, Int>? {
        val sb = StringBuilder()
        var pageCount = 0

        context.contentResolver.openInputStream(wordUri)?.use { rawInput ->
            // RF-CONV-07: ver WordFormatDetection.kt.
            val (format, input) = detectWordFormat(rawInput)

            if (format == WordFileFormat.OLE2) {
                val blocks = extractLegacyDocBlocks(input)
                blocks.forEach { (text, _) -> sb.appendLine(text) }
                pageCount = blocks.size
            } else {
                pageCount = XWPFDocument(input).use { wordDoc -> appendXwpfText(sb, wordDoc) }
            }
        } ?: return null

        return sb.toString().trim() to pageCount
    }

    // Hallazgo real de la auditoría general 2026-09-17 (B4): mismo problema
    // que WordToPdfUseCase -- ver el comentario ahí. `bodyElements` conserva
    // el orden real de párrafos+tablas del documento.
    private fun appendXwpfText(sb: StringBuilder, wordDoc: XWPFDocument): Int {
        wordDoc.bodyElements.forEach { element ->
            when (element) {
                is XWPFParagraph -> appendXwpfParagraph(sb, element)
                is XWPFTable -> appendXwpfTable(sb, element)
                else -> Unit
            }
        }
        return wordDoc.paragraphs.size
    }

    private fun appendXwpfParagraph(sb: StringBuilder, paragraph: XWPFParagraph) {
        if (paragraph.text.isNotBlank()) sb.appendLine(paragraph.text)
    }

    private fun appendXwpfTable(sb: StringBuilder, table: XWPFTable) {
        sb.appendLine()
        table.rows.forEach { row ->
            val rowText = row.tableCells.joinToString(" | ") { it.text }
            if (rowText.isNotBlank()) sb.appendLine(rowText)
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
