package com.docsmart.features.converter.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ImageFormatUseCase con Bitmap real: codificación por formato, aplanado de transparencia y BMP. */
class ImageFormatInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var useCase: ImageFormatUseCase

    @Before
    fun setUp() {
        ctx = newIsolatedContext("imgfmt")
        useCase = ImageFormatUseCase(ctx)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun convert(
        input: File,
        type: ConversionType,
        name: String? = "salida",
    ): ConversionResult = runBlocking { useCase(uriOf(input), type, name) }

    private fun success(result: ConversionResult): ConversionResult.Success {
        assertTrue("se esperaba Success pero fue $result", result is ConversionResult.Success)
        return result as ConversionResult.Success
    }

    private fun decode(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("no se pudo decodificar ${file.name}", bitmap)
        return bitmap
    }

    private fun transparentPng(name: String): File {
        val file = ctx.inputFile(name)
        val bitmap = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun pngAJpgConservaTamanoYExtension() {
        val source = writeTestImage(ctx.inputFile("a.png"), width = 90, height = 60)

        val result = success(convert(source, ConversionType.IMAGE_TO_JPG))

        assertEquals("salida.jpg", result.outputFile.name)
        assertEquals(1, result.pageCount)
        assertEquals(ctx.outputDir("converted"), result.outputFile.parentFile)
        val decoded = decode(result.outputFile)
        assertEquals(90 to 60, decoded.width to decoded.height)
        decoded.recycle()
    }

    @Test
    fun transparenciaSeAplanaSobreBlancoAlConvertirAJpg() {
        val result = success(convert(transparentPng("t.png"), ConversionType.IMAGE_TO_JPG))

        val decoded = decode(result.outputFile)
        val pixel = decoded.getPixel(2, 2)
        assertTrue("el fondo debe ser blanco, no negro", Color.red(pixel) > 240 && Color.green(pixel) > 240)
        decoded.recycle()
    }

    @Test
    fun transparenciaSeConservaAlConvertirAPng() {
        val result = success(convert(transparentPng("t.png"), ConversionType.IMAGE_TO_PNG))

        assertEquals("salida.png", result.outputFile.name)
        val decoded = decode(result.outputFile)
        assertEquals(0, Color.alpha(decoded.getPixel(2, 2)))
        decoded.recycle()
    }

    @Test
    fun jpgAWebpGeneraUnaImagenDecodificable() {
        val source = writeTestImage(ctx.inputFile("a.jpg"), format = Bitmap.CompressFormat.JPEG)

        val result = success(convert(source, ConversionType.IMAGE_TO_WEBP))

        assertEquals("salida.webp", result.outputFile.name)
        val decoded = decode(result.outputFile)
        assertEquals(120 to 80, decoded.width to decoded.height)
        decoded.recycle()
    }

    @Test
    fun conversionABmpEscribeUnBmpDe24BitsValido() {
        val source = writeTestImage(ctx.inputFile("a.png"), width = 5, height = 3)

        val result = success(convert(source, ConversionType.IMAGE_TO_BMP))

        assertEquals("salida.bmp", result.outputFile.name)
        val bytes = result.outputFile.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals('B'.code.toByte(), bytes[0])
        assertEquals('M'.code.toByte(), bytes[1])
        assertEquals(bytes.size, header.getInt(2))
        assertEquals(5, header.getInt(18))
        assertEquals(3, header.getInt(22))
        assertEquals(24, header.getShort(28).toInt())
        // 5 px * 3 bytes = 15 -> se rellena a 16 por fila; 3 filas + 54 de cabecera.
        assertEquals(54 + 16 * 3, bytes.size)
        assertDecodableAsBmp(result.outputFile, 5, 3)
    }

    private fun assertDecodableAsBmp(
        file: File,
        width: Int,
        height: Int,
    ) {
        val decoded = decode(file)
        assertEquals(width to height, decoded.width to decoded.height)
        decoded.recycle()
    }

    @Test
    fun bmpAplanaLaTransparenciaSobreBlanco() {
        val result = success(convert(transparentPng("t.png"), ConversionType.IMAGE_TO_BMP))

        val decoded = decode(result.outputFile)
        val pixel = decoded.getPixel(1, 1)
        assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
        decoded.recycle()
    }

    @Test
    fun bitmapToBmpEmpaquetaPixelesBgrDeAbajoHaciaArriba() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.rgb(10, 20, 30))
        bitmap.setPixel(1, 0, Color.rgb(40, 50, 60))
        bitmap.setPixel(0, 1, Color.rgb(70, 80, 90))
        bitmap.setPixel(1, 1, Color.rgb(100, 110, 120))

        val bytes = useCase.bitmapToBmp(bitmap)
        bitmap.recycle()

        // 2 px * 3 = 6 bytes + 2 de relleno por fila; la primera fila guardada es la de abajo (y = 1).
        assertEquals(54 + 8 * 2, bytes.size)
        val firstPixel = bytes.copyOfRange(54, 57).map { it.toInt() and 0xFF }
        assertEquals(listOf(90, 80, 70), firstPixel)
        val topRowFirst = bytes.copyOfRange(54 + 8, 54 + 8 + 3).map { it.toInt() and 0xFF }
        assertEquals(listOf(30, 20, 10), topRowFirst)
    }

    @Test
    fun tipoNoSoportadoCaeAJpg() {
        val source = writeTestImage(ctx.inputFile("a.png"))

        val result = success(convert(source, ConversionType.IMAGE_TO_PDF))

        assertEquals("salida.jpg", result.outputFile.name)
    }

    @Test
    fun aplicaLaOrientacionExifAntesDeConvertir() {
        val jpg =
            writeTestImage(ctx.inputFile("foto.jpg"), width = 200, height = 100, format = Bitmap.CompressFormat.JPEG)
        ExifInterface(jpg.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val result = success(convert(jpg, ConversionType.IMAGE_TO_PNG))

        val decoded = decode(result.outputFile)
        assertEquals(100 to 200, decoded.width to decoded.height)
        decoded.recycle()
    }

    @Test
    fun sinNombreUsaMarcaDeTiempo() {
        val source = writeTestImage(ctx.inputFile("a.png"))

        val result = success(convert(source, ConversionType.IMAGE_TO_PNG, name = null))

        assertTrue(result.outputFile.name.matches(Regex("\\d{8}_\\d{6}\\.png")))
    }

    @Test
    fun archivoInexistenteODanadoDevuelveErrorDeLectura() {
        val expected = ctx.getString(R.string.converter_error_read_image)
        val garbage = ctx.inputFile("basura.png").apply { writeText("no soy una imagen") }

        val missing = convert(ctx.inputFile("no_existe.png"), ConversionType.IMAGE_TO_JPG)
        val broken = convert(garbage, ConversionType.IMAGE_TO_JPG)

        // El archivo inexistente lanza FileNotFoundException (error genérico); el dañado no decodifica.
        assertTrue(missing is ConversionResult.Error)
        assertEquals(expected, (broken as ConversionResult.Error).message)
    }

    @Test
    fun destinoNoEscribibleDevuelveErrorYNoDejaArchivos() {
        val source = writeTestImage(ctx.inputFile("a.png"))

        val result = convert(source, ConversionType.IMAGE_TO_PNG, name = "no_existe/sub/salida")

        assertTrue(result is ConversionResult.Error)
        assertTrue(ctx.outputDir("converted").walkTopDown().none { it.isFile })
    }
}
