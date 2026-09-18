package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
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
import javax.inject.Inject

data class RedactPdfMessages(
    val emptyRectsError: String,
    val readError      : String,
    val noPages        : String,
    val generateError  : String,
    val success        : String, // formato: %1$d zonas censuradas
    val genericError   : String  // formato: %1$s mensaje de excepción
)

/**
 * Rectángulo a censurar, en fracciones (0..1) del ancho/alto de la página
 * *tal como se ve en pantalla* (origen arriba-izquierda) — no en puntos PDF
 * (origen abajo-izquierda). Guardar como fracción, no en píxeles de la
 * vista previa renderizada, permite convertir a coordenadas PDF exactas sin
 * importar a qué resolución se generó el bitmap de la vista previa.
 */
data class RedactionRect(
    val pageNumber: Int,
    val xFrac: Float,
    val yFrac: Float,
    val wFrac: Float,
    val hFrac: Float
)

class RedactPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RedactPdfUseCase"
    }

    /**
     * RF-PDF-14: censura de forma irreversible las zonas marcadas por el
     * usuario. A diferencia de dibujar un rectángulo negro con `PdfCanvas`
     * (que deja el texto/vector original intacto y extraíble debajo), usa
     * el módulo `pdfCleanup` de iText7 (`PdfCleaner.cleanUp`), que elimina
     * de verdad el contenido del content stream dentro de cada región antes
     * de rellenarla — el texto censurado deja de ser seleccionable,
     * buscable o extraíble en el PDF resultante.
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        rects         : List<RedactionRect>,
        outputFileName: String? = null,
        messages      : RedactPdfMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (rects.isEmpty()) {
            return@withContext PdfToolResult.Error(messages.emptyRectsError)
        }

        var cacheFile: File? = null
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // outputFile era un `val` dentro del try -- el catch de abajo ni
        // siquiera podía referenciarlo para borrarlo si algo lanzaba a
        // mitad de camino, mismo patrón ya corregido en Compare/Compress/
        // OCR (hallazgos #25-27).
        var outputFile: File? = null
        // Hallazgo real de la auditoría general 2026-09-17/18 (décima
        // ronda, Baja -- H4): el mensaje de éxito usaba `rects.size` (el
        // total original) en vez de la cantidad REALMENTE aplicada tras
        // filtrar las que caen en una página fuera de rango -- se saca
        // afuera del bloque para poder usar su tamaño real en el mensaje.
        var appliedCount = 0
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            outputFile = createOutputFile(outputFileName ?: "Censurado")

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                if (pdf.numberOfPages == 0) {
                    // Bug real encontrado 2026-09-14 (repaso general):
                    // PdfWriter(outputFile) ya crea el archivo en disco al
                    // abrirse -- sin este delete() quedaba huérfano en
                    // filesDir/pdftools para siempre (mismo bug ya corregido
                    // antes en NumberPagesUseCase/WatermarkPdfUseCase).
                    outputFile!!.delete()
                    return@withContext PdfToolResult.Error(messages.noPages)
                }

                val locations = rects.mapNotNull { rect ->
                    if (rect.pageNumber < 1 || rect.pageNumber > pdf.numberOfPages) return@mapNotNull null
                    val pageSize = pdf.getPage(rect.pageNumber).pageSize
                    val pdfX = rect.xFrac * pageSize.width
                    val pdfWidth = rect.wFrac * pageSize.width
                    val pdfY = (1f - rect.yFrac - rect.hFrac) * pageSize.height
                    val pdfHeight = rect.hFrac * pageSize.height
                    PdfCleanUpLocation(rect.pageNumber, Rectangle(pdfX, pdfY, pdfWidth, pdfHeight))
                }
                appliedCount = locations.size
                PdfCleaner.cleanUp(pdf, locations)
            }

            if (outputFile!!.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: censura exitosa — $appliedCount zonas")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, appliedCount)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al censurar PDF")
            outputFile?.delete()
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "redact_${System.currentTimeMillis()}.pdf")
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
