package com.docsmart.core.media

import android.graphics.drawable.BitmapDrawable
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.request.Options
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.converter.domain.usecase.writeTestImage
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/** Miniatura de PDF con PdfRenderer real: ancho fijo de 300 px, proporción de la página y fondo blanco. */
class PdfThumbnailFetcherInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private val loader = mockk<ImageLoader>(relaxed = true)

    @Before
    fun setUp() {
        ctx = newIsolatedContext("thumb")
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun fetcherFor(file: File) = PdfThumbnailFetcher(ctx) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }

    @Test
    fun laMiniaturaTiene300PxDeAnchoYConservaLaProporcion() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2, width = 200, height = 300)

        val result = runBlocking { fetcherFor(pdf).fetch() }

        assertTrue(result is DrawableResult)
        result as DrawableResult
        assertEquals(DataSource.DISK, result.dataSource)
        assertTrue(result.isSampled)
        val bitmap = (result.drawable as BitmapDrawable).bitmap
        assertEquals(300, bitmap.width)
        assertEquals(450, bitmap.height)
    }

    @Test
    fun elFondoSinContenidoEsBlancoYNoTransparente() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), width = 100, height = 100)

        val result = runBlocking { fetcherFor(pdf).fetch() } as DrawableResult

        val bitmap = (result.drawable as BitmapDrawable).bitmap
        val corner = bitmap.getPixel(bitmap.width - 2, bitmap.height - 2)
        assertEquals(0xFF, corner ushr 24)
        assertTrue((corner shr 16 and 0xFF) > 230 && (corner shr 8 and 0xFF) > 230 && (corner and 0xFF) > 230)
    }

    @Test
    fun unPdfCorruptoLanzaEnVezDeDevolverUnaMiniaturaVacia() {
        val broken = ctx.inputFile("roto.pdf").apply { writeText("%PDF-1.4 no es valido") }

        try {
            runBlocking { fetcherFor(broken).fetch() }
            fail("un PDF corrupto debe lanzar para que Coil use el ícono de respaldo")
        } catch (e: java.io.IOException) {
            assertNotNull(e)
        } catch (e: SecurityException) {
            assertNotNull(e)
        }
    }

    @Test
    fun fileFactorySoloAceptaArchivosPdf() {
        val pdf = writeAndroidPdf(ctx.inputFile("doc.PDF"))
        val png = writeTestImage(ctx.inputFile("img.png"))
        val factory = PdfThumbnailFetcher.FileFactory()

        val forPdf = factory.create(pdf, Options(ctx), loader)
        val forPng = factory.create(png, Options(ctx), loader)

        assertNotNull(forPdf)
        assertNull(forPng)
        val result = runBlocking { forPdf!!.fetch() }
        assertTrue(result is DrawableResult)
    }

    @Test
    fun uriFactoryReconocePdfPorExtensionYGeneraLaMiniatura() {
        val pdf = writeAndroidPdf(ctx.inputFile("doc.pdf"), width = 300, height = 300)
        val png = writeTestImage(ctx.inputFile("img.png"))
        val factory = PdfThumbnailFetcher.UriFactory()

        val forPdf = factory.create(uriOf(pdf), Options(ctx), loader)
        val forPng = factory.create(uriOf(png), Options(ctx), loader)

        assertNotNull(forPdf)
        assertNull(forPng)
        val bitmap = ((runBlocking { forPdf!!.fetch() }) as DrawableResult).drawable as BitmapDrawable
        assertEquals(300 to 300, bitmap.bitmap.width to bitmap.bitmap.height)
    }
}
