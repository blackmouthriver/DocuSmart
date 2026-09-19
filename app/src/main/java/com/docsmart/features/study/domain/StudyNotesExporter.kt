package com.docsmart.features.study.domain

import android.content.Context
import android.graphics.BitmapFactory
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.XWPFDocument
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.itextpdf.layout.element.Image as PdfImage

/**
 * RF-STU-08/backlog UX #51: exportar notas de Modo Estudio como texto
 * plano, PDF o Word (.docx), para compartirlas fuera de la app -- mismo
 * patrón de nombre de archivo (`DocuSmart_<algo>_<timestamp>`) y ubicación
 * (`filesDir/<carpeta propia>`) ya usado por Conversión/Herramientas PDF.
 * Trabaja sobre `NoteWithImages` (Room) -- `createdAt` (epoch millis) se
 * formatea recién acá, no se persiste un string ya formateado.
 *
 * Hallazgo real de la auditoría general 2026-09-17: el AC1 de HU-51 exige
 * explícitamente "conserva el texto e imágenes adjuntas (HU-49) en el
 * mismo orden que la nota" -- antes esta clase trabajaba sobre
 * `NoteEntity` (sin `images`) y las imágenes desaparecían en silencio al
 * exportar. PDF/Word ahora sí las incluyen, en el orden de `position`;
 * texto plano las omite a propósito (un .txt no puede contener imágenes,
 * no es un bug de esta clase).
 */
object StudyNotesExporter {
    private const val EXPORT_DIR_NAME = "study_exports"
    private const val SEPARATOR = "────────────────────────"

    // A4 son ~595 puntos de ancho de página en iText7 -- se deja margen
    // para los márgenes por defecto del Document. 350pt en Word equivale
    // a poco menos de la mitad de una página carta, un tamaño legible sin
    // desbordar.
    private const val PDF_IMAGE_WIDTH_POINTS = 300f
    private const val WORD_IMAGE_WIDTH_POINTS = 350f

    private val displayDateFormatter
        get() = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())

    private fun displayDate(createdAt: Long) = displayDateFormatter.format(Date(createdAt))

    internal fun buildPlainText(notes: List<NoteEntity>): String =
        notes.joinToString("\n\n$SEPARATOR\n\n") { note ->
            "${note.title}\n${displayDate(note.createdAt)}\n\n${note.text}"
        }

    fun exportAsTextFile(
        context: Context,
        notes: List<NoteWithImages>,
    ): File {
        val file = createOutputFile(context, "txt")
        file.writeText(buildPlainText(notes.map { it.note }))
        return file
    }

    fun exportAsPdfFile(
        context: Context,
        notes: List<NoteWithImages>,
    ): File {
        val file = createOutputFile(context, "pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        notes.forEach { noteWithImages ->
            val note = noteWithImages.note
            document.add(Paragraph(note.title).setBold().setFontSize(14f))
            document.add(
                Paragraph(displayDate(note.createdAt))
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.GRAY),
            )
            document.add(Paragraph(note.text).setFontSize(11f))
            addImagesToPdf(document, noteWithImages.images)
            document.add(
                Paragraph(SEPARATOR)
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER),
            )
        }
        document.close()
        return file
    }

    // Backlog UX #49/#51 (AC1): las imágenes adjuntas a la nota se agregan
    // en el mismo orden en que aparecen en el editor (`position`), cada
    // una acotada a un ancho razonable de página -- una imagen que no se
    // puede decodificar (archivo movido/corrupto) se salta en vez de
    // interrumpir la exportación del resto de la nota.
    @Suppress("TooGenericExceptionCaught")
    private fun addImagesToPdf(
        document: Document,
        images: List<NoteImageEntity>,
    ) {
        images.sortedBy { it.position }.forEach { image ->
            try {
                val imageData = ImageDataFactory.create(image.filePath)
                document.add(PdfImage(imageData).setWidth(PDF_IMAGE_WIDTH_POINTS).setAutoScaleHeight(true))
            } catch (e: Exception) {
                Timber.e(e, "StudyNotesExporter: no se pudo incluir la imagen ${image.filePath}")
            }
        }
    }

    // Backlog UX #51 (AC2): un párrafo en negrita+tamaño mayor para el
    // título y uno gris pequeño para la fecha -- mismo criterio visual que
    // la versión PDF de arriba, con el equivalente real de Apache POI
    // (`XWPFRun.setBold`/`setFontSize`/`setColor`, ya usado en
    // `WordToPdfUseCase`/`PdfToWordUseCase` del proyecto).
    fun exportAsWordFile(
        context: Context,
        notes: List<NoteWithImages>,
    ): File {
        val file = createOutputFile(context, "docx")
        XWPFDocument().use { docx ->
            notes.forEachIndexed { index, noteWithImages ->
                val note = noteWithImages.note
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

                addImagesToWord(docx, noteWithImages.images)

                if (index < notes.lastIndex) docx.createParagraph().createRun().addBreak()
            }
            FileOutputStream(file).use { out -> docx.write(out) }
        }
        return file
    }

    // Mismo criterio que addImagesToPdf(): orden por `position`, ancho
    // acotado, una imagen que falla al leerse/decodificar no aborta el
    // resto de la exportación. XWPFRun.addPicture() exige el ancho/alto en
    // EMUs y el tamaño real de la imagen (para calcular el alto
    // proporcional) -- se lee con ImageIO en vez de asumir un tamaño fijo.
    @Suppress("TooGenericExceptionCaught")
    private fun addImagesToWord(
        docx: XWPFDocument,
        images: List<NoteImageEntity>,
    ) {
        images.sortedBy { it.position }.forEach { image ->
            try {
                val file = File(image.filePath)
                // ImageIO no existe en Android -- se leen solo los bounds
                // reales con BitmapFactory (mismo patrón ya usado en el
                // proyecto para el logo del Creador de QR), sin decodificar
                // los píxeles completos solo para calcular el alto
                // proporcional.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@forEach
                val aspectRatio = bounds.outHeight.toFloat() / bounds.outWidth.toFloat()
                val widthEmu = Units.toEMU(WORD_IMAGE_WIDTH_POINTS.toDouble())
                val heightEmu = (widthEmu * aspectRatio).toInt()
                val run = docx.createParagraph().createRun()
                FileInputStream(file).use { stream ->
                    run.addPicture(stream, poiPictureType(bounds.outMimeType), file.name, widthEmu, heightEmu)
                }
            } catch (e: Exception) {
                Timber.e(e, "StudyNotesExporter: no se pudo incluir la imagen ${image.filePath}")
            }
        }
    }

    // Hallazgo real de la revisión adversarial de esta misma auditoría
    // (2026-09-17): NoteRepository.copyImageToNoteStorage() copia los
    // bytes crudos del proveedor SAF/galería y siempre nombra el archivo
    // con extensión .jpg (nombre, no contenido re-codificado) -- una
    // imagen PNG/WEBP real adjunta a una nota podía etiquetarse como JPEG
    // en el .docx, mostrando "imagen no se puede mostrar" en Word pese a
    // que el archivo era válido. Se usa el mimeType real que
    // BitmapFactory ya detecta por firma de bytes (no por extensión) al
    // leer los bounds, mismo criterio que ya usa ImageDataFactory del
    // lado PDF.
    private fun poiPictureType(mimeType: String?): Int =
        when (mimeType) {
            "image/png" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG
            "image/gif" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF
            "image/bmp" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_BMP
            else -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG
        }

    private fun createOutputFile(
        context: Context,
        extension: String,
    ): File {
        val dir = File(context.filesDir, EXPORT_DIR_NAME).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "DocuSmart_Notas_$timestamp.$extension")
    }
}
