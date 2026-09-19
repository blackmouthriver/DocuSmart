package com.docsmart.core.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * `renderPdfPagesToBitmaps()` no se puede ejercitar de punta a punta en un
 * test unitario JVM puro: una vez copiado el PDF al caché, construye un
 * `android.graphics.pdf.PdfRenderer` real, que exige el runtime nativo de
 * Android (sin Robolectric en este proyecto, mismo límite ya documentado en
 * `PdfToImageUseCaseTest`/`OcrPdfUseCaseTest`). Lo que sí es exercitable —y
 * es exactamente donde estaba el bug real corregido el 2026-09-17 (el
 * archivo de caché nunca se borraba)— es el camino de copiado al caché y su
 * limpieza, que ocurre *antes* de tocar `PdfRenderer`.
 */
class PdfPageBitmapTest {
    private lateinit var cacheDir: File
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_pdfpagebitmap_cache_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun assertCacheDirEmpty(message: String) {
        assertTrue(cacheDir.listFiles()?.isEmpty() != false, message)
    }

    @Test
    fun `devuelve lista vacia y no deja copia en cache si el content uri no se puede abrir`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val pages = renderPdfPagesToBitmaps(uri, context)

        assertTrue(pages.isEmpty())
        assertCacheDirEmpty("no debe quedar copia del PDF en cache tras el fallo de openInputStream")
    }

    @Test
    fun `no propaga la excepcion si el content resolver falla y limpia igual el cache`() {
        // Hallazgo real de la auditoría general 2026-09-18 (ronda 14): el
        // catch de este camino interpolaba `e.message` (que en un
        // SecurityException/FileNotFoundException de un content:// suele
        // incluir la Uri real) en el texto que Timber.e sube a Crashlytics
        // -- el comportamiento observable (no crashea, no deja basura en
        // caché) es lo que cubre este test.
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } throws
            SecurityException(
                "Permission Denial: reading content://com.docsmart.fileprovider/contrato_confidencial.pdf",
            )
        every { context.contentResolver } returns resolver

        val pages = renderPdfPagesToBitmaps(uri, context)

        assertTrue(pages.isEmpty())
        assertCacheDirEmpty("no debe quedar copia del PDF en cache tras la excepcion")
    }

    @Test
    fun `devuelve lista vacia si el uri de esquema file apunta a un archivo inexistente`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns File(cacheDir, "no_existe.pdf").absolutePath

        val pages = renderPdfPagesToBitmaps(uri, context)

        assertTrue(pages.isEmpty())
        assertCacheDirEmpty("no debe quedar copia del PDF en cache si el origen no existe")
    }

    @Test
    fun `devuelve lista vacia si el uri de esquema file apunta a un archivo vacio`() {
        val vacio = File(cacheDir, "vacio.pdf").apply { createNewFile() }
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns vacio.absolutePath

        val pages = renderPdfPagesToBitmaps(uri, context)

        assertTrue(pages.isEmpty())
    }
}
