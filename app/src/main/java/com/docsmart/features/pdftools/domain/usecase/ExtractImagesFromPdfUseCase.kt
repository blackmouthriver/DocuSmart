package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfIndirectReference
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExtractImagesMessages(
    val readError: String,
    val noPages: String,
    // AC2: PDF sin imágenes embebidas
    val noImages: String,
    // formato: %1$d imágenes extraídas
    val success: String,
    // formato: %1$s mensaje de excepción
    val genericError: String,
)

// HU-53 (backlog UX 2026-09-10): extrae las imágenes embebidas de un PDF
// como archivos JPG/PNG individuales. Único use case de las 15 herramientas
// PDF que produce N archivos de salida en vez de uno -- ver
// PdfToolResult.MultiSuccess.
class ExtractImagesFromPdfUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ExtractImagesFromPdfUseCase"
            private const val DEFAULT_EXTENSION = "jpg"
        }

        // El catch de más abajo es el límite de la operación completa (lectura
        // del PDF + recorrido de páginas + escritura de N archivos) -- iText7 y
        // las APIs de archivos de Android no documentan un conjunto acotado de
        // excepciones esperables, mismo criterio que el resto de las 15
        // herramientas PDF de este paquete.
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            pdfUri: Uri,
            outputFileName: String? = null,
            messages: ExtractImagesMessages,
        ): PdfToolResult =
            withContext(Dispatchers.IO) {
                var cacheFile: File? = null
                // `val` mutable in vez de reasignar el resultado de extractAllImages()
                // al volver: si esta lanza a mitad de camino (ej. OutOfMemoryError en
                // la imagen 10 de 50), las primeras 9 ya escritas en disco deben
                // seguir siendo visibles acá para que el catch de abajo las borre --
                // con una reasignación al final, un throw a mitad de camino las
                // dejaba huérfanas para siempre (mismo bug de fondo ya corregido en
                // Compress/Split/Ocr para su propio outputFile único).
                val savedFiles = mutableListOf<File>()
                try {
                    cacheFile = copyUriToCache(pdfUri)
                        ?: return@withContext PdfToolResult.Error(messages.readError)

                    val baseName = outputFileName ?: "Imagen"
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                    PdfDocument(PdfReader(cacheFile)).use { pdfDoc ->
                        if (pdfDoc.numberOfPages == 0) {
                            return@withContext PdfToolResult.Error(messages.noPages)
                        }
                        Timber.d("$TAG: PDF abierto — ${pdfDoc.numberOfPages} páginas")
                        extractAllImages(pdfDoc, baseName, timestamp, savedFiles)
                    }

                    if (savedFiles.isEmpty()) {
                        return@withContext PdfToolResult.Error(messages.noImages)
                    }

                    Timber.d("$TAG: ${savedFiles.size} imágenes extraídas")
                    PdfToolResult.MultiSuccess(
                        outputFiles = savedFiles,
                        message = String.format(messages.success, savedFiles.size),
                    )
                } catch (e: Exception) {
                    Timber.e("$TAG: error al extraer imágenes: ${e.javaClass.simpleName}")
                    savedFiles.forEach { it.delete() }
                    PdfToolResult.Error(
                        message = String.format(messages.genericError, e.message ?: ""),
                        cause = e,
                    )
                } finally {
                    cacheFile?.delete()
                }
            }

        // Un mismo XObject puede estar referenciado desde varias páginas (ej. un
        // logo repetido) -- se deduplica por referencia indirecta (`seen`) para
        // no extraer la misma imagen física más de una vez.
        private fun extractAllImages(
            pdfDoc: PdfDocument,
            baseName: String,
            timestamp: String,
            savedFiles: MutableList<File>,
        ) {
            val seen = mutableSetOf<PdfIndirectReference>()
            for (pageNumber in 1..pdfDoc.numberOfPages) {
                val resources = pdfDoc.getPage(pageNumber).resources?.pdfObject ?: continue
                val images = mutableListOf<PdfImageXObject>()
                collectImageXObjects(resources, seen, images)
                images.forEach { image ->
                    val bytes = readImageBytes(image, pageNumber) ?: return@forEach
                    val extension = image.identifyImageFileExtension() ?: DEFAULT_EXTENSION
                    val file = createOutputFile(baseName, savedFiles.size + 1, extension, timestamp)
                    file.writeBytes(bytes)
                    savedFiles.add(file)
                }
            }
        }

        // Cualquier imagen individual corrupta o con una codificación que iText
        // no pueda decodificar no debe abortar la extracción completa del resto
        // -- se salta esa imagen puntual en vez de fallar todo el PDF.
        @Suppress("TooGenericExceptionCaught")
        private fun readImageBytes(
            image: PdfImageXObject,
            pageNumber: Int,
        ): ByteArray? =
            try {
                image.imageBytes?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Timber.e("$TAG: no se pudo decodificar una imagen (página $pageNumber): ${e.javaClass.simpleName}")
                null
            }

        // Recorre también los Form XObjects (imágenes que un generador de PDF
        // envuelve en un formulario en vez de referenciarlas directo desde la
        // página, común en documentos exportados desde Word/Office) -- sin esto,
        // AC1 ("PDF con 5 imágenes -> 5 archivos") fallaría para ese formato.
        private fun collectImageXObjects(
            resources: PdfDictionary,
            seen: MutableSet<PdfIndirectReference>,
            out: MutableList<PdfImageXObject>,
        ) {
            val xobjects = resources.getAsDictionary(PdfName.XObject) ?: return
            for (name in xobjects.keySet()) {
                val stream = xobjects.get(name) as? PdfStream ?: continue
                val ref = stream.indirectReference
                if (ref == null || seen.add(ref)) {
                    addImageOrRecurseIntoForm(stream, seen, out)
                }
            }
        }

        private fun addImageOrRecurseIntoForm(
            stream: PdfStream,
            seen: MutableSet<PdfIndirectReference>,
            out: MutableList<PdfImageXObject>,
        ) {
            when (stream.getAsName(PdfName.Subtype)) {
                PdfName.Image -> out.add(PdfImageXObject(stream))
                PdfName.Form -> stream.getAsDictionary(PdfName.Resources)?.let { collectImageXObjects(it, seen, out) }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun copyUriToCache(uri: Uri): File? {
            return try {
                val file = File(context.cacheDir, "extractimg_${System.currentTimeMillis()}.pdf")
                val input = context.contentResolver.openInputStream(uri) ?: return null
                val bytes = input.use { inStream -> file.outputStream().use { inStream.copyTo(it) } }
                if (bytes == 0L) {
                    // Evita dejar el archivo vacío huérfano en cacheDir.
                    file.delete()
                    null
                } else {
                    file
                }
            } catch (e: Exception) {
                Timber.e("$TAG: error copiando URI al cache: ${e.javaClass.simpleName}")
                null
            }
        }

        private fun createOutputFile(
            baseName: String,
            index: Int,
            extension: String,
            timestamp: String,
        ): File {
            val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
            return File(dir, "DocuSmart_${baseName}_${index}_$timestamp.$extension")
        }
    }
