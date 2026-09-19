package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
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

class PdfToTextUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(
            pdfUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                // Hallazgo real de la revisión general 2026-09-16 (#39): cacheFile
                // nunca se borraba -- fuga de almacenamiento acumulativa (una copia
                // sin cifrar del PDF del usuario por cada conversión a texto, para
                // siempre) fuera del ciclo de vida normal de filesDir/converted.
                var cacheFile: File? = null
                try {
                    // ── Copiar al cache ───────────────────────
                    // Nombre único por llamada (antes fijo: "temp_text.pdf") -- RF-CONV-08
                    // puede invocar este use case varias veces en el mismo lote.
                    cacheFile = File.createTempFile("temp_text", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_pdf))

                    // ── Extraer texto con iText7 ──────────────
                    val sb = StringBuilder()
                    var pageCount = 0
                    // Hallazgo real de la revisión general 2026-09-16 (#43): el
                    // chequeo de abajo comparaba sb.toString() COMPLETO, que ya
                    // incluye el encabezado "=== Página N ===" agregado para cada
                    // página -- esa rama nunca se alcanzaba (sb nunca queda en
                    // blanco si hay al menos 1 página), así que un PDF escaneado
                    // sin OCR "convertía" con éxito a un .txt sin ningún contenido
                    // real, sin avisar. Se rastrea el texto real por separado del
                    // string final con encabezados.
                    var hasRealText = false
                    // Hallazgo real de la revisión de correctitud adversarial de
                    // este mismo lote (2026-09-16): mismo patrón ya corregido acá
                    // como hallazgo #40 en PdfToHtmlUseCase/PdfToWordUseCase --
                    // pdfDoc.close() manual solo se alcanzaba si NINGUNA página
                    // lanzaba al extraer su texto; una página malformada a mitad
                    // del loop dejaba el PdfDocument/PdfReader sin cerrar. .use{}
                    // lo cierra pase lo que pase.
                    PdfDocument(PdfReader(cacheFile)).use { pdfDoc ->
                        pageCount = pdfDoc.numberOfPages
                        for (i in 1..pageCount) {
                            val pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i))
                            if (pageText.isNotBlank()) hasRealText = true
                            // Hallazgo real de la revisión general 2026-09-16
                            // (cuarta pasada, #26): hardcodeado en español pese al
                            // idioma configurado -- a diferencia de los mensajes
                            // de error (ya corregidos), este es el CONTENIDO real
                            // del .txt que el usuario recibe.
                            sb.appendLine(context.getString(R.string.converter_txt_page_label, i))
                            sb.appendLine(pageText)
                            sb.appendLine()
                        }
                    }

                    val text = sb.toString().trim()
                    if (!hasRealText) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_empty_pdf_text_scanned),
                        )
                    }

                    // ── Guardar como TXT ──────────────────────
                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    val outputFile = File(outputDir, "$baseName.txt")
                    outputFile.writeText(text)

                    ConversionResult.Success(
                        outputFile = outputFile,
                        pageCount = pageCount,
                        fileSizeKb = (outputFile.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt. La limpieza de cacheFile sigue
                    // corriendo igual vía el `finally` de abajo.
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error extrayendo texto del PDF")
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception, así que el
                    // catch de arriba nunca la atrapaba con un PDF grande.
                    Timber.e(e, "PdfToTextUseCase: sin memoria convirtiendo el documento")
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

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
