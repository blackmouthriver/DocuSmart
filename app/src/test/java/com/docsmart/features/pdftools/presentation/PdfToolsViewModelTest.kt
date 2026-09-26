package com.docsmart.features.pdftools.presentation

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.docsmart.features.pdftools.domain.usecase.ComparePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.CompressPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.DetectFormFieldsUseCase
import com.docsmart.features.pdftools.domain.usecase.FormFieldInfo
import com.docsmart.features.pdftools.domain.usecase.PageNumberFormat
import com.docsmart.features.pdftools.domain.usecase.RedactionRect
import com.docsmart.features.pdftools.domain.usecase.RotatePdfUseCase
import com.docsmart.features.pdftools.domain.usecase.SignPdfUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
 * Ronda 15: PdfToolsViewModel estaba al ~30% de cobertura. Cubre las
 * transiciones de estado de execute() (éxito, error, límite diario, doble
 * toque, excepciones, cancelación), el guardado en Descargas y la detección de
 * campos de formulario. shareResult() no se testea: construye Intent/
 * FileProvider reales (android.jar, no mockeados en JVM puro).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfToolsViewModelTest {
    private lateinit var compressPdf: CompressPdfUseCase
    private lateinit var rotatePdf: RotatePdfUseCase
    private lateinit var signPdf: SignPdfUseCase
    private lateinit var comparePdf: ComparePdfUseCase
    private lateinit var detectFormFields: DetectFormFieldsUseCase
    private lateinit var adManager: AdManager
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var premiumManager: PremiumManager
    private lateinit var messages: PdfToolMessages
    private lateinit var viewModel: PdfToolsViewModel

    private val uriA = mockk<Uri>()
    private val uriB = mockk<Uri>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        compressPdf = mockk()
        rotatePdf = mockk()
        signPdf = mockk()
        comparePdf = mockk()
        detectFormFields = mockk()
        adManager = mockk()
        dailyLimitManager = mockk(relaxed = true)
        premiumManager = mockk()

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
                mergePdf = mockk(),
                splitPdf = mockk(),
                compressPdf = compressPdf,
                rotatePdf = rotatePdf,
                numberPagesPdf = mockk(),
                watermarkPdf = mockk(),
                reorderPagesPdf = mockk(),
                comparePdf = comparePdf,
                redactPdf = mockk(),
                cropPdf = mockk(),
                editTextPdf = mockk(),
                signPdf = signPdf,
                detectFormFields = detectFormFields,
                fillForm = mockk(),
                ocrPdf = mockk(),
                extractImagesFromPdf = mockk(),
                dailyLimitManager = dailyLimitManager,
                premiumManager = premiumManager,
                adManager = adManager,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun success(name: String = "out.pdf") = PdfToolResult.Success(File(name), "listo")

    private fun selectCompressWithPdf() {
        viewModel.selectTool(PdfTool.COMPRESS)
        viewModel.onPdfsSelected(listOf(uriA))
    }

    private fun stubCompress(result: PdfToolResult) {
        coEvery { compressPdf(any(), any(), any(), any()) } returns result
    }

    // ── selectTool / edición de parámetros ──────────────────────────────────

    @Test
    fun `selectTool reinicia el estado y carga el contador y limite diarios de esa herramienta`() {
        every { dailyLimitManager.getPdfToolCount("COMPRESS") } returns 2
        every { dailyLimitManager.getPdfToolLimit() } returns 5
        viewModel.onPdfsSelected(listOf(uriA))

        viewModel.selectTool(PdfTool.COMPRESS)

        val state = viewModel.uiState.value
        state.selectedTool shouldBe PdfTool.COMPRESS
        state.selectedPdfs shouldBe emptyList()
        state.toolUseCount shouldBe 2
        state.toolUseLimit shouldBe 5
    }

    @Test
    fun `los parametros numericos se acotan a su rango valido`() {
        viewModel.onCompressionQualityChange(5)
        viewModel.uiState.value.compressionQuality shouldBe 20
        viewModel.onCompressionQualityChange(500)
        viewModel.uiState.value.compressionQuality shouldBe 100

        viewModel.onCropMarginChange(-3)
        viewModel.uiState.value.cropMarginPercent shouldBe 0
        viewModel.onCropMarginChange(99)
        viewModel.uiState.value.cropMarginPercent shouldBe 40

        viewModel.onSplitFromPageChange(0)
        viewModel.uiState.value.splitFromPage shouldBe 1
        viewModel.onSplitToPageChange(-4)
        viewModel.uiState.value.splitToPage shouldBe 1
    }

    @Test
    fun `onRemovePage nunca deja el documento sin paginas`() {
        viewModel.onPagesLoaded(2)

        viewModel.onRemovePage(1)
        viewModel.uiState.value.pageOrder shouldBe listOf(2)

        viewModel.onRemovePage(2)
        viewModel.uiState.value.pageOrder shouldBe listOf(2)
    }

    @Test
    fun `onReorderPage mueve la pagina y descarta indices fuera de rango`() {
        viewModel.onPagesLoaded(4)

        viewModel.onReorderPage(0, 2)
        viewModel.uiState.value.pageOrder shouldBe listOf(2, 3, 1, 4)

        viewModel.onReorderPage(0, 9)
        viewModel.onReorderPage(-1, 1)
        viewModel.uiState.value.pageOrder shouldBe listOf(2, 3, 1, 4)
    }

    @Test
    fun `las paginas de redaccion y firma se acotan al total cargado`() {
        viewModel.onRedactionTotalPagesLoaded(0)
        viewModel.uiState.value.redactionTotalPages shouldBe 1

        viewModel.onRedactionTotalPagesLoaded(5)
        viewModel.onRedactionPageChange(9)
        viewModel.uiState.value.redactionCurrentPage shouldBe 5
        viewModel.onRedactionPageChange(0)
        viewModel.uiState.value.redactionCurrentPage shouldBe 1

        viewModel.onSignatureTotalPagesLoaded(3)
        viewModel.onSignaturePageChange(8)
        viewModel.uiState.value.signaturePageNumber shouldBe 3
    }

    // onClearSignature() no tenia ninguna prueba (ni JVM ni instrumentada): la
    // pantalla de Firmar la invoca solo cuando hay una firma capturada, rama
    // que ningun test instrumentado ejercia sobre el ViewModel real.
    @Test
    fun `onClearSignature borra la firma capturada`() {
        viewModel.onSignatureCaptured(byteArrayOf(1, 2, 3))
        viewModel.uiState.value.signatureImageBytes?.contentEquals(byteArrayOf(1, 2, 3)) shouldBe true

        viewModel.onClearSignature()

        (viewModel.uiState.value.signatureImageBytes == null) shouldBe true
    }

    @Test
    fun `los rectangulos de redaccion se agregan deshacen y limpian`() {
        val r1 = RedactionRect(1, 0.1f, 0.1f, 0.2f, 0.2f)
        val r2 = RedactionRect(1, 0.5f, 0.5f, 0.2f, 0.2f)

        viewModel.onAddRedactionRect(r1)
        viewModel.onAddRedactionRect(r2)
        viewModel.uiState.value.redactionRects shouldBe listOf(r1, r2)

        viewModel.onUndoLastRedactionRect()
        viewModel.uiState.value.redactionRects shouldBe listOf(r1)

        viewModel.onClearRedactionRects()
        viewModel.uiState.value.redactionRects shouldBe emptyList()
    }

    @Test
    fun `addPdfsToMerge no duplica PDFs ya elegidos y removePdf quita uno`() {
        viewModel.addPdfsToMerge(listOf(uriA, uriB))
        viewModel.addPdfsToMerge(listOf(uriA))
        viewModel.uiState.value.selectedPdfs shouldBe listOf(uriA, uriB)

        viewModel.removePdf(uriA)
        viewModel.uiState.value.selectedPdfs shouldBe listOf(uriB)
    }

    @Test
    fun `onPdfsSelected limpia resultado firma y campos de formulario del PDF anterior`() {
        viewModel.onSignatureCaptured(byteArrayOf(1, 2))
        viewModel.onFormFieldValueChange("nombre", "Ana")
        viewModel.onPageNumberFormatChange(PageNumberFormat.PAGE_OF_TOTAL)

        viewModel.onPdfsSelected(listOf(uriA))

        val state = viewModel.uiState.value
        (state.signatureImageBytes == null) shouldBe true
        state.formFieldValues shouldBe emptyMap()
        state.selectedPdfs shouldBe listOf(uriA)
    }

    // ── execute() ───────────────────────────────────────────────────────────

    @Test
    fun `execute sin seleccion o sin herramienta no llama a ningun use case`() =
        runTest {
            viewModel.execute(messages)
            viewModel.selectTool(PdfTool.COMPRESS)
            viewModel.execute(messages)

            coVerify(exactly = 0) { compressPdf(any(), any(), any(), any()) }
            viewModel.uiState.value.isProcessing shouldBe false
        }

    @Test
    fun `execute con exito guarda el resultado, registra el uso y refresca el contador`() =
        runTest {
            selectCompressWithPdf()
            viewModel.onCompressionQualityChange(40)
            viewModel.onOutputFileNameChange("../mi informe")
            stubCompress(success())
            every { dailyLimitManager.getPdfToolCount("COMPRESS") } returns 1

            viewModel.execute(messages)

            val state = viewModel.uiState.value
            state.isProcessing shouldBe false
            state.result shouldBe success()
            state.errorMessage shouldBe null
            state.toolUseCount shouldBe 1
            // El nombre se sanea antes de llegar al use case (path traversal).
            coVerify(exactly = 1) { compressPdf(uriA, 40, "mi informe", messages.compress) }
            verify(exactly = 1) { dailyLimitManager.registerPdfTool("COMPRESS") }
        }

    @Test
    fun `execute con nombre en blanco pasa null como nombre de salida`() =
        runTest {
            selectCompressWithPdf()
            viewModel.onOutputFileNameChange("   ")
            stubCompress(success())

            viewModel.execute(messages)

            coVerify(exactly = 1) { compressPdf(uriA, 60, null, messages.compress) }
        }

    @Test
    fun `execute con resultado Error muestra el mensaje y NO consume el limite diario`() =
        runTest {
            selectCompressWithPdf()
            stubCompress(PdfToolResult.Error("no se pudo"))

            viewModel.execute(messages)

            val state = viewModel.uiState.value
            state.errorMessage shouldBe "no se pudo"
            state.isProcessing shouldBe false
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `execute con el limite diario alcanzado muestra el dialogo y no procesa`() =
        runTest {
            every { dailyLimitManager.canUsePdfTool("COMPRESS") } returns false
            selectCompressWithPdf()

            viewModel.execute(messages)

            viewModel.uiState.value.showLimitDialog shouldBe true
            viewModel.uiState.value.isProcessing shouldBe false
            coVerify(exactly = 0) { compressPdf(any(), any(), any(), any()) }

            viewModel.dismissLimitDialog()
            viewModel.uiState.value.showLimitDialog shouldBe false
        }

    @Test
    fun `un doble toque en Ejecutar procesa una sola vez y cuenta un solo uso`() =
        runTest {
            selectCompressWithPdf()
            val gate = CompletableDeferred<PdfToolResult>()
            coEvery { compressPdf(any(), any(), any(), any()) } coAnswers { gate.await() }

            viewModel.execute(messages)
            viewModel.execute(messages)

            viewModel.uiState.value.isProcessing shouldBe true
            coVerify(exactly = 1) { compressPdf(any(), any(), any(), any()) }

            gate.complete(success())

            viewModel.uiState.value.isProcessing shouldBe false
            verify(exactly = 1) { dailyLimitManager.registerPdfTool("COMPRESS") }
        }

    @Test
    fun `una excepcion no prevista del use case resetea isProcessing y muestra el error generico`() =
        runTest {
            selectCompressWithPdf()
            coEvery { compressPdf(any(), any(), any(), any()) } throws IllegalStateException("boom")

            viewModel.execute(messages)

            val state = viewModel.uiState.value
            state.isProcessing shouldBe false
            state.errorMessage shouldBe "error generico"
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `un OutOfMemoryError del use case resetea isProcessing y muestra el error generico`() =
        runTest {
            selectCompressWithPdf()
            coEvery { compressPdf(any(), any(), any(), any()) } throws OutOfMemoryError("sin memoria")

            viewModel.execute(messages)

            viewModel.uiState.value.isProcessing shouldBe false
            viewModel.uiState.value.errorMessage shouldBe "error generico"
        }

    // Bug real corregido en la ronda 15: el catch (Exception) de execute()
    // atrapaba CancellationException y la mostraba como error generico.
    @Test
    fun `una cancelacion no se muestra como error generico`() =
        runTest {
            selectCompressWithPdf()
            coEvery { compressPdf(any(), any(), any(), any()) } throws CancellationException("cancelado")

            viewModel.execute(messages)

            viewModel.uiState.value.errorMessage shouldBe null
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    // Bug real corregido en la ronda 15: cambiar de herramienta a mitad de una
    // ejecucion dejaba la corrutina vieja viva; al terminar escribia su
    // resultado y consumia un uso diario sobre la pantalla de la otra herramienta.
    @Test
    fun `cambiar de herramienta durante el procesamiento cancela la ejecucion en curso`() =
        runTest {
            selectCompressWithPdf()
            val gate = CompletableDeferred<PdfToolResult>()
            coEvery { compressPdf(any(), any(), any(), any()) } coAnswers { gate.await() }
            viewModel.execute(messages)
            viewModel.uiState.value.isProcessing shouldBe true

            viewModel.selectTool(PdfTool.ROTATE)
            gate.complete(success())

            val state = viewModel.uiState.value
            state.selectedTool shouldBe PdfTool.ROTATE
            state.isProcessing shouldBe false
            (state.result == null) shouldBe true
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `reset durante el procesamiento tambien cancela la ejecucion en curso`() =
        runTest {
            selectCompressWithPdf()
            val gate = CompletableDeferred<PdfToolResult>()
            coEvery { compressPdf(any(), any(), any(), any()) } coAnswers { gate.await() }
            viewModel.execute(messages)

            viewModel.reset()
            gate.complete(success())

            viewModel.uiState.value shouldBe PdfToolsUiState()
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `FIRMAR sin firma capturada no ejecuta nada y no deja isProcessing trabado`() =
        runTest {
            viewModel.selectTool(PdfTool.SIGN)
            viewModel.onPdfsSelected(listOf(uriA))

            viewModel.execute(messages)

            viewModel.uiState.value.isProcessing shouldBe false
            (viewModel.uiState.value.result == null) shouldBe true
            coVerify(exactly = 0) { signPdf(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { dailyLimitManager.registerPdfTool(any()) }
        }

    @Test
    fun `COMPARAR exige los dos PDFs y usa comparePdfA y comparePdfB`() =
        runTest {
            viewModel.selectTool(PdfTool.COMPARE)
            viewModel.onComparePdfASelected(uriA)
            viewModel.execute(messages)
            coVerify(exactly = 0) { comparePdf(any(), any(), any(), any()) }

            viewModel.onComparePdfBSelected(uriB)
            coEvery { comparePdf(any(), any(), any(), any()) } returns success("cmp.pdf")
            viewModel.execute(messages)

            coVerify(exactly = 1) { comparePdf(uriA, uriB, null, messages.compare) }
            viewModel.uiState.value.result shouldBe success("cmp.pdf")
        }

    @Test
    fun `ROTAR usa los grados elegidos`() =
        runTest {
            viewModel.selectTool(PdfTool.ROTATE)
            viewModel.onPdfsSelected(listOf(uriA))
            viewModel.onRotationDegreesChange(270)
            coEvery { rotatePdf(any(), any(), any(), any()) } returns success()

            viewModel.execute(messages)

            coVerify(exactly = 1) { rotatePdf(uriA, 270, null, messages.rotate) }
        }

    // ── Descargas ───────────────────────────────────────────────────────────

    private fun stateWithResult(result: PdfToolResult) {
        selectCompressWithPdf()
        stubCompress(result)
        viewModel.execute(messages)
    }

    @Test
    fun `saveToDownloads marca guardado cuando el archivo se guarda`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returns true
            stateWithResult(success())

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            viewModel.uiState.value.savedToDownloads shouldBe true
            viewModel.uiState.value.isSaving shouldBe false
            coVerify(exactly = 1) { DownloadsSaver.saveFile(any(), File("out.pdf"), "application/pdf", any()) }
        }

    @Test
    fun `saveToDownloads sin resultado no hace nada`() =
        runTest {
            mockkObject(DownloadsSaver)

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            coVerify(exactly = 0) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            viewModel.uiState.value.isSaving shouldBe false
        }

    @Test
    fun `saveToDownloads con fallo muestra el error y libera isSaving`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returns false
            stateWithResult(success())

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            viewModel.uiState.value.errorMessage shouldBe "no se guardo"
            viewModel.uiState.value.savedToDownloads shouldBe false
            viewModel.uiState.value.isSaving shouldBe false
        }

    // Bug real corregido en la ronda 15: sin try/catch, una excepcion de
    // DownloadsSaver dejaba isSaving=true para siempre (o crasheaba la app).
    @Test
    fun `saveToDownloads con una excepcion libera isSaving y muestra el error`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } throws IllegalStateException("x")
            stateWithResult(success())

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            viewModel.uiState.value.errorMessage shouldBe "no se guardo"
            viewModel.uiState.value.isSaving shouldBe false
            viewModel.uiState.value.savedToDownloads shouldBe false
        }

    @Test
    fun `un doble toque en Guardar guarda una sola vez`() =
        runTest {
            mockkObject(DownloadsSaver)
            val gate = CompletableDeferred<Boolean>()
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } coAnswers { gate.await() }
            stateWithResult(success())
            val context = mockk<Context>()

            viewModel.saveToDownloads(context, "no se guardo")
            viewModel.saveToDownloads(context, "no se guardo")

            viewModel.uiState.value.isSaving shouldBe true
            coVerify(exactly = 1) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            gate.complete(true)
            viewModel.uiState.value.savedToDownloads shouldBe true
        }

    @Test
    fun `saveToDownloads de varios archivos intenta todos aunque el primero falle`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returnsMany listOf(false, true)
            val multi = PdfToolResult.MultiSuccess(listOf(File("a.png"), File("b.png")), "listo")
            selectCompressWithPdf()
            stubCompress(multi)
            viewModel.execute(messages)

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            coVerify(exactly = 2) { DownloadsSaver.saveFile(any(), any(), "image/png", any()) }
            viewModel.uiState.value.errorMessage shouldBe "no se guardo"
            viewModel.uiState.value.savedToDownloads shouldBe false
        }

    @Test
    fun `saveToDownloads con un MultiSuccess vacio no marca guardado falsamente`() =
        runTest {
            mockkObject(DownloadsSaver)
            selectCompressWithPdf()
            stubCompress(PdfToolResult.MultiSuccess(emptyList(), "listo"))
            viewModel.execute(messages)

            viewModel.saveToDownloads(mockk<Context>(), "no se guardo")

            viewModel.uiState.value.savedToDownloads shouldBe false
            coVerify(exactly = 0) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
        }

    // ── Detección de campos de formulario ──────────────────────────────────

    @Test
    fun `onDetectFormFields carga los campos y sus valores actuales`() =
        runTest {
            coEvery { detectFormFields(uriA) } returns listOf(FormFieldInfo("nombre", "Ana"))

            viewModel.onDetectFormFields(uriA)

            val state = viewModel.uiState.value
            state.formFieldsDetected shouldBe true
            state.formFields shouldBe listOf(FormFieldInfo("nombre", "Ana"))
            state.formFieldValues shouldBe mapOf("nombre" to "Ana")
        }

    // Bug real corregido en la ronda 15: una excepcion del use case escapaba de
    // la corrutina sin atrapar y crasheaba la app.
    @Test
    fun `onDetectFormFields con una excepcion no crashea y se trata como sin campos`() =
        runTest {
            coEvery { detectFormFields(uriA) } throws IllegalStateException("boom")

            viewModel.onDetectFormFields(uriA)

            viewModel.uiState.value.formFieldsDetected shouldBe true
            viewModel.uiState.value.formFields shouldBe emptyList()
        }

    @Test
    fun `un resultado tardio de un PDF ya reemplazado no pisa los campos del PDF nuevo`() =
        runTest {
            val gateA = CompletableDeferred<List<FormFieldInfo>>()
            coEvery { detectFormFields(uriA) } coAnswers { gateA.await() }
            coEvery { detectFormFields(uriB) } returns listOf(FormFieldInfo("b", "2"))

            viewModel.onDetectFormFields(uriA)
            viewModel.onDetectFormFields(uriB)
            gateA.complete(listOf(FormFieldInfo("a", "1")))

            viewModel.uiState.value.formFields shouldBe listOf(FormFieldInfo("b", "2"))
        }

    // ── Rewarded ad ─────────────────────────────────────────────────────────

    @Test
    fun `ver anuncio otorga un uso extra y refresca el limite`() {
        val activity = mockk<Activity>()
        val onRewarded = slot<() -> Unit>()
        every { adManager.showRewardedAd(activity, capture(onRewarded), any()) } just runs
        viewModel.selectTool(PdfTool.COMPRESS)

        viewModel.watchAdForTool(activity, "sin anuncio")
        every { dailyLimitManager.getPdfToolLimit() } returns 4
        onRewarded.captured.invoke()

        verify(exactly = 1) { dailyLimitManager.addRewardedPdfTool() }
        viewModel.uiState.value.toolUseLimit shouldBe 4
    }

    @Test
    fun `si no hay anuncio disponible se muestra el mensaje y se cierra el dialogo`() {
        val activity = mockk<Activity>()
        val onFailed = slot<() -> Unit>()
        every { adManager.showRewardedAd(activity, any(), capture(onFailed)) } just runs
        every { dailyLimitManager.canUsePdfTool(any()) } returns false
        selectCompressWithPdf()
        viewModel.execute(messages)
        viewModel.uiState.value.showLimitDialog shouldBe true

        viewModel.watchAdForTool(activity, "sin anuncio")
        onFailed.captured.invoke()

        viewModel.uiState.value.showLimitDialog shouldBe false
        viewModel.uiState.value.errorMessage shouldBe "sin anuncio"
        viewModel.dismissError()
        viewModel.uiState.value.errorMessage shouldBe null
    }
}
