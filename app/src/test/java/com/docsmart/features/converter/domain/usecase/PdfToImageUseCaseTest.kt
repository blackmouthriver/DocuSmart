package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.R
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * El camino feliz de `PdfToImageUseCase.invoke()` construye un
 * `android.graphics.pdf.PdfRenderer` directamente (no inyectado), así que no
 * se puede ejercitar en un test unitario JVM puro (mismo límite ya
 * documentado en `OcrPdfUseCaseTest`/`CompressPdfUseCaseTest`, sin
 * Robolectric en este proyecto). El único camino que sí es exercitable de
 * punta a punta es el error temprano cuando el PDF de origen no se puede
 * leer -- ocurre *antes* de tocar PdfRenderer.
 */
class PdfToImageUseCaseTest {
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: PdfToImageUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_pdftoimage_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        useCase = PdfToImageUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `invoke devuelve Error si el PDF de origen no se puede abrir`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            val result = useCase(uri)

            assertTrue(result is ConversionResult.Error)
        }

    @Test
    fun `invoke no deja el archivo de cache en disco tras un error de lectura`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            useCase(uri)

            assertTrue(cacheDir.listFiles()?.isEmpty() != false)
        }

    // Bug real corregido en la ronda 15: un origen de 0 bytes se daba por
    // "copiado" y el PdfRenderer reventaba despues con un mensaje interno de la
    // plataforma, en vez del error de lectura correcto.
    @Test
    fun `un PDF de origen vacio devuelve el error de lectura y no deja cache`() =
        runTest {
            every { context.getString(R.string.converter_error_read_pdf) } returns "no se pudo leer el PDF"
            every { context.getString(R.string.converter_error_generic_format) } returns "generico %1\$s"
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(ByteArray(0)) }
            every { context.contentResolver } returns resolver

            val result = useCase(uri)

            assertEquals(ConversionResult.Error("no se pudo leer el PDF"), result)
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        }

    @Test
    fun `una cancelacion leyendo el PDF se propaga y no deja cache`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } throws CancellationException("cancelado")
            every { context.contentResolver } returns resolver

            var cancelled = false
            try {
                useCase(uri)
            } catch (e: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled, "la CancellationException debia propagarse")
            assertTrue(cacheDir.listFiles().isNullOrEmpty())
        }

    // ── pdfToImageRenderSize (funcion pura) ─────────────────────────────────

    @Test
    fun `una pagina A4 se renderiza al doble de su tamano en puntos`() {
        val size = pdfToImageRenderSize(595, 842)

        assertEquals(PageRenderSize(1190, 1684), size)
    }

    // Bug real corregido en la ronda 15: la escala fija 2x sobre una pagina de
    // gran formato (plano A0, ~2384x3370 pt) pedia ~128 MB por bitmap y agotaba
    // la memoria en un PDF perfectamente valido.
    @Test
    fun `una pagina de gran formato se reduce para no superar el tope de pixeles`() {
        val size = pdfToImageRenderSize(2384, 3370)

        val pixels = size.width.toLong() * size.height
        assertTrue(pixels <= PDF_TO_IMAGE_MAX_PIXELS, "excede el tope: $size")
        // Sigue aprovechando casi todo el presupuesto (no se achica de mas).
        assertTrue(pixels > PDF_TO_IMAGE_MAX_PIXELS * 95 / 100, "demasiado chico: $size")
        // Conserva la proporcion de la pagina.
        assertEquals(2384.0 / 3370.0, size.width.toDouble() / size.height, 0.002)
    }

    @Test
    fun `una pagina justo en el tope no se reduce`() {
        // 2000x2000 pt a 2x = 4000x4000 = 16M px == tope exacto.
        val size = pdfToImageRenderSize(2000, 2000)

        assertEquals(PageRenderSize(4000, 4000), size)
    }

    @Test
    fun `dimensiones invalidas producen un bitmap minimo valido en vez de cero o negativo`() {
        val size = pdfToImageRenderSize(0, -5)

        assertTrue(size.width >= 1 && size.height >= 1)
    }

    @Test
    fun `una pagina extrema no desborda el calculo ni produce dimensiones invalidas`() {
        val size = pdfToImageRenderSize(200_000, 200_000)

        assertTrue(size.width >= 1 && size.height >= 1)
        assertTrue(size.width.toLong() * size.height <= PDF_TO_IMAGE_MAX_PIXELS)
    }
}
