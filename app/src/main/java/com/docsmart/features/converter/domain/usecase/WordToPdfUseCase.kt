package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class WordToPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(
            wordUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada,
                // #22, latente -- WORD_TO_PDF está oculto de la grilla hoy):
                // outputFile era un `val` dentro del try -- el catch de abajo ni
                // siquiera podía referenciarlo para borrarlo si algo lanzaba
                // después de que PdfWriter(outputFile) ya creó el archivo en
                // disco, mismo patrón ya corregido en Herramientas PDF
                // (hallazgos #25-27) y en el resto del Convertidor.
                var outputFile: File? = null
                try {
                    // Hallazgo real de la auditoría general 2026-09-17/18 (décima
                    // ronda, Alta -- C1): ver el mismo hallazgo en
                    // WordToTextUseCase.kt/ExcelToHtmlUseCase.kt. Se chequea antes
                    // de crear outputFile para no dejar un PDF vacío huérfano.
                    if (isPasswordProtectedOfficeUri(context, wordUri)) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_password_protected),
                        )
                    }

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    outputFile = File(outputDir, "$baseName.pdf")

                    context.contentResolver.openInputStream(wordUri)?.use { rawInput ->
                        // RF-CONV-07: detecta OOXML (.docx) vs OLE2 (.doc) por firma
                        // binaria antes de decidir con qué API de POI leer -- ver
                        // WordFormatDetection.kt.
                        val (format, input) = detectWordFormat(rawInput)
                        val writer = PdfWriter(outputFile!!)
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
                    } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_word))

                    ConversionResult.Success(
                        outputFile = outputFile!!,
                        pageCount = 1,
                        fileSizeKb = (outputFile!!.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt.
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error convirtiendo Word a PDF")
                    outputFile?.delete()
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception, así que el
                    // catch de arriba nunca la atrapaba -- un .docx/.doc grande
                    // podía agotar la memoria a mitad de camino y dejar el .pdf
                    // parcial huérfano (PdfWriter ya lo había creado en disco).
                    Timber.e(e, "WordToPdfUseCase: sin memoria convirtiendo el documento")
                    outputFile?.delete()
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
                    )
                }
            }

        // Extraído de invoke() -- además de mantener la complejidad ciclomática
        // bajo el límite de detekt, agrupa toda la escritura del contenido en
        // un solo lugar cubierto por el .use{} de Document en el llamador.
        private fun writeWordContent(
            document: Document,
            format: WordFileFormat,
            input: InputStream,
        ) {
            if (format == WordFileFormat.OLE2) {
                extractLegacyDocBlocks(input).forEach { (text, _) ->
                    document.add(Paragraph(text))
                }
            } else {
                // ── Leer Word (.docx) con Apache POI ──
                XWPFDocument(input).use { wordDoc -> writeXwpfContent(document, wordDoc) }
            }
        }

        // Hallazgo real de la auditoría general 2026-09-17 (B4): párrafos y
        // tablas se procesaban en 2 pasadas separadas (`wordDoc.paragraphs`
        // completo, después `wordDoc.tables` completo) -- un documento con una
        // tabla en medio de dos párrafos ("Intro" → tabla → "Conclusión")
        // salía como "Intro / Conclusión / [tabla]", con la tabla siempre al
        // final sin importar dónde estuviera en el original. `bodyElements`
        // de Apache POI ya devuelve párrafos y tablas intercalados en el orden
        // real del documento.
        private fun writeXwpfContent(
            document: Document,
            wordDoc: XWPFDocument,
        ) {
            wordDoc.bodyElements.forEach { element ->
                when (element) {
                    is XWPFParagraph -> writeXwpfParagraph(document, element)
                    is XWPFTable -> writeXwpfTable(document, element)
                    else -> Unit
                }
            }
        }

        private fun writeXwpfParagraph(
            document: Document,
            paragraph: XWPFParagraph,
        ) {
            if (paragraph.text.isNotBlank()) document.add(Paragraph(paragraph.text))
        }

        private fun writeXwpfTable(
            document: Document,
            table: XWPFTable,
        ) {
            document.add(Paragraph(""))
            table.rows.forEach { row ->
                val rowText = row.tableCells.joinToString(" | ") { it.text }
                if (rowText.isNotBlank()) document.add(Paragraph(rowText))
            }
        }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
