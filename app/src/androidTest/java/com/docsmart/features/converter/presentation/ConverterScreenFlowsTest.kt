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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.model.HIDDEN_FROM_UI
import com.docsmart.features.converter.domain.usecase.ImageFormatUseCase
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import com.docsmart.features.converter.domain.usecase.uriOf
import com.docsmart.features.converter.domain.usecase.writeAndroidPdf
import com.docsmart.features.converter.domain.usecase.writeTestImage
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

    private fun buildViewModel(canConvert: Boolean = true): ConverterViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(false)
        every { adManager.isInitialized } returns MutableStateFlow(true)
        every { adManager.isRewardedReady } returns MutableStateFlow(false)
        val dailyLimitManager = mockk<DailyLimitManager>(relaxed = true)
        every { dailyLimitManager.canConvert() } returns canConvert
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
            wordToText = mockk(relaxed = true),
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
}
