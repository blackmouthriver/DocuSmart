package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SignPdfMessages(
    val emptySignatureError: String,
    val readError          : String,
    val noPages            : String,
    val generateError      : String,
    val success              : String, // formato: %1$d número de página
    val genericError          : String  // formato: %1$s mensaje de excepción
)

class SignPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SignPdfUseCase"
        private const val SIGNATURE_WIDTH_POINTS = 150f
        private const val MARGIN_POINTS = 30f
    }

    /**
     * RF-PDF-11 (firma digital): el proyecto no tiene infraestructura de
     * certificados/PKI (eso sería una firma criptográfica real, fuera de
     * alcance para una app de consumo sin gestión de claves), así que
     * implementa lo que la mayoría de apps de firma de PDF ofrecen en la
     * práctica -- una firma manuscrita dibujada a mano, capturada como
     * imagen (PNG) y estampada sobre la página elegida, en la esquina
     * inferior derecha con margen fijo. Usa
     * `PdfCanvas.addImageFittedIntoRectangle`, mismo mecanismo de bajo
     * nivel que el resto del módulo, no rasteriza el resto de la página
     * (RNF-PDF-01).
     */
    suspend operator fun invoke(
        pdfUri            : Uri,
        signatureImageBytes: ByteArray,
        pageNumber         : Int,
        outputFileName     : String? = null,
        messages           : SignPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (signatureImageBytes.isEmpty()) {
            return@withContext PdfToolResult.Error(messages.emptySignatureError)
        }

        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Firmado")
            var signedPage = 1

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                if (pdf.numberOfPages == 0) {
                    return@withContext PdfToolResult.Error(messages.noPages)
                }

                val safePage = pageNumber.coerceIn(1, pdf.numberOfPages)
                val page = pdf.getPage(safePage)
                val pageSize = page.pageSize
                val imageData = ImageDataFactory.create(signatureImageBytes)
                val aspectRatio = imageData.height / imageData.width
                val signatureHeight = SIGNATURE_WIDTH_POINTS * aspectRatio
                val x = pageSize.x + pageSize.width - SIGNATURE_WIDTH_POINTS - MARGIN_POINTS
                val y = pageSize.y + MARGIN_POINTS

                val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdf)
                canvas.addImageFittedIntoRectangle(
                    imageData,
                    Rectangle(x, y, SIGNATURE_WIDTH_POINTS, signatureHeight),
                    false
                )
                signedPage = safePage
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: firma exitosa — página $signedPage")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, signedPage)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al firmar PDF")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "sign_${System.currentTimeMillis()}.pdf")
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
