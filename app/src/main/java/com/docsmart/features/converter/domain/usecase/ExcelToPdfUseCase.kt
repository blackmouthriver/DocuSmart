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
        try {
            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.pdf")

            // Bug real encontrado 2026-09-14 (repaso general): document.close()/
            // workbook.close() manuales solo se alcanzaban en el camino feliz
            // -- una excepción al leer una celda o escribir un párrafo dejaba
            // el PdfDocument/Document y el Workbook de POI sin cerrar, con el
            // FileOutputStream de outputFile abierto. .use{} anidado garantiza
            // el cierre de ambos pase lo que pase.
            context.contentResolver.openInputStream(excelUri)?.use { input ->
                WorkbookFactory.create(input).use { workbook ->
                    val writer = PdfWriter(outputFile)
                    val pdfDoc = PdfDocument(writer)
                    Document(pdfDoc).use { document ->
                        for (sheetIndex in 0 until workbook.numberOfSheets) {
                            val sheet = workbook.getSheetAt(sheetIndex)
                            document.add(Paragraph("=== ${sheet.sheetName} ==="))

                            sheet.forEach { row ->
                                val rowText = buildString {
                                    row.forEach { cell ->
                                        append(cell.toString().trim())
                                        append("\t")
                                    }
                                }.trim()
                                if (rowText.isNotBlank()) {
                                    document.add(Paragraph(rowText))
                                }
                            }
                            document.add(Paragraph(""))
                        }
                    }
                }
            } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_excel))

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Excel a PDF")
            ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), e.message ?: "")
            )
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}