package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class PdfToWordUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pdfUri  : Uri,
        fileName: String? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            // 1 — Copiar al cache
            cacheFile = File(context.cacheDir, "pdftodocx_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                cacheFile.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ConversionResult.Error("No se pudo leer el PDF")

            // 2 — Extraer texto con iText7
            val pdfDoc        = PdfDocument(PdfReader(cacheFile))
            val totalPages    = pdfDoc.numberOfPages  // ← leer ANTES de cerrar
            val sb            = StringBuilder()

            for (i in 1..totalPages) {
                val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)).trim()
                if (text.isNotBlank()) {
                    sb.appendLine(text)
                    sb.appendLine()
                }
            }
            pdfDoc.close() // ← cerrar DESPUÉS de extraer todo

            val extractedText = sb.toString().trim()
            if (extractedText.isBlank())
                return@withContext ConversionResult.Error(
                    "El PDF no contiene texto extraíble. Puede ser un PDF escaneado."
                )

            // 3 — Crear .docx mínimo válido
            val outputDir  = File(context.filesDir, "converted").apply { mkdirs() }
            val baseName   = fileName ?: generateTimestamp()
            val outputFile = File(outputDir, "$baseName.docx")

            createMinimalDocx(outputFile, extractedText)

            if (outputFile.length() == 0L)
                return@withContext ConversionResult.Error("Error al generar el archivo Word")

            Timber.d("PdfToWordUseCase: docx creado — ${outputFile.length() / 1024} KB")

            ConversionResult.Success(
                outputFile = outputFile,
                pageCount  = totalPages,
                fileSizeKb = (outputFile.length() / 1024).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "PdfToWordUseCase: error — ${e.message}")
            ConversionResult.Error("Error al convertir: ${e.message}")
        } finally {
            cacheFile?.delete()
        }
    }

    private fun createMinimalDocx(outputFile: File, text: String) {
        val paragraphs = text.split("\n").joinToString("") { line ->
            val escaped = line
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            if (escaped.isBlank()) "<w:p/>"
            else "<w:p><w:r><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>"
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$paragraphs<w:sectPr/></w:body>
</w:document>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>"""

        val wordRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        ZipOutputStream(outputFile.outputStream()).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("[Content_Types].xml",          contentTypesXml)
            addEntry("_rels/.rels",                  relsXml)
            addEntry("word/document.xml",            documentXml)
            addEntry("word/_rels/document.xml.rels", wordRelsXml)
        }
    }

    private fun generateTimestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}