package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
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

                sheet.forEach { row ->
                    val cells = row.map { cell -> escapeCsv(cell.toString().trim()) }
                    if (cells.any { it.isNotBlank() }) {
                        sb.appendLine(cells.joinToString(","))
                        rowCount++
                    }
                }
                workbook.close()
            } ?: return@withContext ConversionResult.Error("No se pudo leer el archivo Excel")

            if (rowCount == 0) {
                return@withContext ConversionResult.Error("La hoja de cálculo no contiene datos.")
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
            ConversionResult.Error("Error al convertir: ${e.message}")
        }
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
