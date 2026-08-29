package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
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
                    val wordDoc = XWPFDocument(input)

                    wordDoc.paragraphs.forEach { para ->
                        if (para.text.isNotBlank()) sb.appendLine(para.text)
                    }
                    wordDoc.tables.forEach { table ->
                        sb.appendLine()
                        table.rows.forEach { row ->
                            val rowText = row.tableCells.joinToString(" | ") { it.text }
                            if (rowText.isNotBlank()) sb.appendLine(rowText)
                        }
                    }
                    pageCount = wordDoc.paragraphs.size
                    wordDoc.close()
                }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Word")

            val text = sb.toString().trim()
            if (text.isBlank()) {
                return@withContext ConversionResult.Error("El documento no contiene texto extraíble.")
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
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
