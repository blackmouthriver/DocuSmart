package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `invoke devuelve Error si el PDF de origen no se puede abrir`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase(uri)

        assertTrue(result is ConversionResult.Error)
    }

    @Test
    fun `invoke no deja el archivo de cache en disco tras un error de lectura`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        useCase(uri)

        assertTrue(cacheDir.listFiles()?.isEmpty() != false)
    }
}
