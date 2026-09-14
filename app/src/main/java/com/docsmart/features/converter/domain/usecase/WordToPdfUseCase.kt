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
import java.io.InputStream
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

            context.contentResolver.openInputStream(wordUri)?.use { rawInput ->
                // RF-CONV-07: detecta OOXML (.docx) vs OLE2 (.doc) por firma
                // binaria antes de decidir con qué API de POI leer -- ver
                // WordFormatDetection.kt.
                val (format, input) = detectWordFormat(rawInput)
                val writer = PdfWriter(outputFile)
                val pdfDoc = PdfDocument(writer)

                // Bug real encontrado 2026-09-14 (repaso general):
                // document.close()/wordDoc.close() manuales solo se
                // alcanzaban en el camino feliz -- una excepción al extraer
                // texto de un .docx/.doc con formato inesperado dejaba el
                // PdfDocument/Document (y el XWPFDocument de POI) sin
                // cerrar, con el FileOutputStream de outputFile abierto.
                // .use{} anidado garantiza el cierre de ambos pase lo que
                // pase.
                Document(pdfDoc).use { document -> writeWordContent(document, format, input) }
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

    // Extraído de invoke() -- además de mantener la complejidad ciclomática
    // bajo el límite de detekt, agrupa toda la escritura del contenido en
    // un solo lugar cubierto por el .use{} de Document en el llamador.
    private fun writeWordContent(document: Document, format: WordFileFormat, input: InputStream) {
        if (format == WordFileFormat.OLE2) {
            extractLegacyDocBlocks(input).forEach { (text, _) ->
                document.add(Paragraph(text))
            }
        } else {
            // ── Leer Word (.docx) con Apache POI ──
            XWPFDocument(input).use { wordDoc -> writeXwpfContent(document, wordDoc) }
        }
    }

    private fun writeXwpfContent(document: Document, wordDoc: XWPFDocument) {
        // ── Extraer párrafos y escribir en PDF ─
        wordDoc.paragraphs.forEach { para ->
            val text = para.text
            if (text.isNotBlank()) {
                document.add(Paragraph(text))
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
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}