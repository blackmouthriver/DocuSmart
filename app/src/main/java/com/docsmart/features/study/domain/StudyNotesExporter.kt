package com.docsmart.features.study.domain

import android.content.Context
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RF-STU-08: exportar las notas de Modo Estudio como texto plano o PDF, para
 * compartirlas fuera de la app -- mismo patrón de nombre de archivo
 * (`DocuSmart_<algo>_<timestamp>`) y ubicación (`filesDir/<carpeta propia>`)
 * ya usado por Conversión/Herramientas PDF.
 */
object StudyNotesExporter {

    private const val EXPORT_DIR_NAME = "study_exports"
    private const val SEPARATOR = "────────────────────────"

    internal fun buildPlainText(notes: List<SavedNote>): String =
        notes.joinToString("\n\n$SEPARATOR\n\n") { note ->
            "${note.title}\n${note.dateTime}\n\n${note.text}"
        }

    fun exportAsTextFile(context: Context, notes: List<SavedNote>): File {
        val file = createOutputFile(context, "txt")
        file.writeText(buildPlainText(notes))
        return file
    }

    fun exportAsPdfFile(context: Context, notes: List<SavedNote>): File {
        val file = createOutputFile(context, "pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        notes.forEach { note ->
            document.add(Paragraph(note.title).setBold().setFontSize(14f))
            document.add(
                Paragraph(note.dateTime)
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.GRAY)
            )
            document.add(Paragraph(note.text).setFontSize(11f))
            document.add(
                Paragraph(SEPARATOR)
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
            )
        }
        document.close()
        return file
    }

    private fun createOutputFile(context: Context, extension: String): File {
        val dir = File(context.filesDir, EXPORT_DIR_NAME).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "DocuSmart_Notas_$timestamp.$extension")
    }
}
