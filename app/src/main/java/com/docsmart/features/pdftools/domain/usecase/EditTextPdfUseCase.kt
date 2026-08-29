package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy
import com.itextpdf.pdfcleanup.PdfCleanUpLocation
import com.itextpdf.pdfcleanup.PdfCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject

data class EditTextPdfMessages(
    val emptySearchError: String,
    val readError       : String,
    val noPages         : String,
    val noMatchesError  : String,
    val generateError   : String,
    val success          : String, // formato: %1$d ocurrencias reemplazadas
    val genericError      : String  // formato: %1$s mensaje de excepción
)

private data class TextMatch(val pageNumber: Int, val rectangle: Rectangle)

class EditTextPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EditTextPdfUseCase"
        private const val FONT_SIZE_HEIGHT_FACTOR = 0.8f
        private const val MIN_FONT_SIZE = 4f
    }

    /**
     * RF-PDF-10 (edición básica de contenido: texto): busca todas las
     * ocurrencias de `searchText` en el PDF localizándolas por posición
     * real (`RegexBasedLocationExtractionStrategy`, parte de iText7-core,
     * no requiere el módulo pdfCleanup para esto), elimina ese texto de
     * verdad de cada ubicación (`PdfCleaner.cleanUp`, mismo mecanismo que
     * RF-PDF-14/Censurar) y escribe `replaceText` en su lugar
     * (`PdfCanvas`, mismo mecanismo que RF-PDF-07/Marca de agua) — no es
     * un editor de texto de propósito general, es "buscar y reemplazar"
     * con una implementación real, no cosmética.
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        searchText    : String,
        replaceText   : String,
        outputFileName: String? = null,
        messages      : EditTextPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (searchText.isBlank()) {
            return@withContext PdfToolResult.Error(messages.emptySearchError)
        }

        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Editado")
            val font = PdfFontFactory.createFont()
            var matchCount = 0

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                if (pdf.numberOfPages == 0) {
                    return@withContext PdfToolResult.Error(messages.noPages)
                }

                val matches = findMatches(pdf, searchText)
                if (matches.isEmpty()) {
                    return@withContext PdfToolResult.Error(messages.noMatchesError)
                }

                val cleanUpLocations = matches.map { PdfCleanUpLocation(it.pageNumber, it.rectangle) }
                PdfCleaner.cleanUp(pdf, cleanUpLocations)

                matches.forEach { match -> drawReplacement(pdf, match, replaceText, font) }
                matchCount = matches.size
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: reemplazo exitoso — $matchCount ocurrencias")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, matchCount)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al editar texto del PDF")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun findMatches(pdf: PdfDocument, searchText: String): List<TextMatch> {
        val regex = "(?i)" + Pattern.quote(searchText)
        val matches = mutableListOf<TextMatch>()
        for (pageNumber in 1..pdf.numberOfPages) {
            val strategy = RegexBasedLocationExtractionStrategy(regex)
            PdfCanvasProcessor(strategy).processPageContent(pdf.getPage(pageNumber))
            strategy.resultantLocations.forEach { location ->
                matches.add(TextMatch(pageNumber, location.rectangle))
            }
        }
        return matches
    }

    private fun drawReplacement(pdf: PdfDocument, match: TextMatch, replaceText: String, font: PdfFont) {
        if (replaceText.isEmpty()) return
        val page = pdf.getPage(match.pageNumber)
        val rect = match.rectangle
        val fontSize = fontSizeToFit(font, replaceText, rect.width, rect.height)
        // Se agrega en un content stream nuevo (page.newContentStreamAfter()) en
        // vez de PdfCanvas(page) -- el patrón recomendado por iText7 para escribir
        // contenido *después* de un PdfCleaner.cleanUp() sobre la misma página.
        val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdf)
        canvas.beginText()
            .setFontAndSize(font, fontSize)
            .moveText(rect.x.toDouble(), rect.y.toDouble())
            .showText(replaceText)
            .endText()
    }

    private fun fontSizeToFit(font: PdfFont, text: String, targetWidth: Float, targetHeight: Float): Float {
        val baseSize = (targetHeight * FONT_SIZE_HEIGHT_FACTOR).coerceAtLeast(MIN_FONT_SIZE)
        val widthAtBase = font.getWidth(text, baseSize)
        if (widthAtBase <= targetWidth || targetWidth <= 0f) return baseSize
        return (baseSize * (targetWidth / widthAtBase)).coerceAtLeast(MIN_FONT_SIZE)
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "edittext_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
