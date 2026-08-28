package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ComparePdfMessages(
    val readErrorA        : String,
    val readErrorB        : String,
    val generateError     : String,
    val identical          : String, // sin argumentos
    val differencesFound   : String, // formato: %1$d páginas distintas, %2$d páginas totales
    val genericError        : String, // formato: %1$s mensaje de excepción
    val reportTitle          : String,
    val reportPageHeader      : String, // formato: %1$d número de página
    val reportPageOnlyInA      : String,
    val reportPageOnlyInB      : String,
    val reportOnlyInALine        : String, // formato: %1$s línea
    val reportOnlyInBLine         : String  // formato: %1$s línea
)

private data class PageDiffResult(
    val pageNumber      : Int,
    val pageExistsOnlyInA: Boolean,
    val pageExistsOnlyInB: Boolean,
    val linesOnlyInA     : List<String>,
    val linesOnlyInB     : List<String>
) {
    val hasDifferences: Boolean
        get() = pageExistsOnlyInA || pageExistsOnlyInB || linesOnlyInA.isNotEmpty() || linesOnlyInB.isNotEmpty()
}

class ComparePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ComparePdfUseCase"
    }

    /**
     * RF-PDF-13: compara el texto de cada página de dos PDFs y genera un
     * PDF nuevo con un reporte de las diferencias encontradas (páginas que
     * solo existen en uno de los dos documentos, y líneas de texto
     * presentes en uno pero no en el otro dentro de una misma página). La
     * comparación es por contenido de texto (vía `PdfTextExtractor`, mismo
     * mecanismo ya usado en Estudio/Buscar), no pixel a pixel -- evita
     * requerir que ambos PDFs tengan exactamente el mismo tamaño/DPI de
     * página para poder compararse, y el reporte resultante en sí es texto
     * real, no una imagen.
     */
    suspend operator fun invoke(
        pdfUriA       : Uri,
        pdfUriB       : Uri,
        outputFileName: String? = null,
        messages      : ComparePdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFileA: File? = null
        var cacheFileB: File? = null
        try {
            cacheFileA = copyUriToCache(pdfUriA, "compareA")
                ?: return@withContext PdfToolResult.Error(messages.readErrorA)
            cacheFileB = copyUriToCache(pdfUriB, "compareB")
                ?: return@withContext PdfToolResult.Error(messages.readErrorB)

            val pdfA = PdfDocument(PdfReader(cacheFileA))
            val pdfB = PdfDocument(PdfReader(cacheFileB))
            val pagesA = pdfA.numberOfPages
            val pagesB = pdfB.numberOfPages
            val totalPages = maxOf(pagesA, pagesB)

            val pageResults = (1..totalPages).map { pageNumber ->
                val textA = if (pageNumber <= pagesA) {
                    PdfTextExtractor.getTextFromPage(pdfA.getPage(pageNumber))
                } else null
                val textB = if (pageNumber <= pagesB) {
                    PdfTextExtractor.getTextFromPage(pdfB.getPage(pageNumber))
                } else null
                buildPageDiff(pageNumber, textA, textB)
            }
            pdfA.close()
            pdfB.close()

            val differingPages = pageResults.count { it.hasDifferences }
            val outputFile = createOutputFile(outputFileName ?: "Comparacion")
            writeReport(outputFile, pageResults, differingPages, totalPages, messages)

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: comparación exitosa — $differingPages/$totalPages páginas distintas")

            val resultMessage = if (differingPages == 0) {
                messages.identical
            } else {
                String.format(messages.differencesFound, differingPages, totalPages)
            }

            PdfToolResult.Success(outputFile = outputFile, message = resultMessage)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al comparar PDFs")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFileA?.delete()
            cacheFileB?.delete()
        }
    }

    private fun buildPageDiff(pageNumber: Int, textA: String?, textB: String?): PageDiffResult = when {
        textA == null -> PageDiffResult(
            pageNumber, pageExistsOnlyInA = false, pageExistsOnlyInB = true, emptyList(), emptyList()
        )
        textB == null -> PageDiffResult(
            pageNumber, pageExistsOnlyInA = true, pageExistsOnlyInB = false, emptyList(), emptyList()
        )
        else -> {
            val linesA = textA.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val linesB = textB.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val setA = linesA.toSet()
            val setB = linesB.toSet()
            val onlyInA = linesA.filter { it !in setB }.distinct()
            val onlyInB = linesB.filter { it !in setA }.distinct()
            PageDiffResult(pageNumber, pageExistsOnlyInA = false, pageExistsOnlyInB = false, onlyInA, onlyInB)
        }
    }

    private fun writeReport(
        outputFile     : File,
        pageResults    : List<PageDiffResult>,
        differingPages : Int,
        totalPages     : Int,
        messages       : ComparePdfMessages
    ) {
        val pdfDoc   = PdfDocument(PdfWriter(outputFile))
        val document = Document(pdfDoc)

        document.add(Paragraph(messages.reportTitle).setBold().setFontSize(18f))
        document.add(
            Paragraph(String.format(messages.differencesFound, differingPages, totalPages))
                .setFontSize(12f)
        )
        document.add(Paragraph(" "))

        pageResults.filter { it.hasDifferences }.forEach { page ->
            document.add(
                Paragraph(String.format(messages.reportPageHeader, page.pageNumber))
                    .setBold().setFontSize(13f)
            )
            when {
                page.pageExistsOnlyInA -> document.add(Paragraph(messages.reportPageOnlyInA).setFontSize(11f))
                page.pageExistsOnlyInB -> document.add(Paragraph(messages.reportPageOnlyInB).setFontSize(11f))
                else -> {
                    page.linesOnlyInA.forEach {
                        document.add(Paragraph(String.format(messages.reportOnlyInALine, it)).setFontSize(10f))
                    }
                    page.linesOnlyInB.forEach {
                        document.add(Paragraph(String.format(messages.reportOnlyInBLine, it)).setFontSize(10f))
                    }
                }
            }
            document.add(Paragraph(" "))
        }

        if (differingPages == 0) {
            document.add(Paragraph(messages.identical))
        }

        document.close()
    }

    private fun copyUriToCache(uri: Uri, prefix: String): File? {
        return try {
            val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache ($prefix)")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
