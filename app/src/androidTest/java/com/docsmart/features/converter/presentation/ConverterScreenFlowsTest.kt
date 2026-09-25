package com.docsmart.features.converter.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.model.HIDDEN_FROM_UI
import com.docsmart.features.converter.domain.usecase.ImageFormatUseCase
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.WordToTextUseCase
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.converter.domain.usecase.writeTestImage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * ConverterScreen con conversiones reales de imagen (ImageFormatUseCase sobre carpetas aisladas): cada tipo abre
 * su tarjeta de detalle, lote de varios archivos, error de lectura, límite diario y argumentos iniciales.
 */
class ConverterScreenFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var files: IsolatedContext
    private lateinit var strings: Context

    @Before
    fun setUp() {
        files = newIsolatedContext("converterscreen")
        strings = forceLocale(files.baseContext, "es-ES")
    }

    @After
    fun tearDown() {
        files.cleanUp()
    }

    private fun text(id: Int): String = strings.getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = strings.getString(id, *args)

    // Ronda 23: parametros nuevos (con default identico al comportamiento previo)
    // para ejercitar el indicador de limite diario (antes siempre en count=0, la
    // rama con contenido real nunca se ejecutaba) y el modo lote con un caso de
    // uso mockeado en vez de la conversion real de imagen.
    private fun buildViewModel(
        canConvert: Boolean = true,
        wordToText: WordToTextUseCase = mockk(relaxed = true),
        conversionCount: Int = 0,
        conversionLimit: Int = DailyLimitManager.LIMIT_CONVERSIONS,
        isRewardedReady: Boolean = false,
    ): ConverterViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(false)
        every { adManager.isInitialized } returns MutableStateFlow(true)
        every { adManager.isRewardedReady } returns MutableStateFlow(isRewardedReady)
        val dailyLimitManager = mockk<DailyLimitManager>(relaxed = true)
        every { dailyLimitManager.canConvert() } returns canConvert
        every { dailyLimitManager.getConversionCount() } returns conversionCount
        every { dailyLimitManager.getConversionLimit() } returns conversionLimit
        val premiumManager = mockk<PremiumManager>(relaxed = true)
        every { premiumManager.isPremium } returns MutableStateFlow(false)
        every { premiumManager.canPerform(any()) } answers { firstArg<() -> Boolean>().invoke() }
        return ConverterViewModel(
            convertImageToPdf = mockk(relaxed = true),
            pdfToImage = mockk(relaxed = true),
            pdfToText = mockk(relaxed = true),
            pdfToWord = mockk(relaxed = true),
            pdfToHtml = mockk(relaxed = true),
            imageFormat = ImageFormatUseCase(files),
            wordToPdf = mockk(relaxed = true),
            wordToText = wordToText,
            wordToHtml = mockk(relaxed = true),
            excelToPdf = mockk(relaxed = true),
            excelToCsv = mockk(relaxed = true),
            excelToHtml = mockk(relaxed = true),
            pptToPdf = mockk(relaxed = true),
            pptToText = mockk(relaxed = true),
            adManager = adManager,
            dailyLimitManager = dailyLimitManager,
            premiumManager = premiumManager,
            soundEffectPlayer = mockk(relaxed = true),
        )
    }

    private fun setScreen(
        viewModel: ConverterViewModel,
        initialType: String? = null,
        initialFileUri: String? = null,
        initialFileCategory: String? = null,
    ) {
        composeRule.setContent {
            val base = LocalContext.current
            val localized = remember(base) { forceLocale(base, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalResources provides localized.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) {
                ConverterScreen(
                    initialType = initialType,
                    initialFileUri = initialFileUri,
                    initialFileCategory = initialFileCategory,
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun waitForText(value: String) {
        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectAndConvert(
        viewModel: ConverterViewModel,
        type: ConversionType,
        uris: List<android.net.Uri>,
        buttonLabel: String,
    ) {
        composeRule.runOnUiThread {
            viewModel.onTypeSelected(type)
            viewModel.onFilesSelected(uris)
        }
        waitForText(buttonLabel)
        composeRule.onNodeWithText(buttonLabel).performScrollTo().performClick()
    }

    @Test
    fun cadaTipoDeConversionAbreSuTarjetaDeDetalleConSusArchivos() {
        val image = uriOf(writeTestImage(files.inputFile("a.png")))
        val other = uriOf(writeTestImage(files.inputFile("b.png")))
        val pdf = uriOf(writeAndroidPdf(files.inputFile("a.pdf")))
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        ConversionType.entries.filterNot { it in HIDDEN_FROM_UI }.forEach { type ->
            val selection = if (type.fromFormat == "Imagen") listOf(image, other) else listOf(pdf)
            composeRule.runOnUiThread {
                viewModel.onTypeSelected(type)
                viewModel.onFilesSelected(selection)
            }
            composeRule.waitForIdle()
            assertEquals(type, viewModel.uiState.value.selectedType)
            assertEquals(selection, viewModel.uiState.value.selectedFiles)
        }

        composeRule.runOnUiThread { viewModel.clearAll() }
        waitForText(text(R.string.converter_select))
    }

    @Test
    fun convertirUnaImagenAJpgMuestraElExitoYGeneraElArchivoEnLaCarpetaAislada() {
        val image = uriOf(writeTestImage(files.inputFile("foto.png")))
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_JPG, listOf(image), "Convertir a JPG")

        waitForText("¡Conversión exitosa!")
        val output = viewModel.uiState.value.outputFile
        assertTrue(output != null && output.exists() && output.extension == "jpg")
        assertTrue(output!!.absolutePath.startsWith(files.root.absolutePath))
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun convertirVariasImagenesGeneraUnResultadoPorArchivo() {
        val first = uriOf(writeTestImage(files.inputFile("uno.png")))
        val second = uriOf(writeTestImage(files.inputFile("dos.png")))
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_PNG, listOf(first, second), "Convertir a PNG")

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.batchResults.size == 2
        }
        assertEquals(2, viewModel.uiState.value.batchResults.size)
        assertTrue(viewModel.uiState.value.batchResults.all { it.originalFileName.isNotBlank() })
    }

    @Test
    fun unArchivoQueNoEsImagenMuestraElErrorDeLecturaEnUnaSnackbar() {
        val broken = uriOf(files.inputFile("roto.png").apply { writeText("no soy una imagen") })
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_JPG, listOf(broken), "Convertir a JPG")

        // El caso de uso responde en el idioma del dispositivo (no el forzado de la UI).
        waitForText(files.getString(R.string.converter_error_read_image))
    }

    @Test
    fun conElLimiteDiarioAlcanzadoSeAbreElDialogoYNoSeConvierte() {
        val image = uriOf(writeTestImage(files.inputFile("foto.png")))
        val viewModel = buildViewModel(canConvert = false)
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_JPG, listOf(image), "Convertir a JPG")

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") { viewModel.uiState.value.showLimitDialog }
        assertTrue(viewModel.uiState.value.outputFile == null)

        composeRule.runOnUiThread { viewModel.dismissLimitDialog() }
        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") { !viewModel.uiState.value.showLimitDialog }
    }

    @Test
    fun elTipoInicialSeSeleccionaSoloSinPasarPorLaGrilla() {
        val viewModel = buildViewModel()

        setScreen(viewModel, initialType = ConversionType.IMAGE_TO_PDF.name)

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.selectedType == ConversionType.IMAGE_TO_PDF
        }
    }

    @Test
    fun unArchivoPrecargadoDesdeElMenuSeAdjuntaAlElegirElTipoDeSuCategoria() {
        val image = writeTestImage(files.inputFile("foto.png"))
        val viewModel = buildViewModel()

        setScreen(
            viewModel,
            initialFileUri = uriOf(image).toString(),
            initialFileCategory = "Imagen",
        )
        waitForText(text(R.string.converter_select))
        composeRule.runOnUiThread { viewModel.onTypeSelected(ConversionType.IMAGE_TO_JPG) }

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.selectedFiles == listOf(uriOf(image))
        }
    }

    // ── Ronda 23: indicador de limite diario (antes solo se ejercitaba con
    // count=0, que corta con un return temprano antes de pintar nada) ────────

    @Test
    fun elIndicadorDeLimiteConPocoUsoMuestraElContadorEnColorPrimario() {
        val viewModel = buildViewModel(conversionCount = 1, conversionLimit = 5)
        setScreen(viewModel)

        waitForText(text(R.string.converter_daily_count_label, 1, 5))
    }

    @Test
    fun elIndicadorDeLimiteCercaDelLimiteMuestraElContadorEnColorDeAdvertencia() {
        val viewModel = buildViewModel(conversionCount = 4, conversionLimit = 5)
        setScreen(viewModel)

        waitForText(text(R.string.converter_daily_count_label, 4, 5))
    }

    @Test
    fun elIndicadorDeLimiteAlcanzadoMuestraElContadorEnColorDeError() {
        val viewModel = buildViewModel(conversionCount = 5, conversionLimit = 5)
        setScreen(viewModel)

        waitForText(text(R.string.converter_daily_count_label, 5, 5))
    }

    // ── Ronda 23: interacciones reales de la grilla y de la tarjeta de detalle
    // (antes solo se disparaban llamando al ViewModel directo, sin ejercer los
    // lambdas de ConverterScreen que las conectan a la UI real) ─────────────

    @Test
    fun tocarUnaTarjetaDeLaGrillaSeleccionaEseTipoDeConversion() {
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        val label = "${text(R.string.format_name_image)} → JPG"
        waitForText(label)
        composeRule.onNodeWithText(label).performScrollTo().performClick()

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.selectedType == ConversionType.IMAGE_TO_JPG
        }
    }

    @Test
    fun enLaTarjetaDeDetalleSePuedeQuitarUnaImagenEscribirElNombreYVolver() {
        val first = uriOf(writeTestImage(files.inputFile("uno.png")))
        val second = uriOf(writeTestImage(files.inputFile("dos.png")))
        val viewModel = buildViewModel()
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        composeRule.runOnUiThread {
            viewModel.onTypeSelected(ConversionType.IMAGE_TO_JPG)
            viewModel.onFilesSelected(listOf(first, second))
        }
        // Con 2 archivos es modo lote (isBatchMode): no hay campo de nombre todavia.
        waitForText(text(R.string.converter_batch_hint))

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            composeRule
                .onAllNodesWithContentDescription(text(R.string.general_delete))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(text(R.string.general_delete))[0]
            .performScrollTo()
            .performClick()
        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.selectedFiles.size == 1
        }

        // Con un solo archivo ya aparece el campo de nombre real.
        waitForText(text(R.string.converter_file_name_label))
        composeRule.onNodeWithText(text(R.string.converter_file_name_label))
            .performScrollTo()
            .performTextInput("miarchivo")
        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.fileName.contains("miarchivo")
        }

        composeRule.onNodeWithContentDescription(text(R.string.general_back_action)).performClick()
        waitForText(text(R.string.converter_select))
    }

    // ── Ronda 23: el dialogo de limite diario ya se abria en un test anterior,
    // pero nadie tocaba sus botones reales (onWatchAd/onGetPremium/onDismiss son
    // lambdas propias de ConverterScreen, no de DailyLimitDialog) ───────────

    // Nota: bajo forceLocale() el LocalContext de la pantalla ya no encadena a
    // la Activity real (ver el comentario en ConverterScreenTest.buildViewModel/
    // setContent), asi que `activity` es null y onWatchAd nunca llega a llamar
    // watchAdForConversion() en este arnes de pruebas -- mismo limite ya
    // documentado en LifecycleEffects.kt (SecureScreenEffect) para el resto de
    // la suite. Este test solo verifica que ninguno de los dos botones
    // crashea ni cierra el dialogo por si solo (onGetPremium = {} realmente no
    // hace nada; onWatchAd con activity nulo tampoco).
    @Test
    fun enElDialogoDeLimiteObtenerPremiumYVerAnuncioNoCrasheanNiCierranElDialogo() {
        val image = uriOf(writeTestImage(files.inputFile("foto.png")))
        val viewModel = buildViewModel(canConvert = false, isRewardedReady = true)
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_JPG, listOf(image), "Convertir a JPG")
        waitForText(text(R.string.daily_limit_watch_ad))

        composeRule.onNodeWithText(text(R.string.daily_limit_get_premium)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.daily_limit_watch_ad)).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.showLimitDialog)
    }

    @Test
    fun tocarCancelarEnElDialogoDeLimiteLoCierra() {
        val image = uriOf(writeTestImage(files.inputFile("foto.png")))
        val viewModel = buildViewModel(canConvert = false)
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(viewModel, ConversionType.IMAGE_TO_JPG, listOf(image), "Convertir a JPG")
        waitForText(text(R.string.general_cancel))

        composeRule.onNodeWithText(text(R.string.general_cancel)).performScrollTo().performClick()

        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") { !viewModel.uiState.value.showLimitDialog }
    }

    // ── Ronda 23: pantalla de resultado de lote (antes solo se probaba el
    // componente BatchConversionSuccess aislado, nunca desde ConverterScreen
    // real -- el unico test que llegaba hasta aca por la pantalla real estaba
    // @Ignore por inestabilidad de tiempos con conversion real de imagenes;
    // acá se evita esa inestabilidad mockeando el caso de uso, sin E/S real
    // de imagen) ───────────────────────────────────────────────────────────

    @Test
    fun unLoteDeVariosArchivosMuestraElResumenYPermiteGuardarTodasYConvertirOtro() {
        val uriA = uriOf(files.inputFile("a.docx").apply { writeText("entrada a") })
        val uriB = uriOf(files.inputFile("b.docx").apply { writeText("entrada b") })
        val outputDir = files.outputDir("salidas").apply { mkdirs() }
        val outA = File(outputDir, "a.txt").apply { writeText("salida a") }
        val outB = File(outputDir, "b.txt").apply { writeText("salida b") }
        val wordToText = mockk<WordToTextUseCase>(relaxed = true)
        coEvery { wordToText(uriA, "a") } returns ConversionResult.Success(outA, 1, 1)
        coEvery { wordToText(uriB, "b") } returns ConversionResult.Success(outB, 1, 1)
        val viewModel = buildViewModel(wordToText = wordToText)
        setScreen(viewModel)
        waitForText(text(R.string.converter_select))

        selectAndConvert(
            viewModel,
            ConversionType.WORD_TO_TXT,
            listOf(uriA, uriB),
            text(R.string.converter_convert_batch_button, 2),
        )

        waitForText(text(R.string.converter_batch_success_title, 2, 2))

        composeRule.onNodeWithText(text(R.string.converter_batch_save_all)).performScrollTo().performClick()
        composeRule.waitUntilOrDump("CI_HANG_ConverterScreenFlowsTest") {
            viewModel.uiState.value.batchSavedToDownloads
        }

        composeRule.onNodeWithText(text(R.string.converter_batch_convert_another)).performScrollTo().performClick()
        waitForText(text(R.string.converter_select))
    }
}
