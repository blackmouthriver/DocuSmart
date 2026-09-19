package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Ronda 14 de la auditoría del Convertidor: este use case tenía ~1.6% de
 * cobertura. El camino feliz de `invoke()` construye Bitmaps/Canvas/
 * android.graphics.pdf.PdfDocument reales -- no ejercitable en un test
 * unitario JVM puro sin Robolectric (mismo límite ya documentado en
 * PdfToImageUseCaseTest/ImageFormatUseCaseTest). Los tests de abajo cubren
 * los caminos que SÍ son alcanzables sin decodificar un Bitmap real: la
 * lista vacía, el caso "todas las imágenes fallan al abrir su stream" (no
 * llega a tocar BitmapFactory, ver loadBitmapFromUri) y, sobre todo, el bug
 * real corregido en esta ronda: `loadBitmapFromUri()` atrapaba
 * `CancellationException` con su propio `catch (e: Exception)` genérico
 * interno, sin relanzarla -- el fix de "Hallazgo 1" (relanzar
 * CancellationException antes del catch genérico) estaba aplicado en
 * invoke() pero NO se había propagado a este método anidado.
 */
class ConvertImageToPdfUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ConvertImageToPdfUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_imgtopdf_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        useCase = ConvertImageToPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `lista de imagenes vacia devuelve Error sin tocar el sistema de archivos`() = runTest {
        val result = useCase(emptyList(), "salida")

        assertTrue(result is ConversionResult.Error)
        assertTrue(File(filesDir, "converted").listFiles().isNullOrEmpty())
    }

    @Test
    fun `si todas las imagenes fallan al abrir su stream, devuelve Error sin dejar un PDF huerfano`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        // openInputStream() devuelve null -- loadBitmapFromUri() corta antes
        // de siquiera llamar a BitmapFactory (?.use{} con receptor null).
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase(listOf(uri), "salida")

        assertTrue(result is ConversionResult.Error)
        val orphanedPdfs = File(filesDir, "converted").listFiles { f -> f.extension == "pdf" }
        assertTrue(orphanedPdfs.isNullOrEmpty(), "no debería quedar un .pdf huérfano: ${orphanedPdfs?.map { it.name }}")
    }

    // Bug real corregido en esta ronda: ver el comentario de la clase.
    // Se prueba loadBitmapFromUri() directamente (expuesta como `internal`
    // para esto) en vez de a través de invoke()/buildPdfFromImages(): ese
    // último construye un android.graphics.pdf.PdfDocument real, y su
    // finally { pdfDocument.close() } lanza "not mocked" en un test JVM
    // puro -- esa excepción de infraestructura reemplazaría/enmascararía la
    // CancellationException real que este test verifica (el finally de un
    // try/finally que también lanza sustituye la excepción que se estaba
    // propagando).
    @Test
    fun `una cancelacion leyendo una imagen se propaga en vez de tratarse como imagen fallida`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
        every { context.contentResolver } returns resolver

        assertThrows(CancellationException::class.java) {
            useCase.loadBitmapFromUri(uri)
        }
    }
}
