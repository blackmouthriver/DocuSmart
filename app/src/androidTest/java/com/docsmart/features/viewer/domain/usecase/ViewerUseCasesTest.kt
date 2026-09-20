package com.docsmart.features.viewer.domain.usecase

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.features.viewer.presentation.ViewerTestFiles
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ronda 20: casos de uso del Visor con el framework Android real (ContentResolver/cacheDir/
 * filesDir) sobre PDFs reales de iText7: búsqueda con posición de cada coincidencia
 * (RF-VIS-08) y "aplanado" de anotaciones en una copia (HU-46).
 */
class ViewerUseCasesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ViewerTestFiles()
    private val outputs = mutableListOf<File>()

    @After
    fun tearDown() {
        files.deleteAll()
        outputs.forEach { runCatching { it.delete() } }
    }

    // PDF con un texto distinto por página (300x400 pt, misma medida que el resto de las pruebas del Visor).
    private fun pdfWithPages(vararg pageTexts: String): File {
        val file = files.bytes(".pdf", ByteArray(0))
        val document = Document(PdfDocument(PdfWriter(file)), PageSize(300f, 400f))
        pageTexts.forEachIndexed { index, text ->
            if (index > 0) document.add(AreaBreak())
            document.add(Paragraph(text))
        }
        document.close()
        return file
    }

    private fun annotation(
        id: String,
        type: AnnotationType,
        page: Int,
        text: String = "",
    ) = AnnotationEntity(
        id = id,
        documentId = "doc",
        type = type,
        page = page,
        xPts = 50f,
        yPts = 300f,
        widthPts = if (type == AnnotationType.HIGHLIGHT) 120f else 0f,
        heightPts = if (type == AnnotationType.HIGHLIGHT) 20f else 0f,
        color = 0xFFFFEB3B.toInt(),
        text = text,
        createdAt = 0L,
    )

    private fun flatten(
        source: File,
        annotations: List<AnnotationEntity>,
    ): File? =
        runBlocking { FlattenAnnotationsPdfUseCase(context)(Uri.fromFile(source), annotations) }
            .also { if (it != null) outputs += it }

    private fun search(
        source: File,
        query: String,
    ): List<PdfPageMatches> = runBlocking { SearchPdfTextUseCase(context)(Uri.fromFile(source), query) }

    // ── SearchPdfTextUseCase ────────────────────────────────────────────────

    @Test
    fun busqueda_devuelveSoloLasPaginasConCoincidenciaYSuPosicionReal() {
        val pdf = pdfWithPages("Manzana roja", "Pera verde", "Otra manzana madura")

        val results = search(pdf, "MANZANA")

        assertEquals(listOf(1, 3), results.map { it.pageNumber })
        results.forEach { page ->
            assertTrue(page.rects.isNotEmpty())
            page.rects.forEach { rect ->
                assertTrue(rect.widthPts > 0f)
                assertTrue(rect.heightPts > 0f)
                assertTrue(rect.xPts >= 0f && rect.yPts >= 0f)
            }
        }
    }

    @Test
    fun busqueda_conVariasCoincidenciasEnUnaPaginaDevuelveUnRectanguloPorCada() {
        val pdf = pdfWithPages("clave uno, clave dos y clave tres")

        val results = search(pdf, "clave")

        assertEquals(1, results.size)
        assertEquals(3, results.single().rects.size)
    }

    @Test
    fun busqueda_sinCoincidenciasOConConsultaVaciaDevuelveListaVacia() {
        val pdf = pdfWithPages("Manzana roja")

        assertTrue(search(pdf, "zzz").isEmpty())
        assertTrue(search(pdf, "").isEmpty())
        assertTrue(search(pdf, "   ").isEmpty())
    }

    @Test
    fun busqueda_deUnArchivoInexistenteOQueNoEsPdfNoFallaYDevuelveVacio() {
        val missing = File(files.dir(), "viewer_r20_no_existe_busqueda.pdf")
        assertTrue(search(missing, "algo").isEmpty())

        val garbage = files.bytes(".pdf", "no soy un pdf".toByteArray())
        assertTrue(search(garbage, "algo").isEmpty())
    }

    // ── FlattenAnnotationsPdfUseCase ────────────────────────────────────────

    @Test
    fun aplanar_sinAnotacionesDevuelveNull() {
        val pdf = pdfWithPages("Hola")
        assertNull(flatten(pdf, emptyList()))
    }

    @Test
    fun aplanar_generaUnaCopiaConLaNotaNativaYNoTocaElOriginal() {
        val pdf = pdfWithPages("Pagina uno", "Pagina dos")
        val originalSize = pdf.length()

        val output =
            flatten(
                pdf,
                listOf(
                    annotation("h1", AnnotationType.HIGHLIGHT, page = 1),
                    annotation("n1", AnnotationType.NOTE, page = 2, text = "Nota real"),
                ),
            )

        assertNotNull(output)
        val copy = output!!
        assertTrue(copy.exists() && copy.length() > 0L)
        assertEquals("viewer_share", copy.parentFile?.name)
        assertEquals(originalSize, pdf.length())

        PdfDocument(PdfReader(copy)).use { doc ->
            assertEquals(2, doc.numberOfPages)
            // El resaltado se dibuja en la página; la nota además queda como anotación nativa.
            assertEquals(0, doc.getPage(1).annotations.size)
            val nativeNotes = doc.getPage(2).annotations
            assertEquals(1, nativeNotes.size)
            assertEquals("Nota real", nativeNotes.single().contents.toUnicodeString())
        }
    }

    @Test
    fun aplanar_enPaginasRotadasConvierteLasCoordenadasSinFallar() {
        listOf(90, 180, 270).forEach { rotation ->
            val pdf = files.pdf(pages = 1, rotation = rotation)

            val output =
                flatten(
                    pdf,
                    listOf(
                        annotation("h$rotation", AnnotationType.HIGHLIGHT, page = 1),
                        annotation("n$rotation", AnnotationType.NOTE, page = 1, text = "Rotada $rotation"),
                    ),
                )

            assertNotNull("rotación $rotation", output)
            PdfDocument(PdfReader(output!!)).use { doc -> assertEquals(1, doc.numberOfPages) }
        }
    }

    @Test
    fun aplanar_ignoraAnotacionesDePaginasFueraDeRango() {
        val pdf = pdfWithPages("Solo una pagina")

        val output =
            flatten(
                pdf,
                listOf(
                    annotation("x1", AnnotationType.HIGHLIGHT, page = 99),
                    annotation("x2", AnnotationType.NOTE, page = 0, text = "Pagina cero"),
                ),
            )

        assertNotNull(output)
        PdfDocument(PdfReader(output!!)).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertEquals(0, doc.getPage(1).annotations.size)
        }
    }

    @Test
    fun aplanar_deUnOrigenInexistenteOCorruptoDevuelveNull() {
        val notes = listOf(annotation("n1", AnnotationType.NOTE, page = 1, text = "x"))

        val missing = File(files.dir(), "viewer_r20_no_existe_aplanar.pdf")
        assertNull(flatten(missing, notes))

        val garbage = files.bytes(".pdf", "no soy un pdf".toByteArray())
        assertNull(flatten(garbage, notes))
    }
}
