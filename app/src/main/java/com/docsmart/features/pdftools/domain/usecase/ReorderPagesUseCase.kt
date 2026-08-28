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

data class ReorderPagesMessages(
    val emptyOrderError: String,
    val readError       : String,
    val generateError   : String,
    val success          : String, // formato: %1$d páginas
    val genericError      : String  // formato: %1$s mensaje de excepción
)

class ReorderPagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ReorderPagesUseCase"
    }

    /**
     * RF-PDF-08/HU-PDF-07: reordena y/o elimina páginas en un solo paso vía
     * iText7 (`copyPagesTo`) -- `pageOrder` es la lista final de números de
     * página **1-based del PDF original**, ya en el orden deseado; una
     * página del original que no aparezca en la lista queda eliminada del
     * resultado (AC2). No rasteriza (RNF-PDF-01), conserva el contenido
     * original de cada página tal cual (mismo principio que Unir/Rotar).
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        pageOrder     : List<Int>,
        outputFileName: String? = null,
        messages      : ReorderPagesMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (pageOrder.isEmpty()) {
            return@withContext PdfToolResult.Error(messages.emptyOrderError)
        }

        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Reordered")

            val sourcePdf = PdfDocument(PdfReader(cacheFile))
            val destPdf   = PdfDocument(PdfWriter(outputFile))
            pageOrder.forEach { pageNumber ->
                sourcePdf.copyPagesTo(pageNumber, pageNumber, destPdf)
            }
            destPdf.close()
            sourcePdf.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: reorden exitoso — ${pageOrder.size} páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = String.format(messages.success, pageOrder.size)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al reordenar páginas")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "reorder_${System.currentTimeMillis()}.pdf")
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
