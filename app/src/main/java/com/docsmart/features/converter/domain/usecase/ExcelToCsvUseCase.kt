package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class ExcelToCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Cualquier fallo leyendo/parseando el .xlsx debe verse igual para quien
    // llama: un mensaje de error, no un crash de la conversión completa.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        excelUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            var rowCount = 0

            context.contentResolver.openInputStream(excelUri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                // Solo la primera hoja: CSV es de una sola tabla, no soporta múltiples hojas.
                val sheet = workbook.getSheetAt(0)
                // Hallazgo real de la revisión general 2026-09-16: cell.toString()
                // en una celda de fórmula devuelve el texto de la fórmula
                // ("=SUM(A1:A2)"), no el resultado calculado -- pérdida
                // silenciosa de datos. DataFormatter + FormulaEvaluator la
                // evalúa y la formatea igual que Excel lo mostraría.
                val evaluator    = workbook.creationHelper.createFormulaEvaluator()
                val dataFormatter = DataFormatter()

                sheet.forEach { row ->
                    val cells = row.map { cell -> escapeCsv(formatCellSafely(cell, dataFormatter, evaluator)) }
                    if (cells.any { it.isNotBlank() }) {
                        sb.appendLine(cells.joinToString(","))
                        rowCount++
                    }
                }
                workbook.close()
            } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_excel))

            if (rowCount == 0) {
                return@withContext ConversionResult.Error(context.getString(R.string.converter_error_empty_spreadsheet))
            }

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.csv")
            outputFile.writeText(sb.toString())

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = rowCount,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo Excel a CSV")
            ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), e.message ?: "")
            )
        }
    }

    // Hallazgo real de la revisión de corrección 2026-09-16: evaluator.evaluate()
    // puede lanzar (referencia circular, función no soportada por POI,
    // referencia a otro libro) para UNA celda puntual -- sin este try por
    // celda, esa excepción no la atrapaba nada más que el catch genérico de
    // invoke(), abortando TODA la conversión (regresión frente al
    // cell.toString() anterior, que nunca lanzaba). Mismo criterio que
    // extractExcelSheets() en ViewerScreen.kt.
    @Suppress("TooGenericExceptionCaught")
    private fun formatCellSafely(cell: Cell, dataFormatter: DataFormatter, evaluator: FormulaEvaluator): String =
        try {
            dataFormatter.formatCellValue(cell, evaluator).trim()
        } catch (e: Exception) {
            Timber.w(e, "formatCellSafely: no se pudo formatear una celda, se usa el texto crudo")
            cell.toString().trim()
        }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
