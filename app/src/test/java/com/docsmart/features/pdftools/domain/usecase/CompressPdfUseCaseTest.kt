package com.docsmart.features.pdftools.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * El camino feliz de `CompressPdfUseCase.invoke()` construye un
 * `android.graphics.pdf.PdfRenderer` directamente (no inyectado), así que no
 * se puede ejercitar en un test unitario JVM puro (mismo límite ya
 * documentado en `OcrPdfUseCaseTest`, sin Robolectric en este proyecto).
 *
 * Sí son JVM puro y merecen cobertura real: el cálculo del factor de escala
 * según la calidad elegida, el mensaje de resultado, y el camino de error
 * cuando el PDF de origen no se puede leer -- este último ocurre *antes* de
 * tocar PdfRenderer, así que sí es exercitable de punta a punta.
 */
class CompressPdfUseCaseTest {
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: CompressPdfUseCase

    private val messages =
        CompressPdfMessages(
            readError = "readError",
            emptyFile = "emptyFile",
            noPages = "noPages",
            generateError = "generateError",
            alreadyOptimized = "ya optimo %1\$d KB",
            success = "%1\$d -> %2\$d KB (%3\$d%%)",
            genericError = "error %1\$s",
        )

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_compress_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        useCase = CompressPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `scaleFactorFor usa 1_5 para calidad alta`() {
        assertEquals(1.5f, useCase.scaleFactorFor(100))
        assertEquals(1.5f, useCase.scaleFactorFor(80))
    }

    @Test
    fun `scaleFactorFor usa 1_2 para calidad media`() {
        assertEquals(1.2f, useCase.scaleFactorFor(79))
        assertEquals(1.2f, useCase.scaleFactorFor(60))
    }

    @Test
    fun `scaleFactorFor usa 0_9 para calidad baja`() {
        assertEquals(0.9f, useCase.scaleFactorFor(59))
        assertEquals(0.9f, useCase.scaleFactorFor(40))
    }

    @Test
    fun `scaleFactorFor usa 0_6 para calidad muy baja`() {
        assertEquals(0.6f, useCase.scaleFactorFor(39))
        assertEquals(0.6f, useCase.scaleFactorFor(0))
    }

    @Test
    fun `resultMessage usa el mensaje de exito cuando el archivo se redujo`() {
        val message =
            useCase.resultMessage(
                messages,
                keepOriginal = false,
                originalKb = 500L,
                finalKb = 200L,
                reduction = 60,
            )

        assertEquals("500 -> 200 KB (60%)", message)
    }

    @Test
    fun `resultMessage usa el mensaje de ya optimizado cuando se conserva el original`() {
        val message =
            useCase.resultMessage(
                messages,
                keepOriginal = true,
                originalKb = 300L,
                finalKb = 300L,
                reduction = 0,
            )

        assertEquals("ya optimo 300 KB", message)
    }

    @Test
    fun `invoke devuelve Error de lectura si el PDF de origen no se puede abrir`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            val result = useCase(uri, messages = messages)

            assertTrue(result is PdfToolResult.Error)
            assertEquals(messages.readError, (result as PdfToolResult.Error).message)
        }

    @Test
    fun `invoke devuelve Error de lectura -no de archivo vacio- si el PDF de origen esta vacio`() =
        runTest {
            // copyUriToCache() ya devuelve null cuando el input stream no aporta
            // bytes, así que este camino sale por readError -- nunca llega a
            // evaluarse el chequeo de emptyFile en invoke().
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(0)) }
            every { context.contentResolver } returns resolver

            val result = useCase(uri, messages = messages)

            assertTrue(result is PdfToolResult.Error)
            assertEquals(messages.readError, (result as PdfToolResult.Error).message)
        }

    // Bug real corregido en la ronda 15: copyUriToCache() devolvia null dejando
    // el archivo (vacio o parcial) huerfano en cacheDir -- el `finally` de
    // invoke() solo borra cacheFile cuando la copia ya devolvio un File.
    @Test
    fun `copyUriToCache con un stream vacio devuelve null y no deja archivo en cache`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(0)) }
        every { context.contentResolver } returns resolver

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "cache huerfano: ${cacheDir.listFiles()?.toList()}")
    }

    @Test
    fun `copyUriToCache con stream nulo devuelve null y no deja archivo en cache`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `copyUriToCache borra la copia parcial si el stream falla a mitad de lectura`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } answers { FailingAfterBytesStream(1024) }
        every { context.contentResolver } returns resolver

        assertNull(useCase.copyUriToCache(uri))

        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "cache parcial huerfano: ${cacheDir.listFiles()?.toList()}")
    }

    @Test
    fun `copyUriToCache copia el contenido completo cuando el stream es valido`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        val content = ByteArray(5000) { (it % 251).toByte() }
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(content) }
        every { context.contentResolver } returns resolver

        val copied = useCase.copyUriToCache(uri)

        assertNotNull(copied)
        assertTrue(content.contentEquals(copied!!.readBytes()))
    }

    @Test
    fun `una cancelacion leyendo el origen se propaga y no deja archivo en cache`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
            every { context.contentResolver } returns resolver

            var cancelled = false
            try {
                useCase(uri, messages = messages)
            } catch (e: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled, "la CancellationException debia propagarse")
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        }

    /** Entrega `remaining` bytes y despues lanza IOException, como un stream de red/SAF que se corta. */
    private class FailingAfterBytesStream(private var remaining: Int) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) throw IOException("stream cortado")
            remaining--
            return 7
        }
    }
}
