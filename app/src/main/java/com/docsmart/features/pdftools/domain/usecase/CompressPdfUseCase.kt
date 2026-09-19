package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class CompressPdfMessages(
    val readError: String,
    val emptyFile: String,
    val noPages: String,
    val generateError: String,
    // formato: %1$d KB
    val alreadyOptimized: String,
    // formato: %1$d antes KB, %2$d después KB, %3$d reducción%
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
)

/** Porcentaje de reducción (entero, truncado) entre el tamaño original y el comprimido. Puede ser negativo. */
internal fun reductionPercent(
    originalSize: Long,
    newSize: Long,
): Int =
    if (originalSize > 0) {
        ((originalSize - newSize) * 100 / originalSize).toInt()
    } else {
        0
    }

/** Si el resultado comprimido no es más chico que el original se conserva el original. */
internal fun shouldKeepOriginal(
    originalSize: Long,
    newSize: Long,
): Boolean = newSize >= originalSize

class CompressPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "CompressPdfUseCase"
        }

        suspend operator fun invoke(
            pdfUri: Uri,
            quality: Int = 60,
            outputFileName: String? = null,
            messages: CompressPdfMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                // Hallazgo real de la revisión de correctitud adversarial de este
                // mismo lote (2026-09-16): el fix original del #25 solo cubría el
                // camino feliz de la rama keepOriginal (borrar outputFile ANTES de
                // devolver originalOutput) -- si cacheFile!!.copyTo(originalOutput)
                // lanzaba (ej. disco lleno) entre crear outputFile y ese borrado,
                // el catch de abajo no tenía forma de referenciarlo y quedaba
                // huérfano de nuevo, mismo bug que se corrigió en ComparePdfUseCase/
                // OcrPdfUseCase con este mismo patrón (var afuera del try).
                var outputFile: File? = null
                try {
                    Timber.d("$TAG: iniciando compresión — calidad: $quality")

                    cacheFile = copyUriToCache(pdfUri)
                        ?: return@withContext PdfToolResult.Error(messages.readError)

                    if (cacheFile.length() == 0L) {
                        return@withContext PdfToolResult.Error(messages.emptyFile)
                    }

                    val originalSize = cacheFile.length()
                    Timber.d("$TAG: tamaño original = ${originalSize / 1024} KB")

                    // Bug real corregido 2026-09-08: `renderer`/`fileDescriptor` antes
                    // se cerraban a mano solo en el camino feliz (o en el caso
                    // "sin páginas") -- si `renderAndCompressPages()` fallaba a
                    // mitad de proceso (ej. `OutOfMemoryError` con un PDF grande,
                    // que además ni siquiera hereda de `Exception` y no lo atrapa
                    // el catch de más abajo), ambos quedaban abiertos para siempre.
                    // `.use{}` los cierra pase lo que pase.
                    val pdfDocument =
                        ParcelFileDescriptor.open(
                            cacheFile,
                            ParcelFileDescriptor.MODE_READ_ONLY,
                        ).use { fileDescriptor ->
                            PdfRenderer(fileDescriptor).use { renderer ->
                                if (renderer.pageCount == 0) {
                                    return@withContext PdfToolResult.Error(messages.noPages)
                                }
                                Timber.d("$TAG: ${renderer.pageCount} páginas a comprimir")
                                renderAndCompressPages(renderer, scaleFactorFor(quality), quality)
                            }
                        }

                    val name = outputFileName ?: "Compressed_q$quality"
                    outputFile = createOutputFile(name)

                    // Ronda 15: pdfDocument.close() estaba después de writeTo() sin
                    // finally -- si escribir fallaba (disco lleno, OOM al volcar
                    // las páginas), el PdfDocument quedaba abierto con todos los
                    // bitmaps de página retenidos.
                    try {
                        FileOutputStream(outputFile!!).use { stream ->
                            pdfDocument.writeTo(stream)
                            stream.flush()
                        }
                    } finally {
                        pdfDocument.close()
                    }

                    if (outputFile!!.length() == 0L) {
                        outputFile!!.delete()
                        return@withContext PdfToolResult.Error(messages.generateError)
                    }

                    val newSize = outputFile!!.length()
                    val originalKb = originalSize / 1024
                    val newKb = newSize / 1024
                    val reduction = reductionPercent(originalSize, newSize)

                    Timber.d("$TAG: $originalKb KB → $newKb KB ($reduction%)")

                    val keepOriginal = shouldKeepOriginal(originalSize, newSize)
                    val finalFile =
                        if (keepOriginal) {
                            Timber.d("$TAG: comprimido mayor que original — usando original")
                            val originalOutput = createOutputFile("${name}_optimizado")
                            cacheFile!!.copyTo(originalOutput, overwrite = true)
                            // Hallazgo real de la revisión general 2026-09-16 (#25):
                            // outputFile (la versión comprimida, más grande) quedaba
                            // huérfano en filesDir/pdftools/ para siempre -- nunca se
                            // referenciaba en el resultado ni se borraba, caso común
                            // con PDFs ya optimizados donde comprimir no reduce nada.
                            outputFile!!.delete()
                            originalOutput
                        } else {
                            outputFile!!
                        }

                    PdfToolResult.Success(
                        outputFile = finalFile,
                        message =
                            resultMessage(
                                messages,
                                keepOriginal,
                                originalKb,
                                finalFile.length() / 1024,
                                reduction,
                            ),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo real de la revisión adversarial de correctitud sobre
                    // el fix de cancelación cooperativa (ensureActive() en
                    // renderAndCompressPages()): CancellationException hereda de
                    // Exception, así que sin este catch específico antes del
                    // genérico de abajo, cada cancelación real (navegar hacia
                    // atrás) se registraba como un error de compresión -- ruido
                    // falso en cualquier reporte de fallos. Se relanza tal cual.
                    outputFile?.delete()
                    throw e
                } catch (e: OutOfMemoryError) {
                    // Ronda 15: OutOfMemoryError no hereda de Exception -- si
                    // ocurría al volcar el PDF a disco, el .pdf parcial quedaba
                    // huérfano (el ViewModel solo atrapa el error, no conoce el
                    // archivo). Se relanza para que el ViewModel lo reporte.
                    outputFile?.delete()
                    throw e
                } catch (e: Exception) {
                    // Solo el tipo: CrashlyticsTree reenvía todo >= WARN a Firebase
                    // y e.message puede contener rutas/URIs reales.
                    Timber.e("$TAG: error al comprimir: ${e.javaClass.simpleName}")
                    outputFile?.delete()
                    PdfToolResult.Error(
                        message = String.format(messages.genericError, e.message ?: ""),
                        cause = e,
                    )
                } finally {
                    cacheFile?.delete()
                }
            }

        // internal (no private) para poder testearlas sin tocar PdfRenderer --
        // ver CompressPdfUseCaseTest.
        internal fun scaleFactorFor(quality: Int) =
            when {
                quality >= 80 -> 1.5f
                quality >= 60 -> 1.2f
                quality >= 40 -> 0.9f
                else -> 0.6f
            }

        internal fun resultMessage(
            messages: CompressPdfMessages,
            keepOriginal: Boolean,
            originalKb: Long,
            finalKb: Long,
            reduction: Int,
        ) = if (keepOriginal) {
            String.format(messages.alreadyOptimized, originalKb)
        } else {
            String.format(messages.success, originalKb, finalKb, reduction)
        }

        private suspend fun renderAndCompressPages(
            renderer: PdfRenderer,
            scaleFactor: Float,
            quality: Int,
        ): android.graphics.pdf.PdfDocument {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            // Ronda 15: si una página fallaba a mitad del bucle (OOM,
            // cancelación), el PdfDocument ya creado (con las páginas previas en
            // memoria) se perdía sin cerrar -- solo el llamador lo cerraba en el
            // camino feliz.
            var completed = false
            try {
                for (i in 0 until renderer.pageCount) {
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): sin ningún punto de suspensión en este bucle,
                    // cancelar la corrutina (ej. el usuario navega hacia atrás
                    // mientras comprime) nunca se notaba hasta que todas las
                    // páginas terminaban solas en segundo plano.
                    coroutineContext.ensureActive()
                    addCompressedPage(pdfDocument, renderer, i, scaleFactor, quality)
                    Timber.d("$TAG: página ${i + 1} procesada")
                }
                completed = true
            } finally {
                if (!completed) pdfDocument.close()
            }
            return pdfDocument
        }

        private fun addCompressedPage(
            pdfDocument: android.graphics.pdf.PdfDocument,
            renderer: PdfRenderer,
            index: Int,
            scaleFactor: Float,
            quality: Int,
        ) {
            // .use{}: page.close() manual se salteaba si createBitmap/render
            // lanzaban (OOM), dejando la página del renderer abierta.
            val bitmap =
                renderer.openPage(index).use { page ->
                    val width = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                    val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                    val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    rendered.eraseColor(android.graphics.Color.WHITE)
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    rendered
                }
            var compressed: Bitmap = bitmap
            try {
                compressed = recompressBitmap(bitmap, quality)
                val pageInfo =
                    android.graphics.pdf.PdfDocument.PageInfo
                        .Builder(bitmap.width, bitmap.height, index + 1).create()
                val docPage = pdfDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(compressed, 0f, 0f, null)
                pdfDocument.finishPage(docPage)
            } finally {
                bitmap.recycle()
                if (compressed !== bitmap) compressed.recycle()
            }
        }

        private fun recompressBitmap(
            bitmap: Bitmap,
            quality: Int,
        ): Bitmap {
            return try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val bytes = stream.toByteArray()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
            } catch (e: Exception) {
                Timber.e("$TAG: error recomprimiendo: ${e.javaClass.simpleName}")
                bitmap
            }
        }

        // internal para poder testear el borrado del archivo de cache parcial.
        internal fun copyUriToCache(uri: Uri): File? {
            val file = File(context.cacheDir, "compress_${System.currentTimeMillis()}.pdf")
            return try {
                val bytes =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                Timber.d("$TAG: copiados $bytes bytes al cache")
                // Ronda 15: con 0 bytes o stream nulo se devolvía null dejando el
                // archivo (vacío o parcial) huérfano en cacheDir -- cacheFile del
                // llamador todavía era null, así que su finally nunca lo borraba.
                if (bytes == null || bytes == 0L) {
                    file.delete()
                    null
                } else {
                    file
                }
            } catch (e: CancellationException) {
                file.delete()
                throw e
            } catch (e: Exception) {
                Timber.e("$TAG: error copiando URI al cache: ${e.javaClass.simpleName}")
                file.delete()
                null
            }
        }

        private fun createOutputFile(name: String): File {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
            return File(dir, "DocuSmart_${name}_$timestamp.pdf")
        }
    }
