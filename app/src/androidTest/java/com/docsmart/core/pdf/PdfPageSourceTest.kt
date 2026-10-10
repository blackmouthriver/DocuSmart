package com.docsmart.core.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * `PdfPageSource` abre el PDF una vez y renderiza cada página bajo demanda con una caché acotada por bytes.
 * Antes el Visor rasterizaba TODAS las páginas por adelantado (libro de 2 332 páginas: 21-75 s y ~3 GB).
 */
@RunWith(AndroidJUnit4::class)
class PdfPageSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<File>()
    private val opened = mutableListOf<PdfPageSource>()

    @Before
    fun limpiarCopiasPrevias() {
        context.cacheDir.listFiles { f -> f.name.startsWith("viewer_src") }?.forEach { it.delete() }
    }

    @After
    fun cerrarTodo() {
        opened.forEach { it.close() }
        created.forEach { it.delete() }
    }

    // PDF real de [pages] páginas de 300x400 pt, hecho con la API del sistema (sin dependencias).
    private fun pdf(pages: Int): File {
        val file = File(context.cacheDir, "r_pdfsource_${System.nanoTime()}.pdf").also { created += it }
        val doc = PdfDocument()
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(300, 400, i + 1).create())
            page.canvas.drawText("Pagina ${i + 1}", 40f, 60f, android.graphics.Paint().apply { textSize = 24f })
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun open(file: File): PdfPageSource = runBlocking { PdfPageSource.open(Uri.fromFile(file), context) }.also { opened += it }

    @Test
    fun abreSinRenderizarNadaYConoceElNumeroDePaginasYElTamanoDeLaPrimera() {
        val source = open(pdf(300))

        assertEquals(300, source.pageCount)
        assertEquals(300f, source.firstPageSize.widthPts, 0.5f)
        assertEquals(400f, source.firstPageSize.heightPts, 0.5f)
        assertEquals("abrir no debe renderizar páginas", 0L, source.cachedBytes)
    }

    @Test
    fun renderizaLaPaginaPedidaAlAnchoPedidoConSuProporcion() {
        val source = open(pdf(5))

        val page = runBlocking { source.render(2, 900) }

        assertNotNull(page)
        assertEquals(900, page!!.bitmap.width)
        assertEquals(1200, page.bitmap.height) // 300x400 -> proporción 3:4
        assertEquals(300f, page.pageWidthPts, 0.5f)
    }

    @Test
    fun laCacheNoCreceSinLimiteAlRecorrerMuchasPaginas() {
        val source = open(pdf(150))
        val budget = PdfPageSource.cacheBudgetBytes(context)
        val unaPagina = 1000L * 1333 * 4

        runBlocking { repeat(150) { source.render(it, 1000) } }

        // Sin límite serían ~800 MB; con él nunca se pasa del presupuesto (más, como mucho, la última página).
        assertTrue("caché=${source.cachedBytes} presupuesto=$budget", source.cachedBytes <= budget + unaPagina)
        assertTrue("la caché debe ser mucho menor que todas las páginas juntas", source.cachedBytes < 150L * unaPagina / 4)
    }

    @Test
    fun pedirDosVecesLaMismaPaginaNoLaRenderizaOtraVez() {
        val source = open(pdf(10))

        val first = runBlocking { source.render(3, 800) }
        val second = runBlocking { source.render(3, 800) }

        assertSame(first, second)
    }

    @Test
    fun indicesInvalidosOAnchoInvalidoDevuelvenNull() {
        val source = open(pdf(3))

        assertNull(runBlocking { source.render(-1, 800) })
        assertNull(runBlocking { source.render(3, 800) })
        assertNull(runBlocking { source.render(0, 0) })
    }

    @Test
    fun variosRendersConcurrentesNoFallan() {
        val source = open(pdf(40))

        val pages = runBlocking { (0 until 40).map { async { source.render(it, 600) } }.awaitAll() }

        assertTrue(pages.all { it != null })
        assertEquals(40, pages.map { it!!.bitmap }.distinct().size)
    }

    @Test
    fun alCerrarNoRenderizaMasYBorraLaCopiaEnCache() {
        val source = open(pdf(5))
        assertTrue(context.cacheDir.listFiles { f -> f.name.startsWith("viewer_src") }!!.isNotEmpty())

        source.close()

        assertNull(runBlocking { source.render(0, 600) })
        // La liberación espera su turno en un mutex (en segundo plano): se espera a que termine.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            context.cacheDir.listFiles { f -> f.name.startsWith("viewer_src") }!!.isNotEmpty()
        ) {
            Thread.sleep(50)
        }
        assertTrue(context.cacheDir.listFiles { f -> f.name.startsWith("viewer_src") }!!.isEmpty())
    }

    @Test
    fun cerrarDosVecesNoFalla() {
        val source = open(pdf(2))

        source.close()
        source.close()
    }

    @Test
    fun unArchivoQueNoEsPdfLanzaYNoDejaCopiasEnCache() {
        val roto = File(context.cacheDir, "r_roto_${System.nanoTime()}.pdf").also { created += it }
        roto.writeText("esto no es un pdf")

        val resultado = runCatching { runBlocking { PdfPageSource.open(Uri.fromFile(roto), context) } }

        assertTrue(resultado.isFailure)
        assertTrue(context.cacheDir.listFiles { f -> f.name.startsWith("viewer_src") }!!.isEmpty())
    }
}
