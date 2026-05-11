package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
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

class WordToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        wordUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.pdf")

            context.contentResolver.openInputStream(wordUri)?.use { input ->
                // ── Leer Word con Apache POI ──────────
                val wordDoc = XWPFDocument(input)
                val writer = PdfWriter(outputFile)
                val pdfDoc = PdfDocument(writer)
                val document = Document(pdfDoc)

                // ── Extraer párrafos y escribir en PDF ─
                wordDoc.paragraphs.forEach { para ->
                    val text = para.text
                    if (text.isNotBlank()) {
                        val paragraph = Paragraph(text)
                        document.add(paragraph)
                    }
                }

                // ── Extraer tablas ────────────────────
                wordDoc.tables.forEach { table ->
                    document.add(Paragraph(""))
                    table.rows.forEach { row ->
                        val rowText = row.tableCells.joinToString(" | ") { it.text }
                        if (rowText.isNotBlank()) {
                            document.add(Paragraph(rowText))
                        }
                    }
                }

                document.close()
                wordDoc.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Word")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Word a PDF")
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}