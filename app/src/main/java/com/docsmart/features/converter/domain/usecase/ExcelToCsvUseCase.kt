package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

class ExcelToCsvUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // Cualquier fallo leyendo/parseando el .xlsx debe verse igual para quien
        // llama: un mensaje de error, no un crash de la conversión completa.
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            excelUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                try {
                    val sb = StringBuilder()
                    var rowCount = 0

                    context.contentResolver.openInputStream(excelUri)?.use { input ->
                        // Hallazgo real de la auditoría del Convertidor (H4): antes
                        // el workbook se usaba suelto sin `.use{}` -- workbook.close()
                        // solo se alcanzaba en el camino feliz, así que cualquier
                        // excepción leyendo/formateando una celda dejaba el Workbook
                        // sin cerrar. Mismo patrón ya usado en ExcelToPdfUseCase.kt.
                        WorkbookFactory.create(input).use { workbook ->
                            // Solo la primera hoja: CSV es de una sola tabla, no soporta múltiples hojas.
                            val sheet = workbook.getSheetAt(0)
                            // Hallazgo real de la revisión general 2026-09-16: cell.toString()
                            // en una celda de fórmula devuelve el texto de la fórmula
                            // ("=SUM(A1:A2)"), no el resultado calculado -- pérdida
                            // silenciosa de datos. DataFormatter + FormulaEvaluator la
                            // evalúa y la formatea igual que Excel lo mostraría.
                            val evaluator = workbook.creationHelper.createFormulaEvaluator()
                            val dataFormatter = DataFormatter()

                            sheet.forEach { row ->
                                val cells = row.map { cell -> escapeCsv(formatCellSafely(cell, dataFormatter, evaluator)) }
                                if (cells.any { it.isNotBlank() }) {
                                    sb.appendLine(cells.joinToString(","))
                                    rowCount++
                                }
                            }
                        }
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
                        fileSizeKb = (outputFile.length() / 1024).toInt(),
                    )
                } catch (e: EncryptedDocumentException) {
                    // Hallazgo real de la auditoría general 2026-09-17/18 (décima
                    // ronda, Alta -- C1): a diferencia de Excel→HTML/PPT→PDF/TXT
                    // (que confunden un .xlsx cifrado con formato legado, ver
                    // ExcelToHtmlUseCase.kt), acá WorkbookFactory.create() sí
                    // distingue el cifrado y lanza esta excepción específica de
                    // POI -- pero antes caía en el catch genérico de abajo y
                    // mostraba texto técnico crudo sin traducir en vez de un
                    // mensaje claro sobre la contraseña.
                    Timber.w("ExcelToCsvUseCase: archivo protegido con contraseña: ${e.javaClass.simpleName}")
                    ConversionResult.Error(context.getString(R.string.converter_error_password_protected))
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt.
                    throw e
                } catch (e: Exception) {
                    Timber.e("Error convirtiendo Excel a CSV: ${e.javaClass.simpleName}")
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception, así que el
                    // catch de arriba nunca la atrapaba con un .xlsx grande.
                    Timber.e(e, "ExcelToCsvUseCase: sin memoria convirtiendo el documento")
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
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
        private fun formatCellSafely(
            cell: Cell,
            dataFormatter: DataFormatter,
            evaluator: FormulaEvaluator,
        ): String =
            try {
                dataFormatter.formatCellValue(cell, evaluator).trim()
            } catch (e: Exception) {
                Timber.w("formatCellSafely: celda sin formato, se usa el texto crudo: ${e.javaClass.simpleName}")
                cell.toString().trim()
            }

        private fun escapeCsv(value: String): String =
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
