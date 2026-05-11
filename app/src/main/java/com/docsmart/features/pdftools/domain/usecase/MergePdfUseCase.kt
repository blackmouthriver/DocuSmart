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
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class MergePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MergePdfUseCase"
    }

    suspend operator fun invoke(
        pdfUris: List<Uri>,
        outputFileName: String? = null
    ): PdfToolResult = withContext(Dispatchers.IO) {
        try {
            if (pdfUris.size < 2) {
                return@withContext PdfToolResult.Error(
                    "Selecciona al menos 2 PDFs para unir"
                )
            }

            val mergedDocument = PdfDocument()
            var pageIndex = 0

            pdfUris.forEach { uri ->
                val file = copyUriToCache(uri) ?: run {
                    Timber.w("$TAG: no se pudo copiar URI al cache: $uri")
                    return@forEach
                }

                val fileDescriptor = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY
                )
                val renderer = PdfRenderer(fileDescriptor)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    // ── CORRECCIÓN BUG-03: guardar dimensiones ANTES de cerrar la página ──
                    val pageWidth  = page.width * 2
                    val pageHeight = page.height * 2

                    val bitmap = Bitmap.createBitmap(
                        pageWidth,
                        pageHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(
                        bitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    page.close() // cerrar DESPUÉS de renderizar y guardar dimensiones

                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        ++pageIndex
                    ).create()

                    val docPage = mergedDocument.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    mergedDocument.finishPage(docPage)
                    bitmap.recycle()
                }

                renderer.close()
                fileDescriptor.close()
            }

            if (pageIndex == 0) {
                mergedDocument.close()
                return@withContext PdfToolResult.Error(
                    "No se pudo leer ninguna página de los PDFs seleccionados."
                )
            }

            val outputFile = createOutputFile(outputFileName ?: "Merged")
            FileOutputStream(outputFile).use { mergedDocument.writeTo(it) }
            mergedDocument.close()

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(
                    "Error al generar el PDF combinado."
                )
            }

            Timber.d("$TAG: merge exitoso — $pageIndex páginas, ${outputFile.length() / 1024} KB")

            PdfToolResult.Success(
                outputFile = outputFile,
                message    = "PDFs unidos correctamente — ${pdfUris.size} archivos, $pageIndex páginas"
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al unir PDFs")
            PdfToolResult.Error(
                "Error al unir PDFs: ${e.message ?: "Error desconocido"}",
                e
            )
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(
                context.cacheDir,
                "merge_${System.currentTimeMillis()}.pdf"
            )
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
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
