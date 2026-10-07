package com.docsmart.core.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `una pagina A4 conserva la escala 2x`() {
        val size = viewerPageBitmapSize(595, 842)

        assertEquals(1190, size.width)
        assertEquals(1684, size.height)
    }

    @Test
    fun `la pagina del crash de Crashlytics (156 MB a 2x) queda bajo el limite de Android`() {
        // 3118x3118 pts a 2x = 6236x6236 px = 155.6 MB en ARGB_8888 (el valor exacto del crash).
        val size = viewerPageBitmapSize(3118, 3118)

        val bytes = size.width.toLong() * size.height * 4
        assertTrue(bytes <= VIEWER_PAGE_MAX_PIXELS * 4, "bytes=$bytes")
        assertTrue(bytes < 100L * 1024 * 1024, "debe quedar bajo los 100 MB que Android permite dibujar")
    }

    @Test
    fun `reducir la escala conserva la proporcion de la pagina`() {
        val size = viewerPageBitmapSize(2384, 3370)

        val original = 2384.0 / 3370.0
        assertEquals(original, size.width.toDouble() / size.height, 0.001)
        assertTrue(size.width < 2384 * 2)
    }

    @Test
    fun `dimensiones invalidas no producen un bitmap de tamano cero`() {
        val size = viewerPageBitmapSize(0, -5)

        assertTrue(size.width >= 1)
        assertTrue(size.height >= 1)
    }
}
