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

class PdfToHtmlUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(
            pdfUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                try {
                    cacheFile = File(context.cacheDir, "pdftohtml_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        cacheFile.outputStream().use { input.copyTo(it) }
                    } ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_pdf))

                    // Extraer todo el texto ANTES de cerrar
                    var totalPages = 0
                    val pageTexts = mutableListOf<Pair<Int, String>>()

                    // Hallazgo real de la revisión general 2026-09-16 (#40):
                    // pdfDoc.close() manual solo se alcanzaba si NINGUNA página
                    // lanzaba al extraer su texto -- una página malformada a mitad
                    // del loop dejaba el PdfDocument/PdfReader sin cerrar para
                    // siempre. .use{} lo cierra pase lo que pase.
                    PdfDocument(PdfReader(cacheFile)).use { pdfDoc ->
                        totalPages = pdfDoc.numberOfPages
                        for (i in 1..totalPages) {
                            val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)).trim()
                            if (text.isNotBlank()) pageTexts.add(Pair(i, text))
                        }
                    }

                    if (pageTexts.isEmpty()) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_empty_pdf_text),
                        )
                    }

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    val outputFile = File(outputDir, "$baseName.html")
                    outputFile.writeText(buildHtml(pageTexts))

                    Timber.d("PdfToHtmlUseCase: html creado — ${outputFile.length() / 1024} KB")

                    ConversionResult.Success(
                        outputFile = outputFile,
                        pageCount = totalPages,
                        fileSizeKb = (outputFile.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt. La limpieza de cacheFile sigue
                    // corriendo igual vía el `finally` de abajo.
                    throw e
                } catch (e: Exception) {
                    Timber.e("PdfToHtmlUseCase: error: ${e.javaClass.simpleName}")
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception, así que el
                    // catch de arriba nunca la atrapaba con un PDF grande.
                    Timber.e(e, "PdfToHtmlUseCase: sin memoria convirtiendo el documento")
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

        // Extraído de invoke() -- baja la complejidad ciclomática bajo el
        // umbral de detekt (el hallazgo #40 sumó una rama más al agregar
        // .use{}).
        //
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada,
        // #26): `lang="es"`, el título y "Página N" quedaban hardcodeados en
        // español pese al idioma configurado -- a diferencia de los mensajes
        // de error (ya corregidos), este es el CONTENIDO real del HTML que el
        // usuario recibe. `lang` usa el idioma activo de la app (no el del
        // texto extraído del PDF, imposible de detectar acá) en vez de "es"
        // fijo.
        private fun buildHtml(pageTexts: List<Pair<Int, String>>): String {
            val htmlLang = Locale.getDefault().language
            val title = context.getString(R.string.converter_html_title_pdf)
            val sb = StringBuilder()
            sb.appendLine(
                """<!DOCTYPE html>
<html lang="$htmlLang"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$title</title>
<style>
  body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #333; }
  .page { border-bottom: 2px solid #e0e0e0; padding-bottom: 24px; margin-bottom: 24px; }
  .page-num { color: #999; font-size: 12px; margin-bottom: 8px; }
  p { margin: 8px 0; }
</style>
</head><body>""",
            )

            pageTexts.forEach { (pageNum, text) ->
                val pageLabel = context.getString(R.string.converter_html_page_label, pageNum)
                sb.appendLine("<div class=\"page\">")
                sb.appendLine("<div class=\"page-num\">$pageLabel</div>")
                text.split("\n").forEach { line ->
                    val escaped =
                        line
                            .trim()
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                    if (escaped.isNotBlank()) sb.appendLine("<p>$escaped</p>")
                }
                sb.appendLine("</div>")
            }
            sb.appendLine("</body></html>")
            return sb.toString()
        }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
