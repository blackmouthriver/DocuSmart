package com.docsmart.features.viewer.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.features.viewer.domain.annotation.PdfRectPts
import com.docsmart.features.viewer.domain.annotation.RawPageRect
import com.docsmart.features.viewer.domain.annotation.visualRectToRawPageRect
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * HU-46 (RNF1/RNF2): "quema" resaltados y notas sobre una COPIA nueva del
 * PDF -- el archivo original nunca se toca (mismo principio ya documentado
 * en `WatermarkPdfUseCase`: `PdfCanvas` + `PdfExtGState` para dibujar
 * semitransparente sin rasterizar ninguna página). Las notas además quedan
 * como `PdfTextAnnotation` nativa (el ícono de comentario estándar de
 * cualquier lector de PDF), no solo como un marcador dibujado -- así el
 * texto de la nota sigue siendo legible incluso fuera de DocuSmart.
 *
 * Corrección de rotación (hallazgo #16 de la revisión general 2026-09-16,
 * cuarta pasada): `xPts`/`yPts` de cada anotación se calculan contra
 * `pageWidthPts`/`pageHeightPts` de `PdfPageBitmap`, que vienen de
 * `PdfRenderer.Page` de Android -- esas dimensiones ya reflejan la rotación
 * `/Rotate` de la página (ancho/alto intercambiados si es 90°/270°, bitmap
 * ya "derecho"). Pero `PdfCanvas` dibuja directo sobre el content stream
 * crudo de iText7, que usa el MediaBox SIN rotar. `visualRectToRawPageRect()`
 * (`PdfRectPts.kt`) deshace esa rotación antes de dibujar, para que el
 * resaltado/nota quede en el mismo lugar visual tanto en pantalla como en el
 * PDF aplanado que se comparte.
 */
class FlattenAnnotationsPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FlattenAnnotationsPdfUseCase"
        private const val HIGHLIGHT_OPACITY = 0.35f
        private const val NOTE_MARKER_RADIUS_PTS = 8f
    }

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(sourceUri: Uri, annotations: List<AnnotationEntity>): File? =
        withContext(Dispatchers.IO) {
            if (annotations.isEmpty()) return@withContext null
            var cacheFile: File? = null
            try {
                cacheFile = copyUriToCache(sourceUri) ?: return@withContext null
                val outputFile = createOutputFile()
                val byPage = annotations.groupBy { it.page }
                val gState = PdfExtGState().setFillOpacity(HIGHLIGHT_OPACITY)

                PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                    for ((pageNumber, pageAnnotations) in byPage) {
                        if (pageNumber < 1 || pageNumber > pdf.numberOfPages) continue
                        val page = pdf.getPage(pageNumber)
                        pageAnnotations.forEach { annotation ->
                            when (annotation.type) {
                                AnnotationType.HIGHLIGHT -> drawHighlight(page, annotation, gState)
                                AnnotationType.NOTE      -> drawNote(page, annotation)
                            }
                        }
                    }
                }

                if (outputFile.length() == 0L) {
                    outputFile.delete()
                    return@withContext null
                }
                outputFile
            } catch (e: Exception) {
                Timber.e(e, "$TAG: error aplanando anotaciones")
                null
            } finally {
                cacheFile?.delete()
            }
        }

    private fun drawHighlight(
        page: com.itextpdf.kernel.pdf.PdfPage,
        annotation: AnnotationEntity,
        gState: PdfExtGState
    ) {
        val raw = rawRectFor(page, annotation.xPts, annotation.yPts, annotation.widthPts, annotation.heightPts)
        val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, page.document)
        canvas.saveState()
        canvas.setExtGState(gState)
        canvas.setFillColor(argbToDeviceRgb(annotation.color))
        canvas.rectangle(raw.x.toDouble(), raw.y.toDouble(), raw.width.toDouble(), raw.height.toDouble())
        canvas.fill()
        canvas.restoreState()
    }

    private fun drawNote(page: com.itextpdf.kernel.pdf.PdfPage, annotation: AnnotationEntity) {
        // Marcador visual (círculo relleno pequeño) + PdfTextAnnotation nativa
        // en el mismo punto -- doble representación: se ve como un ícono al
        // mirar la página, y también aparece como comentario nativo del PDF.
        // El punto de anclaje se transforma como un rect de tamaño cero -- el
        // marcador es un círculo (mismo radio en ambos ejes), así que no hace
        // falta transformar su extensión, solo su centro.
        val rawCenter = rawRectFor(page, annotation.xPts, annotation.yPts, 0f, 0f)
        val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, page.document)
        canvas.saveState()
        canvas.setFillColor(argbToDeviceRgb(annotation.color))
        canvas.circle(rawCenter.x.toDouble(), rawCenter.y.toDouble(), NOTE_MARKER_RADIUS_PTS.toDouble())
        canvas.fill()
        canvas.restoreState()

        val rect = Rectangle(
            rawCenter.x - NOTE_MARKER_RADIUS_PTS,
            rawCenter.y - NOTE_MARKER_RADIUS_PTS,
            NOTE_MARKER_RADIUS_PTS * 2,
            NOTE_MARKER_RADIUS_PTS * 2
        )
        val textAnnotation = PdfTextAnnotation(rect)
            .setContents(annotation.text)
            .setColor(argbToDeviceRgb(annotation.color))
        page.addAnnotation(textAnnotation)
    }

    private fun rawRectFor(
        page: com.itextpdf.kernel.pdf.PdfPage,
        xPts: Float, yPts: Float, widthPts: Float, heightPts: Float
    ): RawPageRect {
        val mediaBox = page.mediaBox
        return visualRectToRawPageRect(
            visual = PdfRectPts(xPts, yPts, widthPts, heightPts),
            rotationDegrees = page.rotation,
            rawPageWidthPts = mediaBox.width,
            rawPageHeightPts = mediaBox.height
        )
    }

    private fun argbToDeviceRgb(argb: Int): DeviceRgb {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return DeviceRgb(r, g, b)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun copyUriToCache(uri: Uri): File? {
        val file = File(context.cacheDir, "flatten_annotations_${System.currentTimeMillis()}.pdf")
        return try {
            val copied = if (uri.scheme == "file") copyFileUriToCache(uri, file) else copyContentUriToCache(uri, file)
            if (copied) file else null
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            // Bug real encontrado por la revisión de seguridad HU-46: una
            // excepción a mitad de la copia podía dejar un archivo parcial
            // huérfano en cacheDir -- a diferencia del resto del método (que
            // sí limpia en el camino normal), este catch nunca lo borraba.
            if (file.exists()) file.delete()
            null
        }
    }

    private fun copyFileUriToCache(uri: Uri, dest: File): Boolean {
        val src = uri.path?.let(::File)
        if (src == null || !src.exists()) return false
        src.copyTo(dest, overwrite = true)
        return true
    }

    private fun copyContentUriToCache(uri: Uri, dest: File): Boolean =
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } != null

    private fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "viewer_share").apply { mkdirs() }
        return File(dir, "DocuSmart_anotado_$timestamp.pdf")
    }
}
