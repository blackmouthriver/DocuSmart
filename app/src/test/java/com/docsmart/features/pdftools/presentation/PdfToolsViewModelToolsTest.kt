package com.docsmart.features.pdftools.presentation

import android.net.Uri
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.docsmart.features.pdftools.domain.usecase.CropPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.EditTextPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.ExtractImagesFromPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.FillFormUseCase
import com.docsmart.features.pdftools.domain.usecase.MergePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.NumberPagesUseCase
import com.docsmart.features.pdftools.domain.usecase.OcrPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.PageNumberFormat
import com.docsmart.features.pdftools.domain.usecase.RedactPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.RedactionRect
import com.docsmart.features.pdftools.domain.usecase.ReorderPagesUseCase
import com.docsmart.features.pdftools.domain.usecase.SignPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.SplitPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.WatermarkPdfUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Ronda 17: PdfToolsViewModelTest solo ejercita COMPRIMIR/ROTAR/FIRMAR/COMPARAR.
 * Estos tests cubren el resto de las ramas de runBasicTool()/runAdvancedTool():
 * que cada herramienta llame a SU use case con los parámetros del estado, y que
 * el resultado (Success, MultiSuccess o Error) actualice la UI y el contador
 * diario como corresponde.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfToolsViewModelToolsTest {
    private lateinit var mergePdf: MergePdfUseCase
    private lateinit var splitPdf: SplitPdfUseCase
    private lateinit var numberPages: NumberPagesUseCase
    private lateinit var watermarkPdf: WatermarkPdfUseCase
    private lateinit var reorderPages: ReorderPagesUseCase
    private lateinit var redactPdf: RedactPdfUseCase
    private lateinit var cropPdf: CropPdfUseCase
    private lateinit var editTextPdf: EditTextPdfUseCase
    private lateinit var signPdf: SignPdfUseCase
    private lateinit var fillForm: FillFormUseCase
    private lateinit var ocrPdf: OcrPdfUseCase
    private lateinit var extractImages: ExtractImagesFromPdfUseCase
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var messages: PdfToolMessages
    private lateinit var viewModel: PdfToolsViewModel

    private val uriA = mockk<Uri>()
    private val uriB = mockk<Uri>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mergePdf = mockk()
        splitPdf = mockk()
        numberPages = mockk()
        watermarkPdf = mockk()
        reorderPages = mockk()
        redactPdf = mockk()
        cropPdf = mockk()
        editTextPdf = mockk()
        signPdf = mockk()
        fillForm = mockk()
        ocrPdf = mockk()
        extractImages = mockk()
        dailyLimitManager = mockk(relaxed = true)
        val premiumManager = mockk<PremiumManager>()

        every { dailyLimitManager.canUsePdfTool(any()) } returns true
        every { dailyLimitManager.getPdfToolCount(any()) } returns 0
        every { dailyLimitManager.getPdfToolLimit() } returns 3
        every { premiumManager.canPerform(any()) } answers { firstArg<() -> Boolean>().invoke() }

        messages =
            PdfToolMessages(
                merge = mockk(),
                split = mockk(),
                compress = mockk(),
                rotate = mockk(),
                numberPages = mockk(),
                watermark = mockk(),
                reorderPages = mockk(),
                compare = mockk(),
                redact = mockk(),
                crop = mockk(),
                editText = mockk(),
                sign = mockk(),
                fillForm = mockk(),
                ocr = mockk(),
                extractImages = mockk(),
                genericError = "error generico",
            )

        viewModel =
            PdfToolsViewModel(
                mergePdf = mergePdf,
                splitPdf = splitPdf,
                compressPdf = mockk(),
                rotatePdf = mockk(),
                numberPagesPdf = numberPages,
                watermarkPdf = watermarkPdf,
                reorderPagesPdf = reorderPages,
                comparePdf = mockk(),
                redactPdf = redactPdf,
                cropPdf = cropPdf,
                editTextPdf = editTextPdf,
                signPdf = signPdf,
                detectFormFields = mockk(),
                fillForm = fillForm,
                ocrPdf = ocrPdf,
                extractImagesFromPdf = extractImages,
                dailyLimitManager = dailyLimitManager,
                premiumManager = premiumManager,
                adManager = mockk<AdManager>(),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun success(name: String = "out.pdf") = PdfToolResult.Success(File(name), "listo")

    private fun start(
        tool: PdfTool,
        vararg uris: Uri,
    ) {
        viewModel.selectTool(tool)
        viewModel.onPdfsSelected(uris.toList())
    }

    @Test
    fun `COMBINAR pasa todos los PDFs elegidos en orden y registra el uso`() =
        runTest {
            coEvery { mergePdf(any(), any(), any()) } returns success()
            start(PdfTool.MERGE, uriA, uriB)

            viewModel.execute(messages)

            coVerify(exactly = 1) { mergePdf(listOf(uriA, uriB), null, messages.merge) }
            verify(exactly = 1) { dailyLimitManager.registerPdfTool("MERGE") }
            (viewModel.uiState.value.result is PdfToolResult.Success) shouldBe true
            viewModel.uiState.value.isProcessing shouldBe false
        }

    @Test
    fun `DIVIDIR usa el rango de paginas elegido`() =
        runTest {
            coEvery { splitPdf(any(), any(), any(), any(), any()) } returns success()
            start(PdfTool.SPLIT, uriA)
            viewModel.onSplitFromPageChange(2)
            viewModel.onSplitToPageChange(5)

            viewModel.execute(messages)

            coVerify(exactly = 1) { splitPdf(uriA, 2, 5, null, messages.split) }
        }

    @Test
    fun `NUMERAR PAGINAS usa el formato elegido`() =
        runTest {
            coEvery { numberPages(any(), any(), any(), any()) } returns success()
            start(PdfTool.NUMBER_PAGES, uriA)
            viewModel.onPageNumberFormatChange(PageNumberFormat.NUMBER_ONLY)

            viewModel.execute(messages)

            coVerify(exactly = 1) { numberPages(uriA, PageNumberFormat.NUMBER_ONLY, null, messages.numberPages) }
        }

    @Test
    fun `MARCA DE AGUA usa el texto escrito`() =
        runTest {
            coEvery { watermarkPdf(any(), any(), any(), any()) } returns success()
            start(PdfTool.WATERMARK, uriA)
            viewModel.onWatermarkTextChange("CONFIDENCIAL")

            viewModel.execute(messages)

            coVerify(exactly = 1) { watermarkPdf(uriA, "CONFIDENCIAL", null, messages.watermark) }
        }

    @Test
    fun `REORDENAR usa el orden actual de paginas tras mover y quitar`() =
        runTest {
            coEvery { reorderPages(any(), any(), any(), any()) } returns success()
            start(PdfTool.REORDER_PAGES, uriA)
            viewModel.onPagesLoaded(4)
            viewModel.onReorderPage(0, 3)
            viewModel.onRemovePage(3)

            viewModel.execute(messages)

            // [1,2,3,4] -> mover la 1 al final: [2,3,4,1] -> quitar la 3: [2,4,1]
            coVerify(exactly = 1) { reorderPages(uriA, listOf(2, 4, 1), null, messages.reorderPages) }
        }

    @Test
    fun `EXTRAER IMAGENES con MultiSuccess registra el uso y guarda la lista`() =
        runTest {
            val files = listOf(File("a.png"), File("b.png"))
            coEvery { extractImages(any(), any(), any()) } returns PdfToolResult.MultiSuccess(files, "2 imagenes")
            start(PdfTool.EXTRACT_IMAGES, uriA)

            viewModel.execute(messages)

            verify(exactly = 1) { dailyLimitManager.registerPdfTool("EXTRACT_IMAGES") }
            val result = viewModel.uiState.value.result
            (result is PdfToolResult.MultiSuccess) shouldBe true
            (result as PdfToolResult.MultiSuccess).outputFiles shouldBe files
        }

    @Test
    fun `CENSURAR envia los rectangulos marcados`() =
        runTest {
            val rect = RedactionRect(1, 0.1f, 0.2f, 0.3f, 0.4f)
            coEvery { redactPdf(any(), any(), any(), any()) } returns success()
            start(PdfTool.REDACT, uriA)
            viewModel.onAddRedactionRect(rect)

            viewModel.execute(messages)

            coVerify(exactly = 1) { redactPdf(uriA, listOf(rect), null, messages.redact) }
        }

    @Test
    fun `RECORTAR usa el margen elegido ya acotado`() =
        runTest {
            coEvery { cropPdf(any(), any(), any(), any()) } returns success()
            start(PdfTool.CROP, uriA)
            viewModel.onCropMarginChange(99)

            viewModel.execute(messages)

            coVerify(exactly = 1) { cropPdf(uriA, 40, null, messages.crop) }
        }

    @Test
    fun `EDITAR TEXTO envia busqueda y reemplazo`() =
        runTest {
            coEvery { editTextPdf(any(), any(), any(), any(), any()) } returns success()
            start(PdfTool.EDIT_TEXT, uriA)
            viewModel.onEditSearchTextChange("foo")
            viewModel.onEditReplaceTextChange("bar")

            viewModel.execute(messages)

            coVerify(exactly = 1) { editTextPdf(uriA, "foo", "bar", null, messages.editText) }
        }

    @Test
    fun `FIRMAR con firma capturada usa los bytes y la pagina elegida`() =
        runTest {
            val bytes = byteArrayOf(1, 2, 3)
            coEvery { signPdf(any(), any(), any(), any(), any()) } returns success()
            start(PdfTool.SIGN, uriA)
            viewModel.onSignatureTotalPagesLoaded(5)
            viewModel.onSignaturePageChange(4)
            viewModel.onSignatureCaptured(bytes)

            viewModel.execute(messages)

            coVerify(exactly = 1) { signPdf(uriA, bytes, 4, null, messages.sign) }
        }

    @Test
    fun `RELLENAR FORMULARIO envia los valores editados`() =
        runTest {
            coEvery { fillForm(any(), any(), any(), any()) } returns success()
            start(PdfTool.FILL_FORM, uriA)
            viewModel.onFormFieldValueChange("nombre", "Ana")
            viewModel.onFormFieldValueChange("ciudad", "Lima")

            viewModel.execute(messages)

            coVerify(exactly = 1) {
                fillForm(uriA, mapOf("nombre" to "Ana", "ciudad" to "Lima"), null, messages.fillForm)
            }
        }

    @Test
    fun `OCR llama a su use case con el PDF elegido`() =
        runTest {
            coEvery { ocrPdf(any(), any(), any()) } returns success()
            start(PdfTool.OCR, uriA)

            viewModel.execute(messages)

            coVerify(exactly = 1) { ocrPdf(uriA, null, messages.ocr) }
            verify(exactly = 1) { dailyLimitManager.registerPdfTool("OCR") }
        }

    @Test
    fun `el nombre de salida se sanea antes de llegar al use case`() =
        runTest {
            coEvery { ocrPdf(any(), any(), any()) } returns success()
            start(PdfTool.OCR, uriA)
            viewModel.onOutputFileNameChange("../../etc/mi:archivo")

            viewModel.execute(messages)

            coVerify(exactly = 1) { ocrPdf(uriA, "miarchivo", messages.ocr) }
        }

    @Test
    fun `un Error del use case se muestra y no consume el limite diario`() =
        runTest {
            coEvery { ocrPdf(any(), any(), any()) } returns PdfToolResult.Error("PDF ilegible")
            start(PdfTool.OCR, uriA)

            viewModel.execute(messages)

            viewModel.uiState.value.errorMessage shouldBe "PDF ilegible"
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `dismissError limpia el mensaje de error`() =
        runTest {
            coEvery { ocrPdf(any(), any(), any()) } returns PdfToolResult.Error("PDF ilegible")
            start(PdfTool.OCR, uriA)
            viewModel.execute(messages)

            viewModel.dismissError()

            viewModel.uiState.value.errorMessage shouldBe null
        }

    @Test
    fun `dismissLimitDialog cierra el dialogo de limite`() {
        every { dailyLimitManager.canUsePdfTool(any()) } returns false
        start(PdfTool.OCR, uriA)
        viewModel.execute(messages)
        viewModel.uiState.value.showLimitDialog shouldBe true

        viewModel.dismissLimitDialog()

        viewModel.uiState.value.showLimitDialog shouldBe false
    }

    @Test
    fun `selectTool NONE deja el contador en cero sin consultar el limite por herramienta`() {
        every { dailyLimitManager.getPdfToolCount("OCR") } returns 2
        viewModel.selectTool(PdfTool.OCR)
        viewModel.uiState.value.toolUseCount shouldBe 2

        viewModel.selectTool(PdfTool.NONE)

        viewModel.uiState.value.selectedTool shouldBe PdfTool.NONE
        viewModel.uiState.value.toolUseCount shouldBe 0
    }

    @Test
    fun `reset vuelve al estado inicial`() {
        start(PdfTool.SPLIT, uriA)
        viewModel.onSplitToPageChange(9)

        viewModel.reset()

        viewModel.uiState.value shouldBe PdfToolsUiState()
    }

    @Test
    fun `onOutputFileNameChange y onPdfsSelected reinician el nombre de salida`() {
        viewModel.onOutputFileNameChange("informe")
        viewModel.uiState.value.outputFileName shouldBe "informe"

        viewModel.onPdfsSelected(listOf(uriA))

        viewModel.uiState.value.outputFileName shouldBe ""
    }

    @Test
    fun `los selectores de COMPARAR limpian resultado y nombre de salida`() {
        viewModel.onOutputFileNameChange("x")

        viewModel.onComparePdfASelected(uriA)
        viewModel.onComparePdfBSelected(uriB)

        val state = viewModel.uiState.value
        state.comparePdfA shouldBe uriA
        state.comparePdfB shouldBe uriB
        state.outputFileName shouldBe ""
        state.errorMessage shouldBe null
    }

    @Test
    fun `onRedactionTotalPagesLoaded nunca baja de una pagina`() {
        viewModel.onRedactionTotalPagesLoaded(0)

        viewModel.uiState.value.redactionTotalPages shouldBe 1
    }
}
