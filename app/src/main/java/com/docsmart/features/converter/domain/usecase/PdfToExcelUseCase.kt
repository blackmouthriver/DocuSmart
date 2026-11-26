package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.geom.Vector
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * PDF a Excel (paridad con la web): cada linea del PDF es una fila, las columnas salen de la
 * alineacion del texto y los numeros se guardan como numeros de Excel (con formato de moneda,
 * porcentaje o miles) para poder sumarlos. Una hoja por pagina.
 *
 * Alcance honesto, igual que PDF a Word: parte del texto del PDF, asi que un PDF escaneado (solo
 * imagen) no tiene nada que convertir. No reconstruye bordes ni celdas combinadas.
 */
class PdfToExcelUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            pdfUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                var outputFile: File? = null
                try {
                    cacheFile = copyToCache(pdfUri)
                    val sheets = cacheFile?.let { readSheets(it) }
                    when {
                        cacheFile == null || sheets == null ->
                            ConversionResult.Error(context.getString(R.string.converter_error_read_pdf))
                        sheets.all { sheet -> sheet.rows.none { row -> row.any { !it.isEmpty } } } ->
                            ConversionResult.Error(context.getString(R.string.converter_error_empty_pdf_text_scanned))
                        else -> {
                            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                            val file = File(outputDir, "${fileName ?: generateTimestamp()}.xlsx")
                            outputFile = file
                            file.outputStream().use { XlsxWriter.write(sheets, it) }
                            Timber.d("PdfToExcelUseCase: xlsx creado — ${file.length() / 1024} KB")
                            val sizeKb = (file.length() / 1024).toInt()
                            ConversionResult.Success(file, pageCount = sheets.size, fileSizeKb = sizeKb)
                        }
                    }
                } catch (e: CancellationException) {
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    // Solo el tipo: CrashlyticsTree reenvia todo >= WARN a Firebase y e.message puede traer rutas.
                    Timber.e("PdfToExcelUseCase: error: ${e.javaClass.simpleName}")
                    outputFile?.delete()
                    val format = context.getString(R.string.converter_error_generic_format)
                    ConversionResult.Error(String.format(format, e.message ?: ""))
                } catch (e: OutOfMemoryError) {
                    Timber.e(e, "PdfToExcelUseCase: sin memoria")
                    outputFile?.delete()
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
                    )
                } finally {
                    cacheFile?.delete()
                }
            }

        private fun copyToCache(pdfUri: Uri): File? {
            val file = File(context.cacheDir, "pdftoxlsx_${System.currentTimeMillis()}.pdf")
            val copied =
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                    true
                }
            if (copied != true) file.delete()
            return if (copied == true) file else null
        }

        private fun readSheets(pdf: File): List<SheetData> =
            PdfDocument(PdfReader(pdf)).use { pdfDoc ->
                (1..pdfDoc.numberOfPages).map { number ->
                    val page = pdfDoc.getPage(number)
                    val listener = FragmentListener(page.pageSize.height)
                    PdfCanvasProcessor(listener).processPageContent(page)
                    val lines = PdfSheetLayout.buildLines(listener.fragments, PdfSheetLayout.SPREADSHEET_SPLIT_GAP)
                    val name = XlsxWriter.sheetName(context.getString(R.string.converter_excel_sheet_page, number))
                    SheetData(name, PdfSheetLayout.pageRows(lines))
                }
            }

        /** Reune los fragmentos de texto de una pagina con su posicion (y medida desde arriba). */
        private class FragmentListener(
            private val pageHeight: Float,
        ) : IEventListener {
            val fragments = mutableListOf<TextFragment>()

            override fun eventOccurred(
                data: IEventData?,
                type: EventType,
            ) {
                val info = data as? TextRenderInfo ?: return
                val text = info.text
                if (text.isEmpty()) return
                val fontNames = info.font?.fontProgram?.fontNames
                val fontName = fontNames?.fontName.orEmpty()
                val start = info.baseline.startPoint
                val end = info.baseline.endPoint
                fragments.add(
                    TextFragment(
                        text = text,
                        x = start.get(Vector.I1),
                        y = pageHeight - start.get(Vector.I2),
                        width = end.get(Vector.I1) - start.get(Vector.I1),
                        size = info.fontSize,
                        bold = fontNames?.isBold == true || fontName.contains("bold", ignoreCase = true),
                    ),
                )
            }

            override fun getSupportedEvents(): MutableSet<EventType> = mutableSetOf(EventType.RENDER_TEXT)
        }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
