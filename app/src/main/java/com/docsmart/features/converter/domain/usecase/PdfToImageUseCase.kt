package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** Tamaño en píxeles del bitmap al que se renderiza una página. */
internal data class PageRenderSize(
    val width: Int,
    val height: Int,
)

// Tope de píxeles por página (~64 MB en ARGB_8888). Ronda 15: la escala fija 2x
// sobre una página de gran formato (planos A0, ~2384x3370 pt) pedía ~128 MB por
// bitmap y agotaba la memoria -- el usuario recibía "Error desconocido" en un
// PDF perfectamente válido. Por encima del tope se reduce la escala.
internal const val PDF_TO_IMAGE_MAX_PIXELS = 16_000_000L
private const val PDF_TO_IMAGE_SCALE = 2

/**
 * Tamaño del bitmap para una página de [pageWidth] x [pageHeight] puntos: 2x, o
 * menos si superaría [PDF_TO_IMAGE_MAX_PIXELS]. Función pura, testeable en JVM.
 */
internal fun pdfToImageRenderSize(
    pageWidth: Int,
    pageHeight: Int,
): PageRenderSize {
    val width = pageWidth.coerceAtLeast(1).toLong() * PDF_TO_IMAGE_SCALE
    val height = pageHeight.coerceAtLeast(1).toLong() * PDF_TO_IMAGE_SCALE
    val pixels = width * height
    if (pixels <= PDF_TO_IMAGE_MAX_PIXELS) return PageRenderSize(width.toInt(), height.toInt())
    val factor = sqrt(PDF_TO_IMAGE_MAX_PIXELS.toDouble() / pixels)
    return PageRenderSize(
        width = (width * factor).toInt().coerceAtLeast(1),
        height = (height * factor).toInt().coerceAtLeast(1),
    )
}

class PdfToImageUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(
            pdfUri: Uri,
            fileName: String? = null,
        ): ConversionResult =
            withContext(Dispatchers.IO) {
                // Bug real corregido 2026-09-08: el archivo de caché tenía un
                // nombre fijo ("temp_convert.pdf", no único como en el resto de los
                // use cases de esta app) y nunca se borraba -- quedaba en disco
                // después de cada conversión, y dos conversiones de este tipo a la
                // vez competían por el mismo archivo. `renderer`/`fileDescriptor`
                // tampoco se cerraban si algo fallaba a mitad del loop (ej.
                // `OutOfMemoryError` al renderizar una página a 2x, que ni siquiera
                // hereda de `Exception` y no la atrapa el catch de más abajo).
                var cacheFile: File? = null
                // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
                // si el loop de páginas fallaba a mitad de camino (OutOfMemoryError
                // u otra excepción al renderizar la página N de un PDF grande),
                // las páginas 1..N-1 ya escritas a disco quedaban huérfanas en
                // filesDir/converted para siempre -- la función nunca llegaba a
                // Success, así que nada las referenciaba ni las borraba. Se
                // declara afuera del try para poder limpiarlas en ambos catch.
                val outputFiles = mutableListOf<File>()
                try {
                    // ── Copiar al cache ───────────────────────
                    cacheFile = File(context.cacheDir, "temp_convert_${System.currentTimeMillis()}.pdf")
                    if (!copyPdfToCache(pdfUri, cacheFile)) {
                        return@withContext ConversionResult.Error(context.getString(R.string.converter_error_read_pdf))
                    }

                    val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
                    val baseName = fileName ?: generateTimestamp()

                    renderAllPages(cacheFile, outputDir, baseName, outputFiles)

                    if (outputFiles.isEmpty()) {
                        return@withContext ConversionResult.Error(
                            context.getString(R.string.converter_error_extract_pages_failed),
                        )
                    }

                    // Retornar el primer archivo como resultado principal
                    ConversionResult.Success(
                        outputFile = outputFiles.first(),
                        pageCount = outputFiles.size,
                        fileSizeKb = outputFiles.sumOf { it.length() / 1024 }.toInt(),
                        extraFiles = outputFiles.drop(1),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo 1 (auditoría del Convertidor): ver el mismo hallazgo
                    // en ConvertImageToPdfUseCase.kt. Las páginas ya escritas a
                    // disco antes de la cancelación se borran igual que en los
                    // catches de error (mismo criterio de cleanupOrphanPages), pero
                    // sin construir un ConversionResult.Error -- la cancelación se
                    // relanza tal cual.
                    outputFiles.forEach { it.delete() }
                    throw e
                } catch (e: Exception) {
                    // Solo el tipo: CrashlyticsTree reenvía todo >= WARN a Firebase
                    // y e.message puede contener rutas/URIs reales.
                    Timber.e("Error convirtiendo PDF a imagen: ${e.javaClass.simpleName}")
                    cleanupOrphanPages(outputFiles, e.message ?: "")
                } catch (e: OutOfMemoryError) {
                    // OutOfMemoryError no hereda de Exception -- sin este catch,
                    // una página de alta resolución (width*2 x height*2) sin
                    // memoria suficiente crasheaba toda la conversión.
                    Timber.e("Sin memoria convirtiendo PDF a imagen: ${e.javaClass.simpleName}")
                    cleanupOrphanPages(outputFiles, context.getString(R.string.converter_error_unknown))
                } finally {
                    cacheFile?.delete()
                }
            }

        // Extraído de invoke() (detekt: CyclomaticComplexMethod, disparado al
        // agregar el catch de CancellationException del hallazgo 1 de la
        // auditoría del Convertidor).
        private fun copyPdfToCache(
            pdfUri: Uri,
            cacheFile: File,
        ): Boolean {
            var copied = false
            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                // Ronda 15: un origen de 0 bytes se daba por "copiado" y el
                // PdfRenderer reventaba después con un mensaje interno de la
                // plataforma en vez del error de lectura correcto.
                copied = cacheFile.outputStream().use { output -> input.copyTo(output) } > 0L
            }
            return copied
        }

        // Extraído de invoke() (mismo motivo que copyPdfToCache()) -- agrupa la
        // apertura del PdfRenderer y el render de todas las páginas.
        //
        // Ronda 15: escribe en `outputFiles` (del llamador) página por página en
        // vez de devolver una lista al final -- si fallaba/se cancelaba a mitad,
        // el llamador recibía la excepción SIN lista y la limpieza de "páginas
        // huérfanas" del hallazgo #21 (cuarta pasada) borraba una lista vacía:
        // las páginas 1..N-1 ya escritas seguían huérfanas. Además ahora es
        // suspend con ensureActive() por página: sin punto de suspensión, la
        // cancelación no se notaba hasta el final y withContext descartaba un
        // Success con todos los .jpg ya escritos.
        private suspend fun renderAllPages(
            cacheFile: File,
            outputDir: File,
            baseName: String,
            outputFiles: MutableList<File>,
        ) {
            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        outputFiles.add(renderPageToFile(renderer, i, outputDir, baseName))
                    }
                }
            }
        }

        // Extraído de invoke() (detekt: CyclomaticComplexMethod, disparado al
        // sumar la limpieza de páginas huérfanas de la revisión general
        // 2026-09-16, cuarta pasada, hallazgo #21) -- agrupa el borrado de las
        // páginas ya escritas a disco antes del fallo con la construcción del
        // Error, compartido por los dos catch.
        private fun cleanupOrphanPages(
            outputFiles: List<File>,
            errorDetail: String,
        ): ConversionResult {
            outputFiles.forEach { it.delete() }
            return ConversionResult.Error(
                String.format(context.getString(R.string.converter_error_generic_format), errorDetail),
            )
        }

        // Hallazgo real de la revisión general 2026-09-16: page.close() manual
        // solo se alcanzaba si createBitmap/render no fallaban -- .use{} (a
        // diferencia de un try/catch(Exception)) cierra la page pase lo que
        // pase, OutOfMemoryError incluido. Extraído de invoke() además para
        // bajar la complejidad ciclomática (detekt).
        private fun renderPageToFile(
            renderer: PdfRenderer,
            pageIndex: Int,
            outputDir: File,
            baseName: String,
        ): File =
            renderer.openPage(pageIndex).use { page ->
                val size = pdfToImageRenderSize(page.width, page.height)
                val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                val outputFile = File(outputDir, "${baseName}_pagina${pageIndex + 1}.jpg")
                // Ronda 15: si render/compress fallaba, el bitmap no se reciclaba
                // y el .jpg parcial de ESTA página (aún fuera de `outputFiles`)
                // quedaba huérfano.
                var success = false
                try {
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val encoded =
                        outputFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    if (!encoded) throw IOException("No se pudo codificar la página ${pageIndex + 1}")
                    success = true
                    outputFile
                } finally {
                    bitmap.recycle()
                    if (!success) outputFile.delete()
                }
            }

        private fun generateTimestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
