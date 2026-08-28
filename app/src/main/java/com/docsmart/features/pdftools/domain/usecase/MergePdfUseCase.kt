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

data class MergePdfMessages(
    val minPdfsError : String,
    val readError    : String,
    val generateError: String,
    val success       : String, // formato: %1$d archivos, %2$d páginas
    val genericError  : String  // formato: %1$s mensaje de excepción
)

class MergePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MergePdfUseCase"
    }

    /**
     * Une vía iText7 (`copyPagesTo`) en lugar de rasterizar cada página a
     * bitmap: conserva texto/vectores seleccionables de los PDFs de origen.
     */
    suspend operator fun invoke(
        pdfUris: List<Uri>,
        outputFileName: String? = null,
        messages: MergePdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (pdfUris.size < 2) {
            return@withContext PdfToolResult.Error(messages.minPdfsError)
        }

        val cacheFiles = mutableListOf<File>()
        val outputFile = createOutputFile(outputFileName ?: "Merged")
        try {
            var totalPages = 0

            PdfDocument(PdfWriter(outputFile)).use { destPdf ->
                pdfUris.forEach { uri ->
                    val file = copyUriToCache(uri) ?: run {
                        Timber.w("$TAG: no se pudo copiar URI al cache: $uri")
                        return@forEach
                    }
                    cacheFiles.add(file)

                    PdfDocument(PdfReader(file)).use { sourcePdf ->
                        val pages = sourcePdf.numberOfPages
                        if (pages > 0) {
                            sourcePdf.copyPagesTo(1, pages, destPdf)
                            totalPages += pages
                        }
                    }
                }
            }

            if (totalPages == 0) {
                outputFile.delete()
                return@withContext PdfToolResult.Error(messages.readError)
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: merge exitoso — $totalPages páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, cacheFiles.size, totalPages)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al unir PDFs")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFiles.forEach { it.delete() }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "merge_${System.currentTimeMillis()}_${System.nanoTime()}.pdf")
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
