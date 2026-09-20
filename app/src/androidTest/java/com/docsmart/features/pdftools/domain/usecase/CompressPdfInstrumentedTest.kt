package com.docsmart.features.pdftools.domain.usecase

import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.renderedPageCount
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** CompressPdfUseCase con PdfRenderer/Bitmap/PdfDocument reales (rasteriza y recomprime cada página). */
class CompressPdfInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var useCase: CompressPdfUseCase

    private val messages =
        CompressPdfMessages(
            readError = "readError",
            emptyFile = "emptyFile",
            noPages = "noPages",
            generateError = "generateError",
            alreadyOptimized = "yaOptimizado %1\$d",
            success = "ok %1\$d %2\$d %3\$d",
            genericError = "generico %1\$s",
        )

    @Before
    fun setUp() {
        ctx = newIsolatedContext("compress")
        useCase = CompressPdfUseCase(ctx)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun compress(
        pdf: File,
        quality: Int = 60,
        name: String? = null,
    ): PdfToolResult = runBlocking { useCase(uriOf(pdf), quality, name, messages) }

    @Test
    fun cualquierCalidadProduceUnPdfValidoConLasMismasPaginas() {
        val pdf = writeAndroidPdf(ctx.inputFile("tres.pdf"), pages = 3, width = 300, height = 400)

        // Recorre los 4 tramos de scaleFactorFor: >=80, >=60, >=40 y el resto.
        listOf(90, 60, 40, 10).forEach { quality ->
            val result = compress(pdf, quality = quality, name = "q$quality")

            assertTrue("calidad $quality: $result", result is PdfToolResult.Success)
            result as PdfToolResult.Success
            assertEquals(3, renderedPageCount(result.outputFile))
            assertTrue(result.outputFile.name.startsWith("DocuSmart_q${quality}_"))
            assertEquals(ctx.outputDir("pdftools"), result.outputFile.parentFile)
        }
    }

    @Test
    fun elMensajeCorrespondeAlDesenlaceRealDeLaCompresion() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"), pages = 1)
        val originalKb = pdf.length() / 1024

        val result = compress(pdf, quality = 60)

        assertTrue(result is PdfToolResult.Success)
        result as PdfToolResult.Success
        val finalKb = result.outputFile.length() / 1024
        if (result.message.startsWith("yaOptimizado")) {
            // Se conserva el original: el archivo comprimido más grande se descarta.
            assertEquals("yaOptimizado $originalKb", result.message)
            assertTrue(result.outputFile.name.contains("_optimizado_"))
            assertEquals(pdf.length(), result.outputFile.length())
        } else {
            assertTrue(result.message.startsWith("ok $originalKb $finalKb "))
            assertTrue(result.outputFile.length() < pdf.length())
        }
        // Nunca queda el archivo comprimido descartado en pdftools: solo el resultado final.
        assertEquals(1, ctx.outputDir("pdftools").listFiles().orEmpty().size)
    }

    @Test
    fun nombreDeSalidaPorDefectoIncluyeLaCalidad() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"))

        val result = compress(pdf, quality = 75) as PdfToolResult.Success

        assertTrue(result.outputFile.name.contains("Compressed_q75"))
    }

    @Test
    fun noDejaTemporalesEnCacheTrasComprimir() {
        val pdf = writeAndroidPdf(ctx.inputFile("uno.pdf"))

        compress(pdf)

        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun archivoVacioOInexistenteDevuelveErrorDeLectura() {
        val empty = ctx.inputFile("vacio.pdf").apply { writeBytes(ByteArray(0)) }

        val fromEmpty = compress(empty)
        val fromMissing = compress(ctx.inputFile("no_existe.pdf"))

        assertEquals(PdfToolResult.Error("readError"), fromEmpty)
        assertEquals(PdfToolResult.Error("readError"), fromMissing)
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun copyUriToCacheDevuelveNullYNoDejaArchivoConOrigenVacio() {
        val empty = ctx.inputFile("vacio.pdf").apply { writeBytes(ByteArray(0)) }
        val good = writeAndroidPdf(ctx.inputFile("uno.pdf"))

        assertEquals(null, useCase.copyUriToCache(uriOf(empty)))
        val copied = useCase.copyUriToCache(uriOf(good))

        assertNotNull(copied)
        assertEquals(good.length(), copied!!.length())
        assertEquals(1, ctx.cacheDir.listFiles().orEmpty().size)
    }

    @Test
    fun pdfCorruptoDevuelveErrorGenericoConCausaYSinHuerfanos() {
        val broken = ctx.inputFile("roto.pdf").apply { writeText("%PDF-1.4 no es un pdf") }

        val result = compress(broken)

        assertTrue(result is PdfToolResult.Error)
        result as PdfToolResult.Error
        assertTrue(result.message.startsWith("generico"))
        assertNotNull(result.cause)
        assertTrue(ctx.outputDir("pdftools").listFiles().orEmpty().isEmpty())
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }
}
