package com.docsmart.features.pdftools.domain.usecase

import android.graphics.Bitmap
import android.graphics.Color
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.converter.domain.usecase.writeTestImage
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfFormField
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Herramientas PDF (iText7) ejecutadas sobre el runtime real de Android y, donde importa, sobre PDFs generados por
 * el propio framework (android.graphics.pdf.PdfDocument, igual que los que produce el Escáner de la app).
 */
class PdfToolsUseCasesInstrumentedTest {
    private lateinit var ctx: IsolatedContext

    @Before
    fun setUp() {
        ctx = newIsolatedContext("pdftools")
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun success(result: PdfToolResult): PdfToolResult.Success {
        assertTrue("se esperaba Success pero fue $result", result is PdfToolResult.Success)
        return result as PdfToolResult.Success
    }

    private fun pageCountOf(file: File): Int = PdfDocument(PdfReader(file)).use { it.numberOfPages }

    @Test
    fun rotarEscribeLaRotacionEnUnPdfGeneradoPorAndroid() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2)
        val messages = RotatePdfMessages("r", "np", "g", "girado %1\$d", "e %1\$s")

        val result = success(runBlocking { RotatePdfUseCase(ctx)(uriOf(pdf), 90, "rot", messages) })

        assertEquals("girado 90", result.message)
        val rotations = PdfDocument(PdfReader(result.outputFile)).use { d -> (1..2).map { d.getPage(it).rotation } }
        assertEquals(listOf(90, 90), rotations)
    }

    @Test
    fun dividirExtraeElRangoPedido() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 4)
        val messages = SplitPdfMessages("r", "np", "g", "ok %1\$d %2\$d", "e %1\$s")

        val result = success(runBlocking { SplitPdfUseCase(ctx)(uriOf(pdf), 2, 3, "sub", messages) })

        assertEquals(2, pageCountOf(result.outputFile))
        assertTrue(result.message.startsWith("ok 2 "))
    }

    @Test
    fun reordenarYEliminarPaginas() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 3)
        val messages = ReorderPagesMessages("vacio", "r", "g", "ok %1\$d", "e %1\$s")

        val result = success(runBlocking { ReorderPagesUseCase(ctx)(uriOf(pdf), listOf(3, 1), "ord", messages) })
        val empty = runBlocking { ReorderPagesUseCase(ctx)(uriOf(pdf), emptyList(), "x", messages) }

        assertEquals(2, pageCountOf(result.outputFile))
        assertEquals(PdfToolResult.Error("vacio"), empty)
    }

    @Test
    fun unirSaltaUnArchivoCorruptoYAvisa() {
        val a = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2)
        val b = writeAndroidPdf(ctx.inputFile("b.pdf"), pages = 1)
        val broken = ctx.inputFile("roto.pdf").apply { writeText("no es un pdf") }
        val messages = MergePdfMessages("min", "r", "g", "unidos %1\$d %2\$d", "e %1\$s", "saltados %1\$d")
        val uris = listOf(uriOf(a), uriOf(broken), uriOf(b))

        val result = success(runBlocking { MergePdfUseCase(ctx)(uris, "union", messages) })
        val tooFew = runBlocking { MergePdfUseCase(ctx)(listOf(uriOf(a)), "x", messages) }

        assertEquals(3, pageCountOf(result.outputFile))
        assertEquals("unidos 2 3 saltados 1", result.message)
        assertEquals(PdfToolResult.Error("min"), tooFew)
    }

    @Test
    fun recortarReduceElAreaVisibleDeCadaPagina() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 1, width = 200, height = 300)
        val messages = CropPdfMessages("r", "np", "g", "recorte %1\$d", "e %1\$s")

        val result = success(runBlocking { CropPdfUseCase(ctx)(uriOf(pdf), 10, "rec", messages) })

        val size = PdfDocument(PdfReader(result.outputFile)).use { it.getPage(1).pageSize }
        assertEquals(160f, size.width, 1f)
        assertEquals(240f, size.height, 1f)
    }

    @Test
    fun numerarPaginasEscribeElTextoDelNumero() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2)
        val messages = NumberPagesMessages("r", "np", "g", "num %1\$d", "e %1\$s", "Pag %1\$d de %2\$d")
        val useCase = NumberPagesUseCase(ctx)

        val result = success(runBlocking { useCase(uriOf(pdf), PageNumberFormat.PAGE_OF_TOTAL, "num", messages) })

        assertEquals(2, pageCountOf(result.outputFile))
        assertTrue(pageText(result.outputFile, 2).contains("Pag 2 de 2"))
    }

    @Test
    fun marcaDeAguaConservaLasPaginasYRechazaTextoVacioOFueraDeLatin1() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2)
        val messages = WatermarkMessages("vacio", "r", "np", "g", "marca %1\$d", "e %1\$s")
        val useCase = WatermarkPdfUseCase(ctx)

        val result = success(runBlocking { useCase(uriOf(pdf), "CONFIDENCIAL", "marca", messages) })
        val blank = runBlocking { useCase(uriOf(pdf), "   ", "x", messages) }
        val cyrillic = runBlocking { useCase(uriOf(pdf), "СЕКРЕТ", "x", messages) }

        assertEquals(2, pageCountOf(result.outputFile))
        assertEquals(PdfToolResult.Error("vacio"), blank)
        assertTrue(cyrillic is PdfToolResult.Error)
    }

    @Test
    fun firmarEstampaUnaFirmaPngGeneradaConBitmapReal() {
        val pdf = writeAndroidPdf(ctx.inputFile("a.pdf"), pages = 2)
        val messages = SignPdfMessages("sinFirma", "r", "np", "g", "firmado %1\$d", "e %1\$s")
        val signature = ByteArrayOutputStream()
        Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(10, 10, Color.BLACK)
            compress(Bitmap.CompressFormat.PNG, 100, signature)
            recycle()
        }
        val useCase = SignPdfUseCase(ctx)

        val result = success(runBlocking { useCase(uriOf(pdf), signature.toByteArray(), 99, "firma", messages) })
        val empty = runBlocking { useCase(uriOf(pdf), ByteArray(0), 1, "x", messages) }

        // La página 99 se ajusta a la última (2).
        assertEquals("firmado 2", result.message)
        val xObjects =
            PdfDocument(PdfReader(result.outputFile)).use { d ->
                d.getPage(2).resources.pdfObject.getAsDictionary(PdfName.XObject)
            }
        assertTrue("la página 2 debe tener la imagen de la firma", xObjects != null && xObjects.size() >= 1)
        assertEquals(PdfToolResult.Error("sinFirma"), empty)
    }

    @Test
    fun extraerImagenesDevuelveLasImagenesEmbebidas() {
        val jpg = writeTestImage(ctx.inputFile("foto.jpg"), format = Bitmap.CompressFormat.JPEG)
        val withImage = ctx.inputFile("con_imagen.pdf")
        PdfDocument(PdfWriter(withImage)).use { pdf ->
            val page = pdf.addNewPage()
            PdfCanvas(page).addImageFittedIntoRectangle(
                ImageDataFactory.create(jpg.readBytes()),
                Rectangle(50f, 500f, 120f, 80f),
                false,
            )
        }
        val noImages = writeAndroidPdf(ctx.inputFile("sin.pdf"))
        val messages = ExtractImagesMessages("r", "np", "sinImg", "extraidas %1\$d", "e %1\$s")
        val useCase = ExtractImagesFromPdfUseCase(ctx)

        val found = runBlocking { useCase(uriOf(withImage), "img", messages) }
        val none = runBlocking { useCase(uriOf(noImages), "img", messages) }

        assertTrue("fue $found", found is PdfToolResult.MultiSuccess)
        found as PdfToolResult.MultiSuccess
        assertEquals(1, found.outputFiles.size)
        assertTrue(found.outputFiles.first().length() > 0)
        assertEquals(PdfToolResult.Error("sinImg"), none)
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun censurarEliminaElTextoDeLaZonaMarcada() {
        val pdf = writeTextPdf(ctx.inputFile("t.pdf"), listOf("SECRETO", "PUBLICO"))
        val messages = RedactPdfMessages("sinZonas", "r", "np", "g", "censurado %1\$d", "e %1\$s")
        val rects = listOf(RedactionRect(pageNumber = 1, xFrac = 0.05f, yFrac = 0.14f, wFrac = 0.5f, hFrac = 0.05f))

        val result = success(runBlocking { RedactPdfUseCase(ctx)(uriOf(pdf), rects, "cens", messages) })
        val none = runBlocking { RedactPdfUseCase(ctx)(uriOf(pdf), emptyList(), "x", messages) }

        assertFalse(pageText(result.outputFile, 1).contains("SECRETO"))
        assertTrue(pageText(result.outputFile, 2).contains("PUBLICO"))
        assertEquals(PdfToolResult.Error("sinZonas"), none)
    }

    @Test
    fun editarTextoReemplazaLasCoincidenciasYAvisaSiNoHay() {
        val pdf = writeTextPdf(ctx.inputFile("t.pdf"), listOf("Hola mundo"))
        val messages =
            EditTextPdfMessages("sinBusqueda", "r", "np", "sinCoincidencias", "g", "editado %1\$d", "e %1\$s")
        val useCase = EditTextPdfUseCase(ctx)

        val result = success(runBlocking { useCase(uriOf(pdf), "mundo", "Android", "edit", messages) })
        val noMatch = runBlocking { useCase(uriOf(pdf), "inexistente", "x", "edit2", messages) }
        val blank = runBlocking { useCase(uriOf(pdf), " ", "x", "edit3", messages) }

        assertEquals("editado 1", result.message)
        assertFalse(pageText(result.outputFile).contains("mundo"))
        assertEquals(PdfToolResult.Error("sinCoincidencias"), noMatch)
        assertEquals(PdfToolResult.Error("sinBusqueda"), blank)
        assertEquals(1, ctx.outputDir("pdftools").listFiles().orEmpty().size)
    }

    @Test
    fun compararReportaPaginasDistintasEIdenticas() {
        val a = writeTextPdf(ctx.inputFile("a.pdf"), listOf("uno", "dos"))
        val same = writeTextPdf(ctx.inputFile("a2.pdf"), listOf("uno", "dos"))
        val different = writeTextPdf(ctx.inputFile("b.pdf"), listOf("uno", "tres", "extra"))
        val messages =
            ComparePdfMessages(
                readErrorA = "rA",
                readErrorB = "rB",
                generateError = "g",
                identical = "identicos",
                differencesFound = "difieren %1\$d de %2\$d",
                genericError = "e %1\$s",
                reportTitle = "Informe",
                reportPageHeader = "Pagina %1\$d",
                reportPageOnlyInA = "solo A",
                reportPageOnlyInB = "solo B",
                reportOnlyInALine = "A: %1\$s",
                reportOnlyInBLine = "B: %1\$s",
            )
        val useCase = ComparePdfUseCase(ctx)

        val identical = success(runBlocking { useCase(uriOf(a), uriOf(same), "c1", messages) })
        val diff = success(runBlocking { useCase(uriOf(a), uriOf(different), "c2", messages) })

        assertEquals("identicos", identical.message)
        assertEquals("difieren 2 de 3", diff.message)
        assertTrue(pageText(diff.outputFile, 1).contains("Informe"))
    }

    @Test
    fun detectarYRellenarCamposDeFormulario() {
        val form = ctx.inputFile("form.pdf")
        PdfDocument(PdfWriter(form)).use { pdf ->
            val page = pdf.addNewPage()
            val acro = PdfAcroForm.getAcroForm(pdf, true)
            acro.addField(PdfFormField.createText(pdf, Rectangle(50f, 700f, 200f, 30f), "nombre", ""), page)
            acro.addField(PdfFormField.createText(pdf, Rectangle(50f, 650f, 200f, 30f), "email", "a@b.c"), page)
        }
        val messages = FillFormMessages("sinValores", "r", "sinCampos", "g", "rellenados %1\$d", "e %1\$s")
        val fill = FillFormUseCase(ctx)

        val fields = runBlocking { DetectFormFieldsUseCase(ctx)(uriOf(form)) }
        val filled = success(runBlocking { fill(uriOf(form), mapOf("nombre" to "Ana"), "lleno", messages) })
        val unknown = runBlocking { fill(uriOf(form), mapOf("otro" to "x"), "x", messages) }
        val noValues = runBlocking { fill(uriOf(form), emptyMap(), "x", messages) }
        val noForm = runBlocking { DetectFormFieldsUseCase(ctx)(uriOf(writeAndroidPdf(ctx.inputFile("s.pdf")))) }

        assertEquals(setOf("nombre", "email"), fields.map { it.name }.toSet())
        assertEquals("a@b.c", fields.first { it.name == "email" }.currentValue)
        assertEquals("rellenados 1", filled.message)
        assertTrue(pageText(filled.outputFile).contains("Ana"))
        assertEquals(PdfToolResult.Error("sinCampos"), unknown)
        assertEquals(PdfToolResult.Error("sinValores"), noValues)
        assertTrue(noForm.isEmpty())
    }

    @Test
    fun ocrDevuelveErrorDeLecturaConArchivoVacioYNoTocaMlKit() {
        val empty = ctx.inputFile("vacio.pdf").apply { writeBytes(ByteArray(0)) }
        val messages = OcrPdfMessages("lectura", "np", "yaTieneTexto", "sinTexto", "g", "ok %1\$d %2\$d", "e %1\$s")

        val result = runBlocking { OcrPdfUseCase(ctx)(uriOf(empty), "ocr", messages) }

        assertEquals(PdfToolResult.Error("lectura"), result)
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
        assertTrue(ctx.outputDir("pdftools").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun ocrConPdfConTextoRealNoDejaArchivosDeSalida() {
        val pdf = writeTextPdf(ctx.inputFile("con_texto.pdf"), listOf("ya tiene texto"))
        val messages = OcrPdfMessages("lectura", "np", "yaTieneTexto", "sinTexto", "g", "ok %1\$d %2\$d", "e %1\$s")

        val result = runBlocking { OcrPdfUseCase(ctx)(uriOf(pdf), "ocr", messages) }

        // Las páginas con texto se omiten sin invocar el reconocimiento: la corrida termina en error de
        // "ya tiene texto" (o genérico si ML Kit no pudiera inicializarse) y no queda ninguna salida.
        assertTrue(result is PdfToolResult.Error)
        assertTrue(ctx.outputDir("pdftools").listFiles().orEmpty().isEmpty())
        assertTrue(ctx.cacheDir.listFiles().orEmpty().isEmpty())
    }
}
