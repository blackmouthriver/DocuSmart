package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SplitPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        fromPage: Int,
        toPage: Int,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        try {
            val cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(
                    "No se pudo leer el PDF. Verifica que sea un archivo válido."
                )

            val fileDescriptor = ParcelFileDescriptor.open(
                cacheFile, ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fileDescriptor)
            val totalPages = renderer.pageCount

            if (totalPages == 0) {
                renderer.close()
                return@withContext PdfToolResult.Error("El PDF no tiene páginas.")
            }

            // ── Validación correcta del rango ─────────
            // fromPage y toPage son 1-indexed desde la UI
            val startIndex = (fromPage - 1).coerceIn(0, totalPages - 1)
            val endIndex = toPage.coerceIn(startIndex + 1, totalPages)

            if (startIndex >= endIndex) {
                renderer.close()
                return@withContext PdfToolResult.Error(
                    "Rango inválido. La página final ($toPage) debe ser " +
                            "mayor que la inicial ($fromPage)."
                )
            }

            val newDocument = PdfDocument()

            // ── Renderiza solo las páginas del rango ──
            for (i in startIndex until endIndex) {
                val page = renderer.openPage(i)

                val width  = page.width * 2
                val height = page.height * 2

                val bitmap = Bitmap.createBitmap(
                    width, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                // Número de página en el nuevo doc empieza en 1
                val pageInfo = PdfDocument.PageInfo.Builder(
                    width, height,
                    i - startIndex + 1
                ).create()

                val docPage = newDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                newDocument.finishPage(docPage)
                bitmap.recycle()
            }

            renderer.close()
            fileDescriptor.close()

            val pagesExtracted = endIndex - startIndex
            val name = outputFileName
                ?: "Split_p${startIndex + 1}-p$endIndex"

            val outputFile = createOutputFile(name)
            FileOutputStream(outputFile).use { newDocument.writeTo(it) }
            newDocument.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(
                    "Error al generar el PDF dividido."
                )
            }

            PdfToolResult.Success(
                outputFile = outputFile,
                message = "PDF dividido: $pagesExtracted página(s) " +
                        "extraídas (${outputFile.length() / 1024} KB)"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PdfToolResult.Error(
                "Error al dividir: ${e.message ?: "Error desconocido"}",
                e
            )
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(
                context.cacheDir,
                "split_${System.currentTimeMillis()}.pdf"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "${name}_$timestamp.pdf")
    }
}