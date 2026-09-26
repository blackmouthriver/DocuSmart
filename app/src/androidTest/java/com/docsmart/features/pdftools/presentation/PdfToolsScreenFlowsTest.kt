package com.docsmart.features.pdftools.presentation

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.ui.components.AppLibraryPickerViewModel
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.docsmart.features.pdftools.domain.usecase.ExtractImagesFromPdfUseCase
import com.docsmart.features.pdftools.domain.usecase.RotatePdfUseCase
import com.docsmart.features.pdftools.presentation.components.RecordingContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * PdfToolsScreen: composición de las 15 herramientas (con y sin PDF elegido) y tarjetas de resultado con casos de
 * uso simulados. Nada real de Descargas: "Guardar" usa un ContentResolver simulado y "Compartir" un contexto que
 * registra los Intents.
 */
class PdfToolsScreenFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var files: IsolatedContext
    private lateinit var strings: Context

    @Before
    fun setUp() {
        files = newIsolatedContext("pdftoolsscreen")
        strings = forceLocale(files.baseContext, "es-ES")
    }

    @After
    fun tearDown() {
        files.cleanUp()
    }

    private fun text(id: Int): String = strings.getString(id)

    private fun buildViewModel(
        rotate: RotatePdfUseCase = mockk(relaxed = true),
        extractImages: ExtractImagesFromPdfUseCase = mockk(relaxed = true),
        canUsePdfTool: Boolean = true,
        isRewardedReady: Boolean = false,
        toolUseCount: Int = 0,
        toolUseLimit: Int = 3,
    ): PdfToolsViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(false)
        every { adManager.isInitialized } returns MutableStateFlow(true)
        every { adManager.isRewardedReady } returns MutableStateFlow(isRewardedReady)
        val dailyLimitManager = mockk<DailyLimitManager>(relaxed = true)
        every { dailyLimitManager.canUsePdfTool(any()) } returns canUsePdfTool
        every { dailyLimitManager.getPdfToolCount(any()) } returns toolUseCount
        every { dailyLimitManager.getPdfToolLimit() } returns toolUseLimit
        val premiumManager = mockk<PremiumManager>(relaxed = true)
        every { premiumManager.isPremium } returns MutableStateFlow(false)
        every { premiumManager.canPerform(any()) } answers { firstArg<() -> Boolean>().invoke() }
        return PdfToolsViewModel(
            mergePdf = mockk(relaxed = true),
            splitPdf = mockk(relaxed = true),
            compressPdf = mockk(relaxed = true),
            rotatePdf = rotate,
            numberPagesPdf = mockk(relaxed = true),
            watermarkPdf = mockk(relaxed = true),
            reorderPagesPdf = mockk(relaxed = true),
            comparePdf = mockk(relaxed = true),
            redactPdf = mockk(relaxed = true),
            cropPdf = mockk(relaxed = true),
            editTextPdf = mockk(relaxed = true),
            signPdf = mockk(relaxed = true),
            detectFormFields = mockk(relaxed = true),
            fillForm = mockk(relaxed = true),
            ocrPdf = mockk(relaxed = true),
            extractImagesFromPdf = extractImages,
            dailyLimitManager = dailyLimitManager,
            premiumManager = premiumManager,
            adManager = adManager,
        )
    }

    // AppLibraryPickerViewModel es un ViewModel real (no una interfaz) con una
    // sola dependencia -- se arma a mano con un DocumentRepository simulado en
    // vez de mockearlo entero, mismo criterio que PdfToolsViewModel.
    private fun buildLibraryPickerViewModel(documents: List<DocumentUiModel> = emptyList()): AppLibraryPickerViewModel {
        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadAllDocuments() } returns documents
        return AppLibraryPickerViewModel(repository)
    }

    private fun setScreen(
        viewModel: PdfToolsViewModel,
        override: Context? = null,
        libraryPickerViewModel: AppLibraryPickerViewModel = buildLibraryPickerViewModel(),
    ) {
        composeRule.setContent {
            ScreenLocals(override) {
                PdfToolsScreen(viewModel = viewModel, libraryPickerViewModel = libraryPickerViewModel)
            }
        }
    }

    @Composable
    private fun ScreenLocals(
        override: Context?,
        content: @Composable () -> Unit,
    ) {
        val base = LocalContext.current
        val localized = remember(base) { override ?: forceLocale(base, "es-ES") }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalResources provides localized.resources,
            LocalDensity provides testViewportDensity(),
            LocalActivityResultRegistryOwner provides composeRule.activity,
            LocalOnBackPressedDispatcherOwner provides composeRule.activity,
        ) { content() }
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickButton(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    // FileProvider resuelve sus rutas contra el Context REAL de la app (el que
    // usó al registrarse en el manifiesto), no contra el `context` que se le
    // pasa a getUriForFile() -- un archivo en el cacheDir aislado de la prueba
    // (`files`) nunca es "compartible" de verdad. `filesDir/pdftools/` sí está
    // declarado en file_provider_paths.xml.
    private fun realProviderFile(name: String): File {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return File(target.filesDir, "pdftools/r23_$name")
            .apply { parentFile?.mkdirs() }
    }

    private val singlePdfTools = PdfTool.entries.filterNot { it == PdfTool.NONE || it == PdfTool.MERGE }

    // ── Composición de cada herramienta ───────────────────────────────────────

    @Test
    fun cadaHerramientaComponeSuPantallaYPermiteVolverAlMenu() {
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        PdfTool.entries.filterNot { it == PdfTool.NONE }.forEach { tool ->
            composeRule.runOnUiThread { viewModel.selectTool(tool) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text(R.string.pdf_tools_back_to_tools)).assertExists()
        }

        composeRule.runOnUiThread { viewModel.reset() }
        waitForText(text(R.string.pdf_rotate))
    }

    @Test
    fun cadaHerramientaComponeSuPantallaConUnPdfElegido() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"), pages = 2)
        val other = writeAndroidPdf(files.inputFile("b.pdf"), pages = 1)
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.MERGE)
            viewModel.addPdfsToMerge(listOf(uriOf(pdf), uriOf(other)))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.pdf_tools_back_to_tools)).assertExists()

        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.COMPARE)
            viewModel.onComparePdfASelected(uriOf(pdf))
            viewModel.onComparePdfBSelected(uriOf(other))
        }
        composeRule.waitForIdle()

        singlePdfTools.filter { it != PdfTool.COMPARE }.forEach { tool ->
            composeRule.runOnUiThread {
                viewModel.selectTool(tool)
                viewModel.onPdfsSelected(listOf(uriOf(pdf)))
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text(R.string.pdf_tools_back_to_tools)).assertExists()
        }
    }

    @Test
    fun volverAHerramientasRegresaAlMenuPrincipal() {
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread { viewModel.selectTool(PdfTool.CROP) }
        waitForText(text(R.string.pdf_tools_back_to_tools))
        composeRule.onNodeWithText(text(R.string.pdf_tools_back_to_tools)).performClick()

        waitForText(text(R.string.pdf_merge))
        assertEquals(PdfTool.NONE, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun laHerramientaInicialConArchivoSeSeleccionaSola() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        val viewModel = buildViewModel()
        val libraryPickerViewModel = buildLibraryPickerViewModel()
        composeRule.setContent {
            ScreenLocals(null) {
                PdfToolsScreen(
                    initialTool = "OCR",
                    initialFileUri = uriOf(pdf).toString(),
                    viewModel = viewModel,
                    libraryPickerViewModel = libraryPickerViewModel,
                )
            }
        }

        waitForText(text(R.string.pdf_tools_back_to_tools))

        assertEquals(PdfTool.OCR, viewModel.uiState.value.selectedTool)
        assertEquals(listOf(uriOf(pdf)), viewModel.uiState.value.selectedPdfs)
    }

    // ── Resultados ────────────────────────────────────────────────────────────

    private fun rotateThatReturns(result: PdfToolResult): RotatePdfUseCase {
        val rotate = mockk<RotatePdfUseCase>()
        coEvery { rotate(any(), any(), any(), any()) } returns result
        return rotate
    }

    private fun runRotateUntilSuccess(
        viewModel: PdfToolsViewModel,
        message: String,
    ) {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.ROTATE)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_rotate_ready))
        clickButton(strings.getString(R.string.pdf_rotate_execute, 90))
        waitForText(message)
    }

    @Test
    fun unResultadoExitosoMuestraLaTarjetaConSusAcciones() {
        val output = files.inputFile("resultado.pdf").apply { writeText("contenido") }
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Success(output, "Todo listo")))
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        runRotateUntilSuccess(viewModel, "Todo listo")

        composeRule.onNodeWithText("resultado.pdf").assertExists()
        composeRule.onNodeWithText(text(R.string.pdf_tools_save_to_downloads)).assertExists()
        composeRule.onNodeWithText(text(R.string.pdf_tools_share)).assertExists()
    }

    @Test
    fun nuevaOperacionDesdeElResultadoVuelveAlMenu() {
        val output = files.inputFile("resultado.pdf").apply { writeText("contenido") }
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Success(output, "Todo listo")))
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))
        runRotateUntilSuccess(viewModel, "Todo listo")

        clickButton(text(R.string.pdf_tools_new_operation))

        waitForText(text(R.string.pdf_merge))
        assertEquals(PdfTool.NONE, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun compartirUnArchivoFueraDeLasRutasDelProveedorMuestraElErrorSinLanzarNada() {
        val output = files.inputFile("resultado.pdf").apply { writeText("contenido") }
        val recording = RecordingContext(strings)
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Success(output, "Todo listo")))
        setScreen(viewModel, override = recording)
        waitForText(text(R.string.pdf_rotate))
        runRotateUntilSuccess(viewModel, "Todo listo")

        clickButton(text(R.string.pdf_tools_share))

        // cacheDir de la prueba no está declarado en el FileProvider: se informa el error en vez de compartir.
        waitForText(text(R.string.pdf_tools_share_error))
        assertEquals(0, recording.startedIntents.size)
    }

    // Ronda 23: el camino feliz de shareSingleFile() (FileProvider.getUriForFile
    // resuelve de verdad y se llama a startActivity) no estaba cubierto -- solo
    // el catch de la ruta fuera del proveedor (prueba anterior).
    @Test
    fun compartirUnArchivoDentroDeLasRutasDelProveedorFuncionaYRegistraElIntent() {
        val output = realProviderFile("compartir_${System.nanoTime()}.pdf").apply { writeText("contenido") }
        val recording = RecordingContext(strings)
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Success(output, "Todo listo")))
        setScreen(viewModel, override = recording)
        waitForText(text(R.string.pdf_rotate))
        runRotateUntilSuccess(viewModel, "Todo listo")

        clickButton(text(R.string.pdf_tools_share))

        composeRule.onNodeWithText(text(R.string.pdf_tools_share_error)).assertDoesNotExist()
        assertEquals(1, recording.startedIntents.size)
        val inner = recording.startedIntents.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        assertEquals("application/pdf", inner?.type)

        output.delete()
    }

    @Test
    fun guardarEnDescargasUsaMediaStoreSimuladoYMarcaElResultado() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val output = files.inputFile("resultado.pdf").apply { writeText("contenido") }
        val destination = files.inputFile("descargado.pdf")
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.insert(any(), any()) } returns Uri.fromFile(destination)
        every { resolver.openOutputStream(any()) } answers { FileOutputStream(File(firstArg<Uri>().path!!)) }
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Success(output, "Todo listo")))
        setScreen(viewModel, override = ResolverContext(strings, resolver))
        waitForText(text(R.string.pdf_rotate))
        runRotateUntilSuccess(viewModel, "Todo listo")

        clickButton(text(R.string.pdf_tools_save_to_downloads))

        waitForText(text(R.string.general_saved_downloads))
        assertEquals("contenido", destination.readText())
    }

    @Test
    fun unResultadoConVariasImagenesListaCadaArchivo() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        val first = files.inputFile("img_1.jpg").apply { writeText("1") }
        val second = files.inputFile("img_2.jpg").apply { writeText("2") }
        val extract = mockk<ExtractImagesFromPdfUseCase>()
        coEvery { extract(any(), any(), any()) } returns
            PdfToolResult.MultiSuccess(listOf(first, second), "2 imágenes extraídas")
        val viewModel = buildViewModel(extractImages = extract)
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.EXTRACT_IMAGES)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_extract_images_ready))
        clickButton(text(R.string.pdf_extract_images))
        waitForText("2 imágenes extraídas")

        composeRule.onNodeWithText("img_1.jpg").assertExists()
        composeRule.onNodeWithText("img_2.jpg").assertExists()
        composeRule.onNodeWithText(text(R.string.pdf_tools_share_images)).assertExists()
    }

    // Ronda 23: shareMultipleFiles() (MultiSuccess de Extraer imágenes) no
    // tenía ninguna prueba -- ni el camino feliz ni el catch.
    @Test
    fun compartirVariasImagenesRegistraUnEnvioMultiple() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        val first = realProviderFile("img1_${System.nanoTime()}.jpg").apply { writeText("1") }
        val second = realProviderFile("img2_${System.nanoTime()}.jpg").apply { writeText("2") }
        val extract = mockk<ExtractImagesFromPdfUseCase>()
        coEvery { extract(any(), any(), any()) } returns
            PdfToolResult.MultiSuccess(listOf(first, second), "2 imágenes extraídas")
        val recording = RecordingContext(strings)
        val viewModel = buildViewModel(extractImages = extract)
        setScreen(viewModel, override = recording)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.EXTRACT_IMAGES)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_extract_images_ready))
        clickButton(text(R.string.pdf_extract_images))
        waitForText("2 imágenes extraídas")

        clickButton(text(R.string.pdf_tools_share_images))

        assertEquals(1, recording.startedIntents.size)
        val inner = recording.startedIntents.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, inner?.action)
        assertEquals("image/*", inner?.type)

        first.delete()
        second.delete()
    }

    @Test
    fun compartirVariasImagenesFueraDeLasRutasDelProveedorMuestraElError() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        val first = files.inputFile("img_1.jpg").apply { writeText("1") }
        val second = files.inputFile("img_2.jpg").apply { writeText("2") }
        val extract = mockk<ExtractImagesFromPdfUseCase>()
        coEvery { extract(any(), any(), any()) } returns
            PdfToolResult.MultiSuccess(listOf(first, second), "2 imágenes extraídas")
        val recording = RecordingContext(strings)
        val viewModel = buildViewModel(extractImages = extract)
        setScreen(viewModel, override = recording)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.EXTRACT_IMAGES)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_extract_images_ready))
        clickButton(text(R.string.pdf_extract_images))
        waitForText("2 imágenes extraídas")

        clickButton(text(R.string.pdf_tools_share_images))

        // cacheDir aislado de la prueba no está declarado en el FileProvider real.
        waitForText(text(R.string.pdf_tools_share_error))
        assertEquals(0, recording.startedIntents.size)
    }

    @Test
    fun unResultadoConErrorMuestraElMensajeEnUnaSnackbar() {
        val viewModel = buildViewModel(rotate = rotateThatReturns(PdfToolResult.Error("Fallo simulado")))
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.ROTATE)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_rotate_ready))

        clickButton(strings.getString(R.string.pdf_rotate_execute, 90))

        waitForText("Fallo simulado")
    }

    // ── Diálogo de límite diario ─────────────────────────────────────────────

    // Hallazgo: sin Activity real detrás de LocalContext (forzado a español,
    // ver ScreenLocals), `context as? Activity` de PdfToolsScreen siempre es
    // null -- `createConfigurationContext()`/ContextWrapper no son instancias
    // de Activity. El click en "Ver anuncio" ejerce igual la línea
    // `activity?.let {...}` (rama null); la rama con Activity real (llamar a
    // AdManager.showRewardedAd) ya está cubierta a nivel de ViewModel en
    // `PdfToolsViewModelTest.ver anuncio otorga un uso extra...`.
    @Test
    fun elDialogoDeLimiteDiarioApareceYCancelarLoCierraSinEjecutarNada() {
        val rotate = mockk<RotatePdfUseCase>(relaxed = true)
        val viewModel = buildViewModel(rotate = rotate, canUsePdfTool = false, isRewardedReady = true)
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        composeRule.runOnUiThread {
            viewModel.selectTool(PdfTool.ROTATE)
            viewModel.onPdfsSelected(listOf(uriOf(pdf)))
        }
        waitForText(text(R.string.pdf_rotate_ready))

        clickButton(strings.getString(R.string.pdf_rotate_execute, 90))

        waitForText(text(R.string.daily_limit_title))
        composeRule.onNodeWithText(text(R.string.daily_limit_watch_ad)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(text(R.string.general_cancel)).performClick()

        waitForText(text(R.string.pdf_rotate_ready))
        assertEquals(false, viewModel.uiState.value.showLimitDialog)
        coVerify(exactly = 0) { rotate(any(), any(), any(), any()) }
    }

    // ── Contador de usos diarios ─────────────────────────────────────────────

    @Test
    fun elContadorDeUsosDiariosSeMuestraParaUsuariosFreeConAlMenosUnUso() {
        val viewModel = buildViewModel(toolUseCount = 2, toolUseLimit = 3)
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread { viewModel.selectTool(PdfTool.ROTATE) }

        waitForText(strings.getString(R.string.pdf_tools_usage_today, 2, 3))
    }

    // El contador cambia de color (a error) al llegar al límite -- distinta
    // rama de isUsageLimitReached() ejercida por el propio Composable.
    @Test
    fun elContadorSigueVisibleAlLlegarAlLimiteDiario() {
        val viewModel = buildViewModel(toolUseCount = 3, toolUseLimit = 3)
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread { viewModel.selectTool(PdfTool.ROTATE) }

        waitForText(strings.getString(R.string.pdf_tools_usage_today, 3, 3))
    }

    // ── Selector compartido de origen de PDF (FileSourcePickerDialog) ────────

    @Test
    fun elSelectorDeOrigenDePdfEligeUnDocumentoDeLaBibliotecaYCierraElDialogo() {
        val documents =
            listOf(
                DocumentUiModel(
                    id = "content://docsmart.test/a.pdf",
                    name = "a.pdf",
                    type = DocumentType.PDF,
                    size = "1 KB",
                    date = "hoy",
                ),
                DocumentUiModel(
                    id = "content://docsmart.test/b.docx",
                    name = "b.docx",
                    type = DocumentType.WORD,
                    size = "2 KB",
                    date = "hoy",
                ),
            )
        val viewModel = buildViewModel()
        setScreen(viewModel, libraryPickerViewModel = buildLibraryPickerViewModel(documents))
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread { viewModel.selectTool(PdfTool.ROTATE) }
        waitForText(text(R.string.pdf_tools_select_pdf_prompt))
        composeRule.onNodeWithText(text(R.string.pdf_tools_select_pdf_prompt)).performClick()

        waitForText(text(R.string.filepicker_source_question))
        // El filtro de Herramientas PDF (filter = { it.type == DocumentType.PDF }) deja afuera el .docx.
        waitForText("a.pdf")
        composeRule.onNodeWithText("b.docx").assertDoesNotExist()

        composeRule.onNodeWithText("a.pdf").performClick()

        waitForText(text(R.string.pdf_rotate_ready))
        assertEquals(listOf(Uri.parse("content://docsmart.test/a.pdf")), viewModel.uiState.value.selectedPdfs)
    }

    @Test
    fun elSelectorDeOrigenDePdfSeCierraConCancelarSinCambiarElEstado() {
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.pdf_rotate))

        composeRule.runOnUiThread { viewModel.selectTool(PdfTool.CROP) }
        waitForText(text(R.string.pdf_tools_select_pdf_prompt))
        composeRule.onNodeWithText(text(R.string.pdf_tools_select_pdf_prompt)).performClick()
        waitForText(text(R.string.filepicker_source_question))

        composeRule.onNodeWithText(text(R.string.general_cancel)).performClick()

        waitForText(text(R.string.pdf_tools_select_pdf_prompt))
        assertTrue(viewModel.uiState.value.selectedPdfs.isEmpty())
    }

    // ── Herramienta inicial (acceso directo, HU-42) -- casos borde ───────────

    @Test
    fun unaHerramientaInicialInvalidaNoSeleccionaNada() {
        val pdf = writeAndroidPdf(files.inputFile("a.pdf"))
        val viewModel = buildViewModel()
        val libraryPickerViewModel = buildLibraryPickerViewModel()
        composeRule.setContent {
            ScreenLocals(null) {
                PdfToolsScreen(
                    initialTool = "INEXISTENTE",
                    initialFileUri = uriOf(pdf).toString(),
                    viewModel = viewModel,
                    libraryPickerViewModel = libraryPickerViewModel,
                )
            }
        }

        waitForText(text(R.string.pdf_rotate))
        assertEquals(PdfTool.NONE, viewModel.uiState.value.selectedTool)
    }

    @Test
    fun laHerramientaInicialSinArchivoNoSeleccionaNada() {
        val viewModel = buildViewModel()
        val libraryPickerViewModel = buildLibraryPickerViewModel()
        composeRule.setContent {
            ScreenLocals(null) {
                PdfToolsScreen(
                    initialTool = "OCR",
                    initialFileUri = null,
                    viewModel = viewModel,
                    libraryPickerViewModel = libraryPickerViewModel,
                )
            }
        }

        waitForText(text(R.string.pdf_rotate))
        assertEquals(PdfTool.NONE, viewModel.uiState.value.selectedTool)
    }

    /** Contexto con un ContentResolver propio (para simular MediaStore sin tocar Descargas reales). */
    private class ResolverContext(
        base: Context,
        private val resolver: ContentResolver,
    ) : ContextWrapper(base) {
        override fun getContentResolver(): ContentResolver = resolver
    }
}
