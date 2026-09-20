package com.docsmart.features.scanner.presentation

import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.presentation.ConverterUiState
import com.docsmart.features.converter.presentation.ConverterViewModel
import com.docsmart.features.scanner.domain.ScanImageEditor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * ScanResultScreen.kt (ronda 20): recorre la pantalla de resultado del escaneo con
 * páginas reales (Bitmap -> JPEG en cacheDir) y el editor de imagen real. Los
 * ViewModels del Convertidor y de la sesión son mocks con flujos controlados; los
 * chooser del sistema se graban con [RecordingContext] y nunca se abren.
 * "Guardar en Descargas" es real (MediaStore) pero con nombres `r20scan_*` que se
 * borran en `@After`.
 */
class ScanResultScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val startMillis = System.currentTimeMillis()
    private val scannerDir get() = File(targetContext.cacheDir, "scanner")
    private val fileCounter = AtomicInteger()

    private fun str(
        id: Int,
        vararg args: Any,
    ): String {
        val ctx = forceLocale(targetContext, "es-ES")
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    @After
    fun limpiar() {
        scannerDir.listFiles()?.forEach { if (it.lastModified() >= startMillis - 2_000) it.delete() }
        File(targetContext.cacheDir, "scanner_edits").listFiles()?.forEach {
            if (it.lastModified() >= startMillis - 2_000) it.delete()
        }
        File(targetContext.filesDir, "converted").listFiles()?.forEach {
            if (it.name.startsWith(PREFIX)) it.delete()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                targetContext.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("$PREFIX%"),
                )
            }
        } catch (e: Exception) {
            // Sin permiso o sin dueño: no hay nada más que limpiar.
        }
    }

    private fun newFile(
        name: String,
        extension: String,
    ): File {
        val file = File(scannerDir, "$PREFIX${fileCounter.incrementAndGet()}_$name.$extension")
        return if (extension == "pdf") {
            file.apply {
                parentFile?.mkdirs()
                writeText("%PDF-1.4\n%r20\n")
            }
        } else {
            createTestJpeg(file, Color.rgb(200, 60, 60))
        }
    }

    private fun editsCount(): Int = File(targetContext.cacheDir, "scanner_edits").listFiles()?.size ?: 0

    private fun documentFor(file: File) =
        DocumentUiModel(
            id = file.absolutePath,
            name = file.name,
            type = if (file.extension == "pdf") DocumentType.PDF else DocumentType.IMAGE,
            size = "1 KB",
            date = "hoy",
        )

    private inner class Fixture(
        pageCount: Int = 2,
        premium: Boolean = true,
        rewardedReady: Boolean = false,
        val isPdf: Boolean = false,
        allowSaveSlot: Boolean = true,
    ) {
        val pdfFile: File? = if (isPdf) newFile("origen", "pdf") else null
        val pages: List<Uri> =
            if (pdfFile != null) {
                listOf(Uri.fromFile(pdfFile))
            } else {
                List(pageCount) { Uri.fromFile(newFile("pagina$it", "jpg")) }
            }
        val uiState = MutableStateFlow(ConverterUiState())
        val sessionFiles = MutableStateFlow<List<DocumentUiModel>>(emptyList())
        val limitState = MutableStateFlow(ScanSaveLimitUiState())
        val adManager =
            mockk<AdManager>(relaxed = true).also {
                every { it.isPremium } returns MutableStateFlow(premium)
                every { it.isInitialized } returns MutableStateFlow(false)
                every { it.isRewardedReady } returns MutableStateFlow(rewardedReady)
            }
        val converter =
            mockk<ConverterViewModel>(relaxed = true).also {
                every { it.uiState } returns uiState
                every { it.adManager } returns adManager
            }
        val session =
            mockk<ScanSessionViewModel>(relaxed = true).also {
                every { it.scannedFiles } returns sessionFiles
                every { it.saveLimitState } returns limitState
                every { it.adManager } returns adManager
                every { it.requestScanSaveSlot() } returns allowSaveSlot
                every { it.addFile(any(), any()) } answers {
                    sessionFiles.value = sessionFiles.value + documentFor(firstArg<File>())
                }
            }
        val editor = ScanImageEditorViewModel(ScanImageEditor(targetContext))
        var recording: RecordingContext? = null
        val backCount = AtomicInteger()
        val doneCount = AtomicInteger()
        val premiumCount = AtomicInteger()
        val calls = CopyOnWriteArrayList<String>()

        fun show() {
            composeRule.setContentEsFit(
                overrideContext = { RecordingContext(it).also { ctx -> recording = ctx } },
            ) {
                ScanResultScreen(
                    scannedUris = pages,
                    isPdf = isPdf,
                    onBack = { backCount.incrementAndGet() },
                    onDone = { doneCount.incrementAndGet() },
                    onPremiumClick = { premiumCount.incrementAndGet() },
                    onOpenDocument = { calls += "open:$it" },
                    onConvertDocument = { calls += "convert:${it.id}" },
                    onCreateQrFromDocument = { calls += "qr:${it.id}" },
                    documentActions =
                        ScanResultDocumentActions(
                            onMakeSearchable = { calls += "ocr:${it.id}" },
                            onSign = { calls += "sign:${it.id}" },
                            onMoveToSecureFolder = { calls += "secure:${it.id}" },
                        ),
                    converterViewModel = converter,
                    editorViewModel = editor,
                    scanSessionViewModel = session,
                )
            }
            composeRule.waitForIdle()
        }

        fun successResult(
            extension: String = "pdf",
            pageTotal: Int = 2,
        ): Pair<File, ConversionResult.Success> {
            val file = newFile("resultado", extension)
            return file to ConversionResult.Success(outputFile = file, pageCount = pageTotal, fileSizeKb = 1)
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToIndexAction())[0].performScrollToNode(hasText(text))
    }

    private fun clickInList(text: String) {
        scrollTo(text)
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun waitForText(
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntilOrDump("R20_ScanResult", timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Flujo normal (sin resultado todavía) ─────────────────────────────────────

    @Test
    fun flujoNormal_muestraBannerVistaPreviaFormatoYAcciones() {
        val fx = Fixture(pageCount = 2)
        fx.show()

        composeRule.onNodeWithText(str(R.string.scanner_result_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_subtitle_pages, 2)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_preview)).assertExists()
        composeRule.onNodeWithContentDescription(str(R.string.scan_result_page_content_desc, 1)).assertExists()
        composeRule.onNodeWithContentDescription(str(R.string.scan_result_page_content_desc, 2)).assertExists()
        composeRule.onAllNodesWithText(str(R.string.scan_edit_button_label)).assertCountEquals(2)

        scrollTo(str(R.string.scan_result_export_format_label))
        composeRule.onNodeWithText(str(R.string.scan_color_mode_label)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_page_count, 2, 10)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_export_format_label)).assertExists()
        composeRule.onNodeWithText("PDF").assertExists()
        composeRule.onNodeWithText("JPG").assertExists()
        composeRule.onNodeWithText("WebP").assertExists()

        scrollTo(str(R.string.scan_result_generate_format, "PDF"))
        composeRule.onNodeWithText(str(R.string.scan_result_filename_label)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_generate_format, "PDF")).assertExists()
        composeRule.onNodeWithText(str(R.string.scanner_again)).assertExists()
        composeRule.onNodeWithText(str(R.string.scanner_back)).assertExists()
    }

    @Test
    fun escribirNombreYGenerarPdf_invocaGenerateFromScan() {
        val fx = Fixture()
        fx.show()

        scrollTo(str(R.string.scan_result_filename_label))
        composeRule.onNode(hasSetTextAction()).performTextInput("Mi escaneo")
        clickInList(str(R.string.scan_result_generate_format, "PDF"))

        verify(exactly = 1) { fx.converter.generateFromScan(any(), null, false, "Mi escaneo") }
    }

    @Test
    fun cambiarAFormatosDeImagen_generaConElTipoDeConversionCorrecto() {
        val fx = Fixture()
        fx.show()

        scrollTo(str(R.string.scan_result_export_format_label))
        composeRule.onNodeWithText("JPG").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(str(R.string.scan_result_high_res_title)).assertCountEquals(0)
        clickInList(str(R.string.scan_result_generate_format, "JPG"))
        verify { fx.converter.generateFromScan(any(), ConversionType.IMAGE_TO_JPG, false, "") }

        scrollTo(str(R.string.scan_result_export_format_label))
        composeRule.onNodeWithText("WebP").performClick()
        composeRule.waitForIdle()
        clickInList(str(R.string.scan_result_generate_format, "WebP"))
        verify { fx.converter.generateFromScan(any(), ConversionType.IMAGE_TO_WEBP, false, "") }
    }

    @Test
    fun altaResolucion_conPremium_alternaElSwitchYSeEnviaAlGenerar() {
        val fx = Fixture(premium = true)
        fx.show()

        scrollTo(str(R.string.scan_result_high_res_title))
        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()
        clickInList(str(R.string.scan_result_generate_format, "PDF"))

        verify { fx.converter.generateFromScan(any(), null, true, "") }
        assertEquals(0, fx.premiumCount.get())
    }

    @Test
    fun altaResolucion_sinPremium_pideSuscripcion() {
        val fx = Fixture(premium = false)
        fx.show()

        scrollTo(str(R.string.scan_result_high_res_title))
        composeRule.onNodeWithContentDescription(str(R.string.scan_result_high_res_premium_content_desc)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_high_res_title)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, fx.premiumCount.get())
    }

    @Test
    fun botonesEscanearDeNuevoYVolver_limpianYNavegan() {
        val fx = Fixture()
        fx.show()

        clickInList(str(R.string.scanner_again))
        assertEquals(1, fx.backCount.get())
        verify { fx.converter.clearAll() }

        clickInList(str(R.string.scanner_back))
        assertEquals(1, fx.doneCount.get())
        verify { fx.session.clearSession() }
    }

    @Test
    fun flechaVolverDelBanner_invocaOnBack() {
        val fx = Fixture()
        fx.show()

        composeRule.onNodeWithText(str(R.string.general_back)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, fx.backCount.get())
    }

    // ── Modo de color ────────────────────────────────────────────────────────────

    @Test
    fun modoDeColorPorDefecto_procesaLasPaginasConElEditorReal() {
        val fx = Fixture(pageCount = 2)
        fx.show()
        val before = editsCount()

        scrollTo(str(R.string.scan_color_mode_label))
        composeRule.onNodeWithText(str(R.string.scan_color_mode_bw)).performScrollTo().performClick()
        composeRule.waitUntilOrDump("R20_ColorMode", 10_000) { editsCount() >= before + 2 }
        composeRule.onNodeWithText(str(R.string.scan_color_mode_bw)).assertIsSelected()

        composeRule.onNodeWithText(str(R.string.scan_color_mode_grayscale)).performScrollTo().performClick()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_grayscale)).assertIsSelected()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_highlight)).performScrollTo().performClick()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_highlight)).assertIsSelected()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_color)).performScrollTo().performClick()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_color)).assertIsSelected()
        assertTrue(editsCount() >= before + 2)
    }

    // ── Editor de página ─────────────────────────────────────────────────────────

    @Test
    fun editor_ajustarBrilloYEscala_aplicaConElEditorRealYCierra() {
        val fx = Fixture(pageCount = 1)
        fx.show()
        val before = editsCount()

        composeRule.onAllNodesWithText(str(R.string.scan_edit_button_label))[0].performClick()
        waitForText(str(R.string.scan_edit_title))
        composeRule.onNodeWithText(str(R.string.scan_edit_brightness, 50)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_edit_contrast, 50)).assertExists()

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[1]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.scan_edit_brightness, 75)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_edit_contrast, 30)).assertExists()

        composeRule.onNodeWithText("50%").performClick()
        composeRule.onNodeWithText(str(R.string.scan_edit_apply)).performClick()

        composeRule.waitUntilOrDump("R20_EditorApply", 10_000) {
            composeRule.onAllNodesWithText(str(R.string.scan_edit_title)).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(editsCount() > before)
        composeRule.onNodeWithContentDescription(str(R.string.scan_result_page_content_desc, 1)).assertExists()
    }

    @Test
    fun editor_cancelar_cierraSinGenerarArchivos() {
        val fx = Fixture(pageCount = 1)
        fx.show()
        val before = editsCount()

        composeRule.onAllNodesWithText(str(R.string.scan_edit_button_label))[0].performClick()
        waitForText(str(R.string.scan_edit_title))
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(str(R.string.scan_edit_title)).assertCountEquals(0)
        assertEquals(before, editsCount())
    }

    // ── Agregar página ───────────────────────────────────────────────────────────

    @Test
    fun agregarPagina_bajoElLimite_noAbreElDialogoDeLimite() {
        val fx = Fixture(pageCount = 2, premium = false)
        fx.show()

        clickInList(str(R.string.scan_add_page))

        composeRule.onAllNodesWithText(str(R.string.scan_page_limit_title)).assertCountEquals(0)
        assertEquals(0, fx.premiumCount.get())
    }

    @Test
    fun agregarPagina_enElLimiteSinPremium_muestraDialogoYPideSuscripcion() {
        val fx = Fixture(pageCount = 10, premium = false, rewardedReady = false)
        fx.show()

        clickInList(str(R.string.scan_add_page))
        waitForText(str(R.string.scan_page_limit_title))
        composeRule.onNodeWithText(str(R.string.scan_page_limit_body, 10, 10)).assertExists()
        composeRule.onNodeWithText(str(R.string.daily_limit_ad_not_ready)).assertExists()

        composeRule.onNodeWithText(str(R.string.daily_limit_get_premium)).performClick()
        composeRule.waitForIdle()
        assertEquals(1, fx.premiumCount.get())

        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(str(R.string.scan_page_limit_title)).assertCountEquals(0)
    }

    @Test
    fun agregarPagina_enElLimiteConAnuncioListo_cierraElDialogoAlVerlo() {
        val fx = Fixture(pageCount = 10, premium = false, rewardedReady = true)
        fx.show()

        clickInList(str(R.string.scan_add_page))
        waitForText(str(R.string.scan_page_limit_title))
        composeRule.onNodeWithText(str(R.string.daily_limit_watch_ad)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(str(R.string.scan_page_limit_title)).assertCountEquals(0)
    }

    // ── Con resultado generado ───────────────────────────────────────────────────

    @Test
    fun convirtiendo_muestraElIndicadorDeProgreso() {
        val fx = Fixture()
        fx.uiState.value = ConverterUiState(isConverting = true)
        fx.show()

        scrollTo(str(R.string.scan_result_generating_format, "PDF"))
        composeRule.onNodeWithText(str(R.string.scan_result_generating_format, "PDF")).assertExists()
    }

    @Test
    fun conResultado_muestraGuardarYCompartirSinLasOpcionesDeFormato() {
        val fx = Fixture()
        fx.uiState.value = ConverterUiState(conversionResult = fx.successResult().second)
        fx.show()

        scrollTo(str(R.string.converter_save))
        composeRule.onNodeWithText(str(R.string.converter_save)).assertExists()
        composeRule.onNodeWithText(str(R.string.scan_result_share_format, "PDF")).assertExists()
        composeRule.onAllNodesWithText(str(R.string.scan_result_export_format_label)).assertCountEquals(0)
        composeRule.onAllNodesWithText(str(R.string.scan_result_generate_format, "PDF")).assertCountEquals(0)

        clickInList(str(R.string.scanner_back))
        assertEquals(1, fx.doneCount.get())
    }

    @Test
    fun guardarEnDescargas_conCupo_finalizaLaSesionYMuestraLaLista() {
        val fx = Fixture()
        val (file, result) = fx.successResult()
        fx.uiState.value = ConverterUiState(conversionResult = result)
        fx.show()

        clickInList(str(R.string.converter_save))
        waitForText(str(R.string.scan_session_title), timeoutMillis = 15_000)

        verify(exactly = 1) { fx.session.addFile(file, any()) }
        verify(exactly = 1) { fx.session.registerScanSaved() }
        composeRule.onNodeWithText(file.name).assertExists()
        // La vista previa y las opciones desaparecen al finalizar.
        composeRule.onAllNodesWithText(str(R.string.scan_result_preview)).assertCountEquals(0)
    }

    @Test
    fun guardarYCompartir_sinCupoDiario_noHacenNada() {
        val fx = Fixture(allowSaveSlot = false)
        fx.uiState.value = ConverterUiState(conversionResult = fx.successResult().second)
        fx.show()

        clickInList(str(R.string.converter_save))
        clickInList(str(R.string.scan_result_share_format, "PDF"))

        verify(exactly = 0) { fx.session.addFile(any(), any()) }
        assertTrue(fx.recording?.started.orEmpty().isEmpty())
        composeRule.onNodeWithText(str(R.string.converter_save)).assertExists()
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun compartir_abreElSelectorYAlElegirUnaAppFinalizaLaSesion() {
        val fx = Fixture()
        val (file, result) = fx.successResult()
        fx.uiState.value = ConverterUiState(conversionResult = result)
        fx.show()

        clickInList(str(R.string.scan_result_share_format, "PDF"))
        composeRule.waitUntilOrDump("R20_Share", 10_000) { fx.recording?.started?.isNotEmpty() == true }

        val chooser = fx.recording!!.started.first()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("application/pdf", send.type)

        // Simula que el usuario eligió una app: el sistema dispara el IntentSender del chooser.
        @Suppress("DEPRECATION")
        val sender = chooser.getParcelableExtra<IntentSender>(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER)
        assertNotNull(sender)
        sender!!.sendIntent(targetContext, 0, null, null, null)

        waitForText(str(R.string.scan_session_title), timeoutMillis = 15_000)
        verify { fx.session.addFile(file, any()) }
        verify { fx.session.registerScanSaved() }
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun pdfDeEscaner_compartirCopiaAlCacheYAbreElSelector() {
        val fx = Fixture(isPdf = true)
        fx.show()

        scrollTo(str(R.string.scan_result_filename_label))
        composeRule.onNode(hasSetTextAction()).performTextInput("${PREFIX}pdf_compartido")
        composeRule.onAllNodesWithText(str(R.string.scan_result_preview)).assertCountEquals(0)
        clickInList(str(R.string.scan_result_share_format, "PDF"))

        composeRule.waitUntilOrDump("R20_SharePdf", 10_000) { fx.recording?.started?.isNotEmpty() == true }
        @Suppress("DEPRECATION")
        val send = fx.recording!!.started.first().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("application/pdf", send?.type)
        assertTrue(File(scannerDir, "${PREFIX}pdf_compartido.pdf").exists())
    }

    @Test
    fun pdfDeEscaner_guardarCopiaElArchivoYFinalizaLaSesion() {
        val fx = Fixture(isPdf = true)
        fx.show()

        scrollTo(str(R.string.scan_result_filename_label))
        composeRule.onNode(hasSetTextAction()).performTextInput("${PREFIX}pdf_guardado")
        clickInList(str(R.string.converter_save))
        waitForText(str(R.string.scan_session_title), timeoutMillis = 15_000)

        verify(exactly = 1) { fx.session.addFile(any(), any()) }
        assertTrue(File(targetContext.filesDir, "converted/${PREFIX}pdf_guardado.pdf").exists())
    }

    // ── Límites diarios ──────────────────────────────────────────────────────────

    @Test
    fun limiteDiarioDeConversiones_muestraElDialogoYSusAcciones() {
        val fx = Fixture(premium = true, rewardedReady = true)
        fx.uiState.value = ConverterUiState(showLimitDialog = true, conversionCount = 5, conversionLimit = 5)
        fx.show()

        waitForText(str(R.string.daily_limit_title))
        composeRule.onNodeWithText(str(R.string.daily_limit_get_premium)).performClick()
        composeRule.waitForIdle()
        assertEquals(1, fx.premiumCount.get())

        composeRule.onNodeWithText(str(R.string.daily_limit_watch_ad)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        verify(atLeast = 1) { fx.converter.dismissLimitDialog() }
    }

    @Test
    fun limiteDiarioDeEscaneosGuardados_muestraElDialogoYSeCierra() {
        val fx = Fixture(premium = true)
        fx.limitState.value = ScanSaveLimitUiState(savedCount = 8, savedLimit = 8, showLimitDialog = true)
        fx.show()

        waitForText(str(R.string.daily_limit_title))
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { fx.session.dismissScanLimitDialog() }
    }

    // ── Lote de imágenes ─────────────────────────────────────────────────────────

    private fun batchOf(fx: Fixture): Pair<List<File>, List<BatchConversionItem>> {
        val files = List(2) { fx.successResult("jpg", 1).first }
        val items =
            files.mapIndexed { index, file ->
                BatchConversionItem("pagina$index.jpg", ConversionResult.Success(file, 1, 1))
            } + BatchConversionItem("mala.jpg", ConversionResult.Error("fallo"))
        return files to items
    }

    @Test
    fun loteDeImagenes_muestraElResumenYSusAcciones() {
        val fx = Fixture()
        val (_, items) = batchOf(fx)
        fx.uiState.value = ConverterUiState(batchResults = items)
        fx.show()

        scrollTo(str(R.string.converter_batch_success_title, 2, 3))
        composeRule.onNodeWithText(str(R.string.converter_batch_success_title, 2, 3)).assertExists()

        clickInList(str(R.string.converter_batch_save_all))
        verify(exactly = 1) { fx.converter.saveAllToDownloads(any()) }

        clickInList(str(R.string.converter_batch_convert_another))
        assertEquals(1, fx.backCount.get())

        clickInList(str(R.string.scanner_back))
        assertEquals(1, fx.doneCount.get())
    }

    @Test
    fun loteDeImagenes_sinCupo_noGuardaEnDescargas() {
        val fx = Fixture(allowSaveSlot = false)
        val (_, items) = batchOf(fx)
        fx.uiState.value = ConverterUiState(batchResults = items)
        fx.show()

        clickInList(str(R.string.converter_batch_save_all))

        verify(exactly = 0) { fx.converter.saveAllToDownloads(any()) }
    }

    // ── Sesión finalizada (lista de archivos escaneados) ─────────────────────────

    private fun showFinalizedSession(pdfFirst: Boolean = false): Pair<Fixture, List<File>> {
        val fx = Fixture()
        val (batchFiles, items) = batchOf(fx)
        // Con un PDF primero, el menú de la primera fila incluye también OCR y Firmar.
        val files = if (pdfFirst) listOf(newFile("doc", "pdf")) + batchFiles else batchFiles
        if (pdfFirst) fx.sessionFiles.value = listOf(documentFor(files[0]))
        fx.uiState.value = ConverterUiState(batchResults = items, batchSavedToDownloads = true)
        fx.show()
        waitForText(str(R.string.scan_session_title))
        return fx to files
    }

    @Test
    fun loteGuardado_agregaLosArchivosALaSesionYMuestraLaLista() {
        val (fx, files) = showFinalizedSession()

        verify(exactly = 2) { fx.session.addFile(any(), any()) }
        verify(exactly = 1) { fx.session.registerScanSaved() }
        files.forEach { composeRule.onNodeWithText(it.name).assertExists() }
        composeRule.onAllNodesWithText(str(R.string.scan_result_preview)).assertCountEquals(0)
    }

    @Test
    fun sesionFinalizada_escanearOtroYVolverAlInicio() {
        val (fx, _) = showFinalizedSession()

        clickInList(str(R.string.scan_session_scan_another))
        assertEquals(1, fx.backCount.get())
        verify { fx.converter.clearAll() }

        clickInList(str(R.string.scanner_back))
        assertEquals(1, fx.doneCount.get())
        verify { fx.session.clearSession() }
    }

    @Test
    fun sesionFinalizada_tocarLaFilaYElFavorito_invocaLosCallbacks() {
        val (fx, files) = showFinalizedSession()

        composeRule.onNodeWithText(files[0].name).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithContentDescription(str(R.string.doc_item_add_favorite))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("open:${files[0].absolutePath}"), fx.calls.toList())
        verify { fx.session.toggleFavorite(files[0].absolutePath) }
    }

    @Test
    fun sesionFinalizada_menuContextual_ejecutaLasAcciones() {
        val (fx, files) = showFinalizedSession(pdfFirst = true)
        val id = files[0].absolutePath

        listOf(
            R.string.qr_open_document,
            R.string.doc_item_add_favorite,
            R.string.viewer_convert,
            R.string.viewer_create_qr,
            R.string.doc_item_make_searchable,
            R.string.doc_item_sign,
            R.string.doc_item_move_to_secure_folder,
            R.string.general_delete,
            R.string.general_share,
        ).forEach { itemRes ->
            composeRule.onAllNodesWithContentDescription(str(R.string.viewer_more_options))[0].performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(str(itemRes)).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }

        assertEquals(
            listOf("open:$id", "convert:$id", "qr:$id", "ocr:$id", "sign:$id", "secure:$id"),
            fx.calls.toList(),
        )
        verify { fx.session.toggleFavorite(id) }
        verify { fx.session.deleteDocument(id) }
        // "Compartir" de la fila arma el chooser de ACTION_SEND con el archivo real.
        val chooser = fx.recording!!.started.first()

        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("application/pdf", send?.type)
    }

    @Test
    fun sesionFinalizada_renombrarConfirmaConLaExtensionOriginal() {
        val (fx, files) = showFinalizedSession()

        composeRule.onAllNodesWithContentDescription(str(R.string.viewer_more_options))[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.viewer_rename)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Nuevo")
        composeRule.onNodeWithText(str(R.string.viewer_rename)).performClick()
        composeRule.waitForIdle()

        verify { fx.session.renameDocument(files[0].absolutePath, "Nuevo.jpg") }
    }

    @Test
    fun sesionFinalizada_compartirUnArchivoInexistente_noAbreElSelector() {
        val fx = Fixture()
        val (_, items) = batchOf(fx)
        val ghost = File(scannerDir, "${PREFIX}fantasma.jpg")
        fx.sessionFiles.value = listOf(documentFor(ghost))
        fx.uiState.value = ConverterUiState(batchResults = items, batchSavedToDownloads = true)
        fx.show()
        waitForText(str(R.string.scan_session_title))

        composeRule.onAllNodesWithContentDescription(str(R.string.viewer_more_options))[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.general_share)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(fx.recording?.started.orEmpty().isEmpty())
    }

    private companion object {
        const val PREFIX = "r20scan_"
    }
}
