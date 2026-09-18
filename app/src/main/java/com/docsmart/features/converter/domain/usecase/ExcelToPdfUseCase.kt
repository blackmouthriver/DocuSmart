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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.EncryptedDocumentException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.WorkbookFactory
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExcelToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        excelUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada,
        // #22, latente -- EXCEL_TO_PDF está oculto de la grilla hoy):
        // outputFile era un `val` dentro del try -- el catch de abajo ni
        // siquiera podía referenciarlo para borrarlo si algo lanzaba
        // después de que PdfWriter(outputFile) ya creó el archivo en
        // disco, mismo patrón ya corregido en Herramientas PDF
        // (hallazgos #25-27) y en el resto del Convertidor.
        var outputFile: File? = null
        try {
            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            outputFile = File(outputDir, "$baseName.pdf")

            // Bug real encontrado 2026-09-14 (repaso general): document.close()/
            // workbook.close() manuales solo se alcanzaban en el camino feliz
            // -- una excepción al leer una celda o escribir un párrafo dejaba
            // el PdfDocument/Document y el Workbook de POI sin cerrar, con el
            // FileOutputStream de outputFile abierto. .use{} anidado garantiza
            // el cierre de ambos pase lo que pase.
            // Extraído a writeWorkbookToPdf() -- además de mantener la
            // complejidad ciclomática de invoke() bajo el límite de detekt
            // (creció al agregar el catch de EncryptedDocumentException,
            // hallazgo C1 de la décima ronda), agrupa toda la escritura en
            // un solo lugar.
            val wrote = writeWorkbookToPdf(excelUri, outputFile!!)
            if (!wrote) {
                return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_excel))
            }

            ConversionResult.Success(
                outputFile = outputFile!!,
                pageCount = 1,
                fileSizeKb = (outputFile!!.length() / 1024).toInt()
            )
        } catch (e: EncryptedDocumentException) {
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Alta -- C1): ver el mismo hallazgo en
            // ExcelToCsvUseCase.kt.
            Timber.w(e, "ExcelToPdfUseCase: archivo protegido con contraseña")
            outputFile?.delete()
            ConversionResult.Error(context.getString(R.string.converter_error_password_protected))
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Excel a PDF")
            outputFile?.delete()
            ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), e.message ?: "")
            )
        } catch (e: OutOfMemoryError) {
            // Hallazgo real de la auditoría general 2026-09-17 (quinta
            // pasada): OutOfMemoryError no hereda de Exception -- un .xlsx
            // grande podía agotar la memoria a mitad de camino y dejar el
            // .pdf parcial huérfano (PdfWriter ya lo había creado en disco).
            Timber.e(e, "ExcelToPdfUseCase: sin memoria convirtiendo el documento")
            outputFile?.delete()
            ConversionResult.Error(
                String.format(
                    context.getString(R.string.converter_error_generic_format),
                    context.getString(R.string.converter_error_unknown)
                )
            )
        }
    }

    private fun writeWorkbookToPdf(excelUri: Uri, outputFile: File): Boolean {
        var wrote = false
        context.contentResolver.openInputStream(excelUri)?.use { input ->
            WorkbookFactory.create(input).use { workbook -> writeWorkbookContent(workbook, outputFile) }
            wrote = true
        }
        return wrote
    }

    // Extraído de writeWorkbookToPdf() -- detekt: NestedBlockDepth, disparado
    // al agrupar toda la escritura para bajar la complejidad ciclomática de
    // invoke() (hallazgo C1, décima ronda).
    private fun writeWorkbookContent(workbook: org.apache.poi.ss.usermodel.Workbook, outputFile: File) {
        val pdfDoc = PdfDocument(PdfWriter(outputFile))
        // Hallazgo real de la revisión general 2026-09-16: cell.toString()
        // en una celda de fórmula devuelve el texto de la fórmula, no el
        // resultado calculado -- mismo bug que en ExcelToCsvUseCase.
        val evaluator     = workbook.creationHelper.createFormulaEvaluator()
        val dataFormatter = DataFormatter()
        Document(pdfDoc).use { document ->
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                appendSheetToDocument(document, workbook.getSheetAt(sheetIndex), dataFormatter, evaluator)
            }
        }
    }

    private fun appendSheetToDocument(
        document: Document,
        sheet: org.apache.poi.ss.usermodel.Sheet,
        dataFormatter: DataFormatter,
        evaluator: FormulaEvaluator
    ) {
        document.add(Paragraph("=== ${sheet.sheetName} ==="))
        sheet.forEach { row ->
            val rowText = buildString {
                row.forEach { cell ->
                    append(formatCellSafely(cell, dataFormatter, evaluator))
                    append("\t")
                }
            }.trim()
            if (rowText.isNotBlank()) {
                document.add(Paragraph(rowText))
            }
        }
        document.add(Paragraph(""))
    }

    // Hallazgo real de la revisión de corrección 2026-09-16: evaluator.evaluate()
    // puede lanzar (referencia circular, función no soportada por POI,
    // referencia a otro libro) para UNA celda puntual -- sin este try por
    // celda, esa excepción no la atrapaba nada más que el catch genérico de
    // invoke(), abortando TODA la conversión (regresión frente al
    // cell.toString() anterior, que nunca lanzaba). Mismo criterio que
    // extractExcelSheets() en ViewerScreen.kt.
    private fun formatCellSafely(cell: Cell, dataFormatter: DataFormatter, evaluator: FormulaEvaluator): String =
        try {
            dataFormatter.formatCellValue(cell, evaluator).trim()
        } catch (e: Exception) {
            Timber.w(e, "formatCellSafely: no se pudo formatear una celda, se usa el texto crudo")
            cell.toString().trim()
        }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}