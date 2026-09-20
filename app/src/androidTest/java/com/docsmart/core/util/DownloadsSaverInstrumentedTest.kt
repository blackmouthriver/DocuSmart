package com.docsmart.core.util

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * DownloadsSaver con un ContentResolver simulado: la "colección de Descargas" es un archivo dentro de la carpeta
 * aislada de la prueba, así que nunca se escribe en MediaStore ni en la carpeta pública real. Todas las pruebas
 * de guardado exigen API 29+ (rama MediaStore); en versiones anteriores el código escribiría en Descargas reales.
 */
class DownloadsSaverInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var destination: File

    private val resolver = mockk<ContentResolver>(relaxed = true)
    private var insertedName: String? = null
    private var insertedMime: String? = null
    private var pendingAtInsert: Int? = null

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ctx = newIsolatedContext("downloads")
        ctx.resolver = resolver
        destination = File(ctx.root, "destino/salida.bin").apply { parentFile?.mkdirs() }
        every { resolver.insert(any(), any()) } answers {
            val values = secondArg<ContentValues>()
            insertedName = values.getAsString(MediaStore.Downloads.DISPLAY_NAME)
            insertedMime = values.getAsString(MediaStore.Downloads.MIME_TYPE)
            pendingAtInsert = values.getAsInteger(MediaStore.Downloads.IS_PENDING)
            Uri.fromFile(destination)
        }
        every { resolver.openOutputStream(any()) } answers { FileOutputStream(File(firstArg<Uri>().path!!)) }
        every { resolver.openInputStream(any()) } answers { FileInputStream(File(firstArg<Uri>().path!!)) }
    }

    @After
    fun tearDown() {
        if (::ctx.isInitialized) ctx.cleanUp()
    }

    private fun source(
        name: String,
        bytes: ByteArray,
    ): File =
        ctx.inputFile(name).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

    @Test
    fun mimeTypeForExtensionCubreLosTiposConocidosYElPorDefecto() {
        assertEquals("application/pdf", DownloadsSaver.mimeTypeForExtension("PDF"))
        assertEquals("text/plain", DownloadsSaver.mimeTypeForExtension("txt"))
        assertEquals("text/csv", DownloadsSaver.mimeTypeForExtension("csv"))
        assertEquals("image/jpeg", DownloadsSaver.mimeTypeForExtension("jpg"))
        assertEquals("image/jpeg", DownloadsSaver.mimeTypeForExtension("JPEG"))
        assertEquals("image/png", DownloadsSaver.mimeTypeForExtension("png"))
        assertEquals("application/octet-stream", DownloadsSaver.mimeTypeForExtension("xyz"))
    }

    @Test
    fun saveFileCopiaElContenidoYMarcaElArchivoComoTerminado() {
        val bytes = ByteArray(2048) { (it % 251).toByte() }
        val file = source("reporte.pdf", bytes)

        val ok = runBlocking { DownloadsSaver.saveFile(ctx, file, "application/pdf") }

        assertTrue(ok)
        assertArrayEquals(bytes, destination.readBytes())
        assertEquals("reporte.pdf", insertedName)
        assertEquals("application/pdf", insertedMime)
        // Se inserta como pendiente (1) y luego se actualiza para publicarlo (0).
        assertEquals(1, pendingAtInsert)
        verify {
            resolver.update(any(), match { it.getAsInteger(MediaStore.Downloads.IS_PENDING) == 0 }, any(), any())
        }
    }

    @Test
    fun saveFileSaneaUnNombreConSegmentosDeRuta() {
        val file = source("origen.txt", byteArrayOf(1, 2, 3))

        val ok = runBlocking { DownloadsSaver.saveFile(ctx, file, "text/plain", "../../secreto.txt") }

        assertTrue(ok)
        assertEquals("secreto.txt", insertedName)
    }

    @Test
    fun saveFileDevuelveFalsoSiMediaStoreNoInsertaLaFila() {
        every { resolver.insert(any(), any()) } returns null
        val file = source("a.pdf", byteArrayOf(1))

        assertFalse(runBlocking { DownloadsSaver.saveFile(ctx, file, "application/pdf") })
    }

    @Test
    fun saveFileDevuelveFalsoSiNoHayFlujoDeSalidaONoSePuedeEscribir() {
        val file = source("a.pdf", byteArrayOf(1))

        every { resolver.openOutputStream(any()) } returns null
        assertFalse(runBlocking { DownloadsSaver.saveFile(ctx, file, "application/pdf") })

        every { resolver.openOutputStream(any()) } throws IOException("disco lleno")
        assertFalse(runBlocking { DownloadsSaver.saveFile(ctx, file, "application/pdf") })
    }

    @Test
    fun saveFileDevuelveFalsoSiElOrigenNoExiste() {
        val missing = File(ctx.root, "no_existe.pdf")

        assertFalse(runBlocking { DownloadsSaver.saveFile(ctx, missing, "application/pdf") })
    }

    @Test
    fun saveUriCopiaDesdeUnaUriDeOrigen() {
        val bytes = ByteArray(500) { it.toByte() }
        val file = source("hoja.csv", bytes)

        val ok = runBlocking { DownloadsSaver.saveUri(ctx, Uri.fromFile(file), "text/csv", "datos.csv") }

        assertTrue(ok)
        assertArrayEquals(bytes, destination.readBytes())
        assertEquals("datos.csv", insertedName)
        assertEquals("text/csv", insertedMime)
    }

    @Test
    fun saveUriSaneaElNombreDeDestino() {
        val file = source("hoja.csv", byteArrayOf(9))

        runBlocking { DownloadsSaver.saveUri(ctx, Uri.fromFile(file), "text/csv", "..\\..\\evil.csv") }

        assertEquals("evil.csv", insertedName)
    }

    @Test
    fun saveUriDevuelveFalsoSiFallaCualquierPaso() {
        val file = source("hoja.csv", byteArrayOf(9))
        val uri = Uri.fromFile(file)

        every { resolver.openInputStream(any()) } returns null
        assertFalse(runBlocking { DownloadsSaver.saveUri(ctx, uri, "text/csv", "a.csv") })

        every { resolver.openInputStream(any()) } throws IOException("sin acceso")
        assertFalse(runBlocking { DownloadsSaver.saveUri(ctx, uri, "text/csv", "a.csv") })

        every { resolver.insert(any(), any()) } returns null
        assertFalse(runBlocking { DownloadsSaver.saveUri(ctx, uri, "text/csv", "a.csv") })
    }

    @Test
    fun saveUriDevuelveFalsoSiNoHayFlujoDeSalida() {
        val file = source("hoja.csv", byteArrayOf(9))
        every { resolver.openOutputStream(any()) } returns null

        assertFalse(runBlocking { DownloadsSaver.saveUri(ctx, Uri.fromFile(file), "text/csv", "a.csv") })
    }
}
