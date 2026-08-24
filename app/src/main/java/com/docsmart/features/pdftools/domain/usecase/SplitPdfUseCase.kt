package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SplitPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SplitPdfUseCase"
    }

    suspend operator fun invoke(
        pdfUri        : Uri,
        fromPage      : Int,
        toPage        : Int,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(
                    "No se pudo leer el PDF. Verifica que sea un archivo válido."
                )

            val reader     = PdfReader(cacheFile)
            val sourcePdf  = PdfDocument(reader)
            val totalPages = sourcePdf.numberOfPages

            Timber.d("$TAG: PDF abierto — $totalPages páginas totales")

            if (totalPages == 0) {
                sourcePdf.close()
                return@withContext PdfToolResult.Error("El PDF no tiene páginas.")
            }

            val startPage = fromPage.coerceIn(1, totalPages)
            val endPage   = toPage.coerceIn(startPage, totalPages)

            if (startPage == endPage && totalPages > 1) {
                sourcePdf.close()
                return@withContext PdfToolResult.Error(
                    "El rango debe incluir al menos 2 páginas. El PDF tiene $totalPages páginas."
                )
            }

            val pagesExtracted = endPage - startPage + 1
            Timber.d("$TAG: extrayendo páginas $startPage a $endPage ($pagesExtracted páginas)")

            val name       = outputFileName ?: "Split_p${startPage}-p${endPage}"
            val outputFile = createOutputFile(name)
            val writer     = PdfWriter(outputFile)
            val destPdf    = PdfDocument(writer)

            sourcePdf.copyPagesTo(startPage, endPage, destPdf)

            destPdf.close()
            sourcePdf.close()

            if (outputFile.length() == 0L)
                return@withContext PdfToolResult.Error("Error al generar el PDF dividido.")

            val sizeKb = outputFile.length() / 1024
            Timber.d("$TAG: split exitoso — $pagesExtracted páginas, $sizeKb KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = "PDF dividido: $pagesExtracted página(s) extraídas · $sizeKb KB"
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al dividir PDF")
            PdfToolResult.Error(
                message = "Error al dividir: ${e.message ?: "Error desconocido"}",
                cause   = e
            )
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "split_${System.currentTimeMillis()}.pdf")
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
        val dir       = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "${name}_$timestamp.pdf")
    }
}