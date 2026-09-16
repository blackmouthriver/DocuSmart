package com.docsmart.features.study.domain

import android.content.Context
import com.docsmart.core.data.db.NoteEntity
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RF-STU-08/backlog UX #51: exportar notas de Modo Estudio como texto
 * plano, PDF o Word (.docx), para compartirlas fuera de la app -- mismo
 * patrón de nombre de archivo (`DocuSmart_<algo>_<timestamp>`) y ubicación
 * (`filesDir/<carpeta propia>`) ya usado por Conversión/Herramientas PDF.
 * Trabaja sobre `NoteEntity` (Room) -- `createdAt` (epoch millis) se
 * formatea recién acá, no se persiste un string ya formateado.
 */
object StudyNotesExporter {

    private const val EXPORT_DIR_NAME = "study_exports"
    private const val SEPARATOR = "────────────────────────"

    private val displayDateFormatter
        get() = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())

    private fun displayDate(createdAt: Long) = displayDateFormatter.format(Date(createdAt))

    internal fun buildPlainText(notes: List<NoteEntity>): String =
        notes.joinToString("\n\n$SEPARATOR\n\n") { note ->
            "${note.title}\n${displayDate(note.createdAt)}\n\n${note.text}"
        }

    fun exportAsTextFile(context: Context, notes: List<NoteEntity>): File {
        val file = createOutputFile(context, "txt")
        file.writeText(buildPlainText(notes))
        return file
    }

    fun exportAsPdfFile(context: Context, notes: List<NoteEntity>): File {
        val file = createOutputFile(context, "pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        notes.forEach { note ->
            document.add(Paragraph(note.title).setBold().setFontSize(14f))
            document.add(
                Paragraph(displayDate(note.createdAt))
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

    // Backlog UX #51 (AC2): un párrafo en negrita+tamaño mayor para el
    // título y uno gris pequeño para la fecha -- mismo criterio visual que
    // la versión PDF de arriba, con el equivalente real de Apache POI
    // (`XWPFRun.setBold`/`setFontSize`/`setColor`, ya usado en
    // `WordToPdfUseCase`/`PdfToWordUseCase` del proyecto).
    fun exportAsWordFile(context: Context, notes: List<NoteEntity>): File {
        val file = createOutputFile(context, "docx")
        XWPFDocument().use { docx ->
            notes.forEachIndexed { index, note ->
                val titlePara = docx.createParagraph()
                val titleRun = titlePara.createRun()
                titleRun.isBold = true
                titleRun.fontSize = 14
                titleRun.setText(note.title)

                val dateRun = docx.createParagraph().createRun()
                dateRun.fontSize = 9
                dateRun.setColor("808080")
                dateRun.setText(displayDate(note.createdAt))

                // Word no interpreta "\n" dentro de un run como salto de
                // línea visible -- cada línea del texto libre de la nota
                // necesita su propio run, con addBreak() al final de todos
                // menos el último (mismo patrón ya usado en
                // PdfToWordUseCase.buildDocx()).
                val bodyParagraph = docx.createParagraph()
                val lines = note.text.split("\n")
                lines.forEachIndexed { lineIndex, line ->
                    val bodyRun = bodyParagraph.createRun()
                    bodyRun.fontSize = 11
                    bodyRun.setText(line)
                    if (lineIndex < lines.lastIndex) bodyRun.addBreak()
                }

                if (index < notes.lastIndex) docx.createParagraph().createRun().addBreak()
            }
            FileOutputStream(file).use { out -> docx.write(out) }
        }
        return file
    }

    private fun createOutputFile(context: Context, extension: String): File {
        val dir = File(context.filesDir, EXPORT_DIR_NAME).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "DocuSmart_Notas_$timestamp.$extension")
    }
}
