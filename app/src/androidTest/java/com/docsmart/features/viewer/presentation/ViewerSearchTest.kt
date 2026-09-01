package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Flujo de prioridad Alta #4 de RF-QA-01 (ver compose-ui-testing.md): Visor
 * -- búsqueda (RF-VIS-08). Escribir término, ver coincidencias resaltadas
 * por página y navegar entre ellas.
 *
 * A diferencia de `ViewerScreenTest` (que usa `documentId = "1"`, resuelto
 * por `getMockDocument()` sin `fileUri` real), acá se necesita un `fileUri`
 * real para que `ViewerViewModel.searchInPdf()` no corte temprano -- se usa
 * una ruta de archivo absoluta a un PDF real de 2 páginas generado con
 * iText7 (mismo patrón de `StudyNotesExporter`), con el término de búsqueda
 * en ambas páginas. `SearchPdfTextUseCase` NO se mockea -- es la misma
 * clase real usada en producción, mismo criterio ya aplicado en
 * `ConverterScreenTest` para `ImageFormatUseCase` (usar la real cuando es
 * barato y da protección de regresión genuina).
 */
class ViewerSearchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): ViewerViewModel {
        val favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
        every { favoritesRepository.isFavorite(any()) } returns false
        every { favoritesRepository.getAlias(any()) } returns null

        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        return ViewerViewModel(
            favoritesRepository = favoritesRepository,
            searchPdfText       = SearchPdfTextUseCase(appContext), // real, no mock
            documentHistoryDao  = mockk<DocumentHistoryDao>(relaxed = true),
            documentRepository  = mockk<DocumentRepository>(relaxed = true),
            trashRepository     = mockk<TrashRepository>(relaxed = true),
            adManager           = adManager
        )
    }

    // PDF real de 2 páginas con el mismo término buscable en ambas --
    // mismo patrón de creación de archivo real que
    // ConverterScreenTest.createTestImageFile(), vía iText7 (ya usado en
    // producción, ver StudyNotesExporter.kt).
    private fun createSearchablePdf(searchTerm: String): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(appContext.cacheDir, "viewer_search_test_${System.currentTimeMillis()}.pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        document.add(Paragraph("Página uno con el término $searchTerm dentro del texto."))
        document.add(AreaBreak())
        document.add(Paragraph("Página dos también contiene $searchTerm otra vez."))
        document.close()
        return file
    }

    // Fuerza español -- "Buscar en documento"/"Coincidencia X de Y" vienen
    // de stringResource(), y el emulador de CI arranca en inglés por
    // defecto (ver com.docsmart.core.ui.test.forceLocale).
    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localizedContext) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun escribirTermino_muestraCoincidenciasYPermiteNavegarEntreEllas() {
        val searchTerm = "ClaveBuscada"
        val pdfFile    = createSearchablePdf(searchTerm)
        val viewModel  = buildViewModel()

        setContentWithLocale {
            ViewerScreen(documentId = pdfFile.absolutePath, onBack = {}, viewModel = viewModel)
        }
        // Confirma que el documento cargó de verdad (no encriptado/error) --
        // el nombre real del archivo aparece en la barra superior.
        waitForText(pdfFile.name)

        composeRule.onNodeWithContentDescription("Buscar en documento").performClick()
        composeRule.onNodeWithText("Buscar en documento...").performTextInput(searchTerm)

        // searchInPdf() corre en una corrutina real (SearchPdfTextUseCase
        // real, no mockeado) -- se espera explícitamente el resultado en vez
        // de asumir que waitForIdle() alcanza.
        waitForText("Coincidencia 1 de 2")

        composeRule.onNodeWithContentDescription("Siguiente coincidencia").performClick()
        waitForText("Coincidencia 2 de 2")

        // nextPdfSearchResult() da la vuelta (módulo) -- de la última
        // coincidencia vuelve a la primera.
        composeRule.onNodeWithContentDescription("Siguiente coincidencia").performClick()
        waitForText("Coincidencia 1 de 2")

        composeRule.onNodeWithContentDescription("Coincidencia anterior").performClick()
        waitForText("Coincidencia 2 de 2")

        composeRule.onNodeWithText("Coincidencia 2 de 2").assertIsDisplayed()
    }
}
