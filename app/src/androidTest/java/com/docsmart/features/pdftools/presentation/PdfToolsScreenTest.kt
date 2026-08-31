package com.docsmart.features.pdftools.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.pdftools.domain.usecase.RotatePdfUseCase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Flujo de prioridad Alta #10 de RF-QA-01 (ver compose-ui-testing.md):
 * Herramientas PDF -- elegir herramienta, ejecutar sobre un PDF real, ver
 * `ToolSuccessCard`.
 *
 * `PdfToolsViewModel` tiene 14 use cases de herramientas distintas -- para
 * el camino principal se ejercita solo "Rotar PDF" (la más simple: un
 * único PDF de entrada, un parámetro numérico, sin UI de recorte/firma/
 * campos de formulario) con el `RotatePdfUseCase` REAL (mismo criterio ya
 * usado en `ConverterScreenTest`/`ViewerSearchTest`: usar la clase real
 * cuando es barato, para dar protección de regresión genuina). Las otras
 * 13 herramientas quedan mockeadas relajadas -- nunca se invocan en este
 * test, ya que solo se selecciona/ejecuta Rotar.
 *
 * El selector de archivos del sistema (`singlePdfLauncher`) es un proceso
 * externo que Compose UI Testing no puede conducir -- se simula llamando a
 * `onPdfsSelected()` directo, mismo principio que `ConverterScreenTest`.
 */
class PdfToolsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): PdfToolsViewModel {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(false)
        every { adManager.isInitialized } returns MutableStateFlow(true)
        every { adManager.isRewardedReady } returns MutableStateFlow(false)

        // canUsePdfTool() en un mock relajado devuelve false por defecto --
        // sin esto, execute() muestra el diálogo de límite diario en vez de
        // ejecutar de verdad (mismo hallazgo ya documentado en
        // ConverterScreenTest para canConvert()).
        val dailyLimitManager = mockk<DailyLimitManager>(relaxed = true)
        every { dailyLimitManager.canUsePdfTool(any()) } returns true

        return PdfToolsViewModel(
            mergePdf         = mockk(relaxed = true),
            splitPdf         = mockk(relaxed = true),
            compressPdf      = mockk(relaxed = true),
            rotatePdf        = RotatePdfUseCase(appContext), // real, no mock
            numberPagesPdf   = mockk(relaxed = true),
            watermarkPdf     = mockk(relaxed = true),
            reorderPagesPdf  = mockk(relaxed = true),
            comparePdf       = mockk(relaxed = true),
            redactPdf        = mockk(relaxed = true),
            cropPdf          = mockk(relaxed = true),
            editTextPdf      = mockk(relaxed = true),
            signPdf          = mockk(relaxed = true),
            detectFormFields = mockk(relaxed = true),
            fillForm         = mockk(relaxed = true),
            ocrPdf           = mockk(relaxed = true),
            dailyLimitManager = dailyLimitManager,
            adManager         = adManager
        )
    }

    // PDF real de 1 página -- mismo patrón de creación de archivo real que
    // ConverterScreenTest.createTestImageFile()/ViewerSearchTest, vía
    // iText7 (ya usado en producción, ver StudyNotesExporter.kt).
    private fun createTestPdf(): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(appContext.cacheDir, "pdftools_test_${System.currentTimeMillis()}.pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        document.add(Paragraph("Documento de prueba para Herramientas PDF."))
        document.close()
        return file
    }

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Rotar PDF"/"PDF rotado..." vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // PdfToolsScreen usa rememberLauncherForActivityResult() para
            // los pickers de PDF -- mismo motivo que
            // ConverterScreenTest/SecurityScreenTest para re-proveer estos
            // dos apuntando a la Activity real.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun elegirRotarPdf_ejecutarSobreUnPdfReal_muestraResultadoExitoso() {
        val viewModel = buildViewModel()
        val pdfFile   = createTestPdf()

        setContentWithLocale { PdfToolsScreen(viewModel = viewModel) }
        waitForText("Rotar PDF")

        composeRule.onNodeWithText("Rotar PDF").performClick()
        waitForText("Seleccionar PDF")

        composeRule.runOnUiThread {
            viewModel.onPdfsSelected(listOf(Uri.fromFile(pdfFile)))
        }
        waitForText("PDF listo para rotar")

        // Ángulo por defecto: 90° (RotatePdfUiState.rotationDegrees) -- el
        // botón de ejecutar ya incluye el ángulo en su texto. El botón queda
        // fuera del viewport visible de la LazyColumn (la Card de vista
        // previa + controles empuja el resto del contenido más allá del
        // alto de pantalla) -- performClick() dispara un toque sintético en
        // las coordenadas reales del nodo, que caen fuera de lo visible si
        // no se hace scroll primero (mismo tipo de hallazgo ya documentado
        // en SettingsScreenTest para "Restablecer configuración", aquí con
        // el agravante de que el nodo sí existe en el árbol de semántica --
        // no lanza ninguna excepción, simplemente el toque no llega a nada).
        composeRule.onNodeWithText("Rotar PDF 90°").performScrollTo().performClick()

        // execute() corre en una corrutina real (RotatePdfUseCase real, no
        // mockeado, con E/S real de archivo) -- se espera explícitamente el
        // resultado en vez de asumir que waitForIdle() alcanza.
        waitForText("PDF rotado 90° correctamente")

        composeRule.onNodeWithText("PDF rotado 90° correctamente").assertIsDisplayed()
    }
}
