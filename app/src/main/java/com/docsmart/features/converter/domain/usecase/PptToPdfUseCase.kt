package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PptToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Cualquier fallo leyendo/parseando el .pptx debe verse igual para quien
    // llama: un mensaje de error, no un crash de la conversión completa.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        pptUri: Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val slideMap = extractSlideText(pptUri)
                ?: return@withContext ConversionResult.Error("No se pudo leer el archivo PowerPoint")

            if (slideMap.isEmpty()) {
                return@withContext ConversionResult.Error("La presentación no contiene texto")
            }

            val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.pdf")

            val pdfDoc = PdfDocument(PdfWriter(outputFile))
            val document = Document(pdfDoc)
            slideMap.toSortedMap().entries.forEachIndexed { index, (num, text) ->
                document.add(Paragraph("=== Diapositiva $num ===").setBold())
                document.add(Paragraph(text))
                if (index < slideMap.size - 1) document.add(AreaBreak())
            }
            document.close()

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount = slideMap.size,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error convirtiendo PowerPoint a PDF")
            ConversionResult.Error("Error al convertir: ${e.message}")
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
        val bytes = context.contentResolver.openInputStream(pptUri)?.use { it.readBytes() }
            ?: return null
        return java.io.ByteArrayInputStream(bytes).use { ZipInputStream(it).use(::readSlideTexts) }
    }

    private fun readSlideTexts(zip: ZipInputStream): Map<Int, String> {
        val slideMap = mutableMapOf<Int, String>()
        generateSequence { zip.nextEntry }
            .filter { isSlideEntry(it.name) }
            .forEach { entry ->
                val text = textOfSlideXml(zip.readBytes().toString(Charsets.UTF_8))
                if (text.isNotBlank()) slideMap[slideNumberOf(entry.name)] = text
            }
        return slideMap
    }

    private fun isSlideEntry(name: String) =
        name.startsWith("ppt/slides/slide") && name.endsWith(".xml") && !name.contains("_rels")

    private fun slideNumberOf(name: String) =
        name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0

    private fun textOfSlideXml(xml: String): String =
        Regex("<a:p[ >](.*?)</a:p>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .mapNotNull { m ->
                val t = m.value
                    .replace(Regex("<a:rPr[^/]*/?>|</a:rPr>"), "")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace(Regex("\\s+"), " ").trim()
                t.ifBlank { null }
            }.joinToString("\n")

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
