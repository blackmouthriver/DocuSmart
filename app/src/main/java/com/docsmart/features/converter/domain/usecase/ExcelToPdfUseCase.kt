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

            context.contentResolver.openInputStream(excelUri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                val writer = PdfWriter(outputFile)
                val pdfDoc = PdfDocument(writer)
                val document = Document(pdfDoc)

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

                document.close()
                workbook.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Excel")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = 1,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Excel a PDF")
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}