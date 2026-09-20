package com.docsmart.features.converter.domain.usecase

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Casos de uso reales con Bitmap/PdfDocument/PdfRenderer del framework Android (en JVM no se pueden
 * probar): entradas sintéticas en una carpeta aislada, salida verificada abriendo el PDF resultante.
 */
class ConvertImageToPdfInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var useCase: ConvertImageToPdfUseCase

    @Before
    fun setUp() {
        ctx = newIsolatedContext("img2pdf")
        useCase = ConvertImageToPdfUseCase(ctx)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun convert(
        images: List<File>,
        name: String = "salida",
        highRes: Boolean = false,
    ): ConversionResult = runBlocking { useCase(images.map(::uriOf), name, highRes) }

    @Test
    fun unaImagenGeneraUnPdfA4DeUnaPagina() {
        val image = writeTestImage(ctx.inputFile("a.png"))

        val result = convert(listOf(image))

        assertTrue(result is ConversionResult.Success)
        result as ConversionResult.Success
        assertEquals(1, result.pageCount)
        assertEquals("salida.pdf", result.outputFile.name)
        assertEquals(ctx.outputDir("converted"), result.outputFile.parentFile)
        assertEquals(1, renderedPageCount(result.outputFile))
        assertEquals(595 to 842, renderedPageSize(result.outputFile))
    }

    @Test
    fun variasImagenesGeneranUnaPaginaCadaUna() {
        val images =
            listOf(
                writeTestImage(ctx.inputFile("a.png")),
                writeTestImage(ctx.inputFile("b.jpg"), format = Bitmap.CompressFormat.JPEG),
                writeTestImage(ctx.inputFile("c.png"), width = 60, height = 200),
            )

        val result = convert(images)

        assertTrue(result is ConversionResult.Success)
        result as ConversionResult.Success
        assertEquals(3, result.pageCount)
        assertEquals(3, renderedPageCount(result.outputFile))
        assertTrue(result.fileSizeKb >= 0)
    }

    @Test
    fun unaImagenIlegibleSeSaltaYLasDemasSeConvierten() {
        val good = writeTestImage(ctx.inputFile("ok.png"))
        val missing = ctx.inputFile("no_existe.png")
        val garbage = ctx.inputFile("basura.png").apply { writeText("esto no es una imagen") }

        val result = convert(listOf(missing, good, garbage))

        assertTrue(result is ConversionResult.Success)
        result as ConversionResult.Success
        assertEquals(1, result.pageCount)
        assertEquals(1, renderedPageCount(result.outputFile))
    }

    @Test
    fun siNingunaImagenSePuedeCargarDevuelveError() {
        val missing = ctx.inputFile("no_existe.png")
        val garbage = ctx.inputFile("basura.png").apply { writeText("x") }

        val result = convert(listOf(missing, garbage))

        assertTrue(result is ConversionResult.Error)
        assertEquals(
            ctx.getString(R.string.converter_error_no_images_loaded),
            (result as ConversionResult.Error).message,
        )
        assertTrue(ctx.outputDir("converted").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun listaVaciaDevuelveErrorDeSeleccion() {
        val result = convert(emptyList())

        assertTrue(result is ConversionResult.Error)
        assertEquals(
            ctx.getString(R.string.converter_error_no_images_selected),
            (result as ConversionResult.Error).message,
        )
    }

    @Test
    fun imagenGrandeSeReescalaEnResolucionEstandarYAlta() {
        val big = writeTestImage(ctx.inputFile("grande.png"), width = 2400, height = 3200)

        val standard = convert(listOf(big), name = "std", highRes = false)
        val high = convert(listOf(big), name = "alta", highRes = true)

        assertTrue(standard is ConversionResult.Success)
        assertTrue(high is ConversionResult.Success)
        assertEquals(1, renderedPageCount((standard as ConversionResult.Success).outputFile))
        assertEquals(1, renderedPageCount((high as ConversionResult.Success).outputFile))
    }

    @Test
    fun nombreDeSalidaConRutaInexistenteDevuelveErrorYNoDejaArchivos() {
        val image = writeTestImage(ctx.inputFile("a.png"))

        val result = convert(listOf(image), name = "carpeta_inexistente/otra/salida")

        assertTrue(result is ConversionResult.Error)
        assertTrue(ctx.outputDir("converted").walkTopDown().none { it.isFile })
    }

    @Test
    fun nombreDePorDefectoUsaPrefijoConversion() {
        val image = writeTestImage(ctx.inputFile("a.png"))

        val result = runBlocking { useCase(listOf(uriOf(image))) }

        assertTrue(result is ConversionResult.Success)
        assertTrue((result as ConversionResult.Success).outputFile.name.startsWith("Conversion_"))
    }

    @Test
    fun loadBitmapAplicaLaOrientacionExifDeUnaFotoVertical() {
        val jpg =
            writeTestImage(
                ctx.inputFile("foto.jpg"),
                width = 200,
                height = 100,
                format = Bitmap.CompressFormat.JPEG,
            )
        ExifInterface(jpg.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val bitmap = useCase.loadBitmapFromUri(uriOf(jpg))

        assertNotNull(bitmap)
        assertEquals(100, bitmap!!.width)
        assertEquals(200, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun loadBitmapDevuelveNullSiElArchivoNoExisteONoEsImagen() {
        assertNull(useCase.loadBitmapFromUri(uriOf(ctx.inputFile("no_existe.jpg"))))
        assertNull(useCase.loadBitmapFromUri(uriOf(ctx.inputFile("texto.jpg").apply { writeText("hola") })))
    }

    @Test
    fun readExifOrientationLeeElTagYCaeANormalSinMetadatos() {
        val jpg = writeTestImage(ctx.inputFile("o.jpg"), format = Bitmap.CompressFormat.JPEG)
        ExifInterface(jpg.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_270.toString())
            saveAttributes()
        }
        val png = writeTestImage(ctx.inputFile("sin_exif.png"))

        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, readExifOrientation(ctx, uriOf(jpg)))
        // Sin metadatos EXIF (o sin archivo) no hay giro que aplicar: NORMAL o UNDEFINED.
        val noTransform = setOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(readExifOrientation(ctx, uriOf(png)) in noTransform)
        assertTrue(readExifOrientation(ctx, uriOf(ctx.inputFile("no_existe.jpg"))) in noTransform)
    }

    @Test
    fun applyExifOrientationTransformaCadaOrientacion() {
        val swapsDimensions =
            mapOf(
                ExifInterface.ORIENTATION_ROTATE_90 to true,
                ExifInterface.ORIENTATION_ROTATE_180 to false,
                ExifInterface.ORIENTATION_ROTATE_270 to true,
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL to false,
                ExifInterface.ORIENTATION_FLIP_VERTICAL to false,
                ExifInterface.ORIENTATION_TRANSPOSE to true,
                ExifInterface.ORIENTATION_TRANSVERSE to true,
            )
        swapsDimensions.forEach { (orientation, swaps) ->
            val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)

            val result = applyExifOrientation(source, orientation)

            val expected = if (swaps) 20 to 40 else 40 to 20
            assertEquals("orientacion $orientation", expected, result.width to result.height)
            result.recycle()
        }
    }

    @Test
    fun applyExifOrientationNormalDevuelveElMismoBitmap() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertSame(source, applyExifOrientation(source, ExifInterface.ORIENTATION_NORMAL))
        assertSame(source, applyExifOrientation(source, ExifInterface.ORIENTATION_UNDEFINED))
        source.recycle()
    }
}
