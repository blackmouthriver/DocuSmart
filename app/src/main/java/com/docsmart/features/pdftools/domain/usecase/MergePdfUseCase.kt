package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class MergePdfMessages(
    val minPdfsError: String,
    val readError: String,
    val generateError: String,
    // formato: %1$d archivos, %2$d páginas
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
    // Hallazgo real de la auditoría general 2026-09-17 (M5): si una URI
    // falla al copiarse (permiso revocado, archivo movido/borrado entre
    // la selección y la ejecución), la unión seguía con el resto y
    // reportaba "Success" sin avisar cuál se saltó -- formato: %1$d
    // archivo(s) omitido(s).
    val partialWarning: String,
)

class MergePdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            messages: MergePdfMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                if (pdfUris.size < 2) {
                    return@withContext PdfToolResult.Error(messages.minPdfsError)
                }

                val cacheFiles = mutableListOf<File>()
                var skippedCount = 0
                // Revisión adversarial de correctitud (ronda 13): antes se usaba
                // cacheFiles.size como "archivos incluidos" en el mensaje final --
                // pero cacheFiles solo cuenta "se pudo copiar el URI al cache",
                // que casi siempre es true incluso para un archivo protegido/
                // corrupto (copyUriToCache() solo copia bytes, no valida que sea
                // un PDF abrible). Un archivo salteado por copyFilePagesOrNull()
                // quedaba contado DOS veces: una en "N archivos" (vía cacheFiles.
                // size) y otra en "(N no se pudo incluir)". mergedFileCount solo
                // se incrementa cuando el archivo realmente se incorporó al merge.
                var mergedFileCount = 0
                val outputFile = createOutputFile(outputFileName ?: "Merged")
                try {
                    var totalPages = 0

                    PdfDocument(PdfWriter(outputFile)).use { destPdf ->
                        pdfUris.forEach { uri ->
                            coroutineContext.ensureActive()
                            val pagesCopied = mergeOneUri(uri, destPdf, cacheFiles)
                            if (pagesCopied != null) {
                                totalPages += pagesCopied
                                mergedFileCount++
                            } else {
                                skippedCount++
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
                        message = buildSuccessMessage(messages, mergedFileCount, totalPages, skippedCount),
                    )
                } catch (e: CancellationException) {
                    // Hallazgo real de la auditoría r13 (Media): CancellationException
                    // hereda de Exception, así que sin este catch específico antes
                    // del genérico de abajo cada cancelación real (navegar hacia
                    // atrás mientras se unen los PDFs) se registraba como error.
                    // Se relanza tal cual, mismo patrón que CompressPdfUseCase.
                    outputFile.delete()
                    throw e
                } catch (e: OutOfMemoryError) {
                    // Hallazgo real de la auditoría r13 (Media): OutOfMemoryError no
                    // hereda de Exception en Kotlin/Java, así que el catch genérico
                    // de abajo nunca lo atrapaba y outputFile quedaba huérfano.
                    outputFile.delete()
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: error al unir PDFs")
                    // Hallazgo real de la revisión general 2026-09-16 (cuarta
                    // pasada): outputFile SÍ está en scope acá (a diferencia de
                    // las otras herramientas), pero nunca se borraba si
                    // copyPagesTo()/etc. lanzaba a mitad de camino -- mismo patrón
                    // de archivo huérfano que #25-27, solo que por un motivo
                    // distinto (el delete() faltaba, no el scope).
                    outputFile.delete()
                    PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
                } finally {
                    cacheFiles.forEach { it.delete() }
                }
            }

        // Extraído de invoke() -- CyclomaticComplexMethod de detekt tras el fix
        // del Hallazgo 1 de la auditoría r13 (Alta): antes la apertura de cada
        // archivo no tenía su propio try/catch -- un solo PDF protegido con
        // contraseña o corrupto lanzaba una excepción que abortaba TODO el
        // merge, perdiendo incluso las páginas de los archivos anteriores ya
        // copiados a destPdf. Copia el URI al cache y le copia las páginas a
        // destPdf; si cualquiera de los dos pasos falla, devuelve null y el
        // llamador lo cuenta como archivo salteado en vez de abortar el resto
        // (mismo criterio que ya existía para "URI no se pudo copiar").
        private suspend fun mergeOneUri(
            uri: Uri,
            destPdf: PdfDocument,
            cacheFiles: MutableList<File>,
        ): Int? {
            val file =
                copyUriToCache(uri) ?: run {
                    Timber.w("$TAG: no se pudo copiar URI al cache")
                    return null
                }
            cacheFiles.add(file)
            return copyFilePagesOrNull(file, destPdf)
        }

        private suspend fun copyFilePagesOrNull(
            file: File,
            destPdf: PdfDocument,
        ): Int? =
            try {
                PdfDocument(PdfReader(file)).use { sourcePdf ->
                    val pages = sourcePdf.numberOfPages
                    if (pages > 0) {
                        sourcePdf.copyPagesTo(1, pages, destPdf)
                    }
                    pages
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "$TAG: no se pudo abrir/copiar un archivo (protegido o corrupto)")
                null
            }

        // Extraído de invoke() -- CyclomaticComplexMethod de detekt tras M5.
        // Hallazgo real M5: si se salteó al menos un archivo, se avisa en el
        // mismo mensaje de éxito en vez de reportar "Success" liso, para que el
        // usuario sepa que el PDF resultante tiene menos archivos de los que
        // eligió.
        private fun buildSuccessMessage(
            messages: MergePdfMessages,
            mergedCount: Int,
            totalPages: Int,
            skippedCount: Int,
        ): String {
            val successMessage = String.format(messages.success, mergedCount, totalPages)
            return if (skippedCount > 0) {
                "$successMessage ${String.format(messages.partialWarning, skippedCount)}"
            } else {
                successMessage
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
