package com.docsmart.features.scanner.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * ScanImageEditor.kt con Bitmap, BitmapFactory y FileProvider reales (en JVM solo se
 * pueden probar las funciones puras): escala, brillo/contraste, modos de color, URI
 * inexistente y limpieza de los archivos propios de scanner_edits/.
 */
class ScanImageEditorInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val editor = ScanImageEditor(context)
    private val sources = mutableListOf<File>()
    private val editsDir get() = File(context.cacheDir, "scanner_edits")
    private val startMillis = System.currentTimeMillis()

    @After
    fun limpiar() {
        sources.forEach { it.delete() }
        editsDir.listFiles()?.forEach { if (it.lastModified() >= startMillis - 2_000) it.delete() }
    }

    private fun source(
        color: Int,
        width: Int = 40,
        height: Int = 60,
    ): Uri {
        val file = File(context.cacheDir, "r20_editor_${System.nanoTime()}.jpg").also { sources += it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun decode(uri: Uri): Bitmap =
        checkNotNull(context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) })

    private fun editsCount() = editsDir.listFiles()?.size ?: 0

    @Test
    fun applyAdjustments_escalaA50_reduceLasDimensiones() {
        val result = runBlocking { editor.applyAdjustments(source(Color.GRAY), 0, 0, 50) }

        assertNotNull(result)
        val bitmap = decode(result!!)
        assertEquals(20, bitmap.width)
        assertEquals(30, bitmap.height)
    }

    @Test
    fun applyAdjustments_sinCambios_conservaTamanoYGeneraUnArchivoNuevo() {
        val before = editsCount()

        val result = runBlocking { editor.applyAdjustments(source(Color.GRAY), 0, 0, 100) }

        assertNotNull(result)
        assertEquals(40, decode(result!!).width)
        assertEquals(before + 1, editsCount())
    }

    @Test
    fun applyAdjustments_brilloPositivo_aclaraLaImagen() {
        val gray = Color.rgb(100, 100, 100)
        val result = runBlocking { editor.applyAdjustments(source(gray), 50, 0, 100) }

        val bitmap = decode(result!!)
        assertTrue(Color.red(bitmap.getPixel(5, 5)) > 150)
    }

    @Test
    fun applyAdjustments_brilloNegativo_oscureceLaImagen() {
        val gray = Color.rgb(150, 150, 150)
        val result = runBlocking { editor.applyAdjustments(source(gray), -50, 0, 100) }

        val bitmap = decode(result!!)
        assertTrue(Color.red(bitmap.getPixel(5, 5)) < 100)
    }

    @Test
    fun applyAdjustments_contrasteYEscalaJuntos_devuelveUnaImagenValida() {
        val result = runBlocking { editor.applyAdjustments(source(Color.rgb(90, 140, 200)), 20, 40, 75) }

        val bitmap = decode(result!!)
        assertEquals(30, bitmap.width)
        assertEquals(45, bitmap.height)
    }

    @Test
    fun applyColorMode_color_devuelveLaMismaUriSinProcesar() {
        val uri = source(Color.RED)
        val before = editsCount()

        val result = runBlocking { editor.applyColorMode(uri, ScanColorMode.COLOR) }

        assertEquals(uri, result)
        assertEquals(before, editsCount())
    }

    @Test
    fun applyColorMode_escalaDeGrises_igualaLosCanalesRgb() {
        val uri = source(Color.rgb(200, 50, 50))

        val result = runBlocking { editor.applyColorMode(uri, ScanColorMode.GRAYSCALE) }

        val pixel = decode(result!!).getPixel(5, 5)
        assertTrue(abs(Color.red(pixel) - Color.green(pixel)) <= 6)
        assertTrue(abs(Color.green(pixel) - Color.blue(pixel)) <= 6)
    }

    @Test
    fun applyColorMode_blancoYNegroYResaltarTexto_generanArchivosNuevos() {
        val uri = source(Color.rgb(230, 230, 60))
        val before = editsCount()

        val bw = runBlocking { editor.applyColorMode(uri, ScanColorMode.BLACK_AND_WHITE) }
        val highlight = runBlocking { editor.applyColorMode(uri, ScanColorMode.HIGHLIGHT_TEXT) }

        assertNotNull(bw)
        assertNotNull(highlight)
        assertNotEquals(bw, highlight)
        assertNotEquals(uri, bw)
        assertEquals(before + 2, editsCount())
    }

    @Test
    fun uriInexistente_devuelveNullEnAmbasOperaciones() {
        val missing = Uri.fromFile(File(context.cacheDir, "r20_no_existe.jpg"))

        assertNull(runBlocking { editor.applyAdjustments(missing, 10, 10, 50) })
        assertNull(runBlocking { editor.applyColorMode(missing, ScanColorMode.GRAYSCALE) })
    }

    @Test
    fun archivoQueNoEsImagen_devuelveNull() {
        val file = File(context.cacheDir, "r20_no_imagen_${System.nanoTime()}.jpg").also { sources += it }
        file.writeText("esto no es una imagen")

        val result = runBlocking { editor.applyAdjustments(Uri.fromFile(file), 10, 0, 100) }

        assertNull(result)
    }

    @Test
    fun deleteCachedFile_borraSoloElArchivoPropioDeEsteEditor() {
        val original = source(Color.GRAY)
        val edited = runBlocking { editor.applyAdjustments(original, 10, 0, 100) }!!
        val afterCreate = editsCount()

        // Una URI ajena (la original del escaneo) nunca se borra.
        editor.deleteCachedFile(original)
        assertTrue(File(checkNotNull(original.path)).exists())
        assertEquals(afterCreate, editsCount())

        editor.deleteCachedFile(edited)
        assertEquals(afterCreate - 1, editsCount())

        // Repetir es un no-op seguro.
        editor.deleteCachedFile(edited)
        assertEquals(afterCreate - 1, editsCount())
    }
}
