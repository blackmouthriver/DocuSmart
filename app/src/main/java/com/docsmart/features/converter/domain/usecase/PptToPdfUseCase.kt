package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Convierte PowerPoint (.pptx) a PDF extrayendo el texto de cada diapositiva
 * (mismo parseo XML que [PptToTextUseCase]) y componiéndolo como una página
 * de texto por diapositiva con iText7 — no reproduce el diseño visual
 * original, igual que Word→PDF y Excel→PDF tampoco lo hacen. No usa Apache
 * POI para renderizar diapositivas porque esa ruta depende de `java.awt`
 * (`Graphics2D`), que no está disponible en Android.
 */
class PptToPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // Cualquier fallo leyendo/parseando el .pptx debe verse igual para quien
        // llama: un mensaje de error, no un crash de la conversión completa.
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            pptUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada,
                // #22, latente -- PPT_TO_PDF está oculto de la grilla hoy):
                // outputFile era un `val` dentro del try -- el catch de abajo ni
                // siquiera podía referenciarlo para borrarlo si algo lanzaba
                // después de que PdfWriter(outputFile) ya creó el archivo en
                // disco, mismo patrón ya corregido en Herramientas PDF
                // (hallazgos #25-27) y en el resto del Convertidor.
                var outputFile: File? = null
                try {
                    // Hallazgo real #38: ConversionType declara .ppt (OLE2, pre-
                    // Office 2007) como origen soportado, pero este parser solo
                    // entiende el ZIP interno de .pptx -- sin este chequeo, un
                    // .ppt real fallaba con "sin texto" en vez de avisar que el
                    // formato en sí no está soportado.
                    if (isLegacyOle2Uri(context, pptUri)) {
                        // Hallazgo real de la auditoría general 2026-09-17/18
                        // (décima ronda, Alta -- C1): ver el mismo hallazgo en
                        // ExcelToHtmlUseCase.kt.
                        if (isPasswordProtectedOfficeUri(context, pptUri)) {
                            return@withContext ConversionResult.Error(
                                context.getString(R.string.converter_error_password_protected),
                            )
                        }
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_legacy_format_unsupported),
                        )
                    }
                    val slideMap =
                        extractSlideText(pptUri)
                            ?: return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_ppt))

                    if (slideMap.isEmpty()) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_empty_presentation),
                        )
                    }

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()
                    outputFile = File(outputDir, "$baseName.pdf")

                    // Bug real encontrado 2026-09-14 (repaso general): document.close()
                    // manual solo se alcanzaba en el camino feliz -- una excepción al
                    // escribir una diapositiva dejaba el PdfDocument/Document sin
                    // cerrar, con el FileOutputStream de outputFile abierto. .use{}
                    // garantiza el cierre pase lo que pase.
                    val pdfDoc = PdfDocument(PdfWriter(outputFile!!))
                    Document(pdfDoc).use { document ->
                        slideMap.toSortedMap().entries.forEachIndexed { index, (num, text) ->
                            // Hallazgo real de la revisión general 2026-09-16
                            // (cuarta pasada, #26, latente -- PPT_TO_PDF está
                            // oculto de la grilla hoy): hardcodeado en español
                            // pese al idioma configurado -- este es el CONTENIDO
                            // real del PDF que el usuario recibe.
                            document.add(
                                Paragraph(context.getString(R.string.converter_pdf_slide_label, num)).setBold(),
                            )
                            document.add(Paragraph(text))
                            if (index < slideMap.size - 1) document.add(AreaBreak())
                        }
                    }

                    ConversionResult.Success(
                        outputFile = outputFile!!,
                        pageCount = slideMap.size,
                        fileSizeKb = (outputFile!!.length() / 1024).toInt(),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt.
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error convirtiendo PowerPoint a PDF")
                    outputFile?.delete()
                    ConversionResult.Error(
                        String.format(context.getString(R.string.converter_error_generic_format), e.message ?: ""),
                    )
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): OutOfMemoryError no hereda de Exception -- un .pptx
                    // grande podía agotar la memoria a mitad de camino y dejar el
                    // .pdf parcial huérfano (PdfWriter ya lo había creado en disco).
                    Timber.e(e, "PptToPdfUseCase: sin memoria convirtiendo el documento")
                    outputFile?.delete()
                    ConversionResult.Error(
                        String.format(
                            context.getString(R.string.converter_error_generic_format),
                            context.getString(R.string.converter_error_unknown),
                        ),
                    )
                }
            }

        // Bug real reportado por testers 2026-09-11: "La presentación no contiene
        // texto" con archivos .pptx que sí tienen texto real. Causa raíz
        // confirmada con diagnóstico en dispositivo real: envolver directamente
        // el InputStream del content resolver en ZipInputStream podía encontrar
        // 0 entradas (zip.nextEntry devolvía null de entrada) pese a que el mismo
        // archivo, leído directo desde disco, se procesaba sin problemas -- una
        // inconsistencia real del stream que entrega el content resolver para
        // ciertas URIs de Storage Access Framework. Se corrige leyendo el
        // archivo completo a memoria primero (los .pptx de este flujo son
        // documentos de texto, no video/imagen pesada) y envolviendo esos bytes
        // en un ByteArrayInputStream simple antes de pasarlo a ZipInputStream --
        // elimina cualquier dependencia del comportamiento del stream original.
        private fun extractSlideText(pptUri: Uri): Map<Int, String>? {
            // Hallazgo real de la revisión de seguridad adversarial 2026-09-16:
            // readBytes() sin límite bufferea el .pptx completo -- ver
            // readBoundedBytes() en ZipEntrySafety.kt, mismo criterio que
            // readEntrySafely() pero aplicado acá, antes de llegar al ZIP.
            val bytes =
                context.contentResolver.openInputStream(pptUri)?.use { it.readBoundedBytes() }
                    ?: return null
            return java.io.ByteArrayInputStream(bytes).use { ZipInputStream(it).use(::readSlideTexts) }
        }

        private fun readSlideTexts(zip: ZipInputStream): Map<Int, String> {
            val slideMap = mutableMapOf<Int, String>()
            generateSequence { zip.nextEntry }
                .filter { isSlideEntry(it.name) }
                .forEach { entry ->
                    val text = textOfSlideXml(zip.readEntrySafely().toString(Charsets.UTF_8))
                    if (text.isNotBlank()) slideMap[slideNumberOf(entry.name)] = text
                }
            return slideMap
        }

        private fun isSlideEntry(name: String) = name.startsWith("ppt/slides/slide") && name.endsWith(".xml") && !name.contains("_rels")

        private fun slideNumberOf(name: String) = name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0

        private fun textOfSlideXml(xml: String): String =
            Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .mapNotNull { m ->
                    val t =
                        m.value
                            .replace(Regex("<a:rPr[^/]*/?>|</a:rPr>"), "")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    t.ifBlank { null }
                }.joinToString("\n")

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
