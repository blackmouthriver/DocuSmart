package com.docsmart.features.converter.domain.usecase

import android.graphics.BitmapFactory
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** PdfToImageUseCase con PdfRenderer/Bitmap reales: un JPG por página, limpieza de temporales y errores. */
class PdfToImageInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var useCase: PdfToImageUseCase

    @Before
    fun setUp() {
        ctx = newIsolatedContext("pdf2img")
        useCase = PdfToImageUseCase(ctx)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun convert(
        pdf: File,
        name: String? = "doc",
    ): ConversionResult = runBlocking { useCase(uriOf(pdf), name) }

    @Test
    fun cadaPaginaSeConvierteEnUnJpgAlDobleDeTamano() {
        val pdf = writeAndroidPdf(ctx.inputFile("tres.pdf"), pages = 3, width = 200, height = 300)

        val result = convert(pdf)

        assertTrue(result is ConversionResult.Success)
        result as ConversionResult.Success
        assertEquals(3, result.pageCount)
        assertEquals(2, result.extraFiles.size)
        assertEquals("doc_pagina1.jpg", result.outputFile.name)
        assertEquals(
            listOf("doc_pagina2.jpg", "doc_pagina3.jpg"),
            result.extraFiles.map { it.name },
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(result.outputFile.absolutePath, bounds)
        assertEquals(400 to 600, bounds.outWidth to bounds.outHeight)
        assertTrue(result.fileSizeKb >= 0)
    }

    @Test
    fun laPaginaRenderizadaTieneFondoBlancoYContenido() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"), pages = 1, width = 100, height = 100)

        val result = convert(pdf) as ConversionResult.Success

        val bitmap = BitmapFactory.decodeFile(result.outputFile.absolutePath)
        assertNotNull(bitmap)
        // Esquina inferior derecha: fondo blanco. Centro-superior del rectángulo azul: no blanca.
        val corner = bitmap.getPixel(bitmap.width - 2, bitmap.height - 2)
        assertTrue((corner shr 16 and 0xFF) > 230 && (corner shr 8 and 0xFF) > 230)
        val painted = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        assertTrue((painted and 0xFF) > (painted shr 16 and 0xFF))
        bitmap.recycle()
    }

    @Test
    fun noDejaArchivosTemporalesEnCache() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"))

        convert(pdf)

        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sinNombreUsaMarcaDeTiempo() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"))

        val result = convert(pdf, name = null) as ConversionResult.Success

        assertTrue(result.outputFile.name.matches(Regex("\\d{8}_\\d{6}_pagina1\\.jpg")))
    }

    @Test
    fun pdfVacioDevuelveErrorDeLectura() {
        val empty = ctx.inputFile("vacio.pdf").apply { writeBytes(ByteArray(0)) }

        val result = convert(empty)

        assertTrue(result is ConversionResult.Error)
        assertEquals(ctx.getString(R.string.converter_error_read_pdf), (result as ConversionResult.Error).message)
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun pdfCorruptoDevuelveErrorYNoDejaHuerfanos() {
        val broken = ctx.inputFile("roto.pdf").apply { writeText("%PDF-1.4 esto no es un pdf valido") }

        val result = convert(broken)

        assertTrue(result is ConversionResult.Error)
        assertTrue(ctx.outputDir("converted").walkTopDown().none { it.isFile })
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun archivoInexistenteDevuelveError() {
        val result = convert(ctx.inputFile("no_existe.pdf"))

        assertTrue(result is ConversionResult.Error)
    }

    @Test
    fun nombreConRutaInexistenteDevuelveErrorYBorraLoEscrito() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"), pages = 2)

        val result = convert(pdf, name = "carpeta_falsa/doc")

        assertTrue(result is ConversionResult.Error)
        assertTrue(ctx.outputDir("converted").walkTopDown().none { it.isFile })
    }
}
