package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Flujo crítico #1 del manual de marca: "el usuario debe poder abrir un
 * documento en menos de 3 toques". Primera prueba de Compose UI del
 * proyecto — instrumentada, corre en dispositivo/emulador (JUnit4, no
 * JUnit5, por herramental de Google — el resto del proyecto sigue en
 * JUnit5 en `test/`, este source set es aparte).
 *
 * ViewerScreen ya acepta `viewModel` como parámetro con default
 * `hiltViewModel()` — en el test se pasa una instancia construida a mano
 * con fakes/mocks (mismo patrón que ViewerViewModelTest a nivel unitario,
 * si existiera), así que no hace falta infraestructura de Hilt para probar
 * la UI real. `documentId = "1"` resuelve por la vía mock
 * (`getMockDocument`), sin tocar ContentResolver ni archivos reales.
 */
class ViewerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var favoritesRepository: FavoritesRepository

    private fun buildViewModel(): ViewerViewModel {
        favoritesRepository = mockk(relaxed = true)
        every { favoritesRepository.isFavorite("1") } returns false

        // Los StateFlow<Boolean> de AdManager se leen directo en Composables
        // de anuncios (DocuSmartBannerAd, agregado a ViewerScreen en el
        // backlog UX 2026-08-30) -- un mock relajado sin stub explícito para
        // cada uno causa un ClassCastException al leer el proxy de MockK
        // como Boolean primitivo (mismo patrón ya usado en ConverterScreenTest).
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        return ViewerViewModel(
            favoritesRepository = favoritesRepository,
            searchPdfText = mockk<SearchPdfTextUseCase>(relaxed = true),
            documentHistoryDao = mockk<DocumentHistoryDao>(relaxed = true),
            documentRepository = mockk<DocumentRepository>(relaxed = true),
            trashRepository = mockk<TrashRepository>(relaxed = true),
            adManager = adManager,
            // Hallazgo real 2026-09-15 (CI): ViewerViewModel ganó estos dos
            // parámetros con HU-46 (migración de anotaciones) pero este
            // builder no se actualizó -- detectado por
            // compileDebugAndroidTestKotlin en CI, que no corre localmente
            // sin un dispositivo/emulador.
            annotationDao = mockk(relaxed = true),
            flattenAnnotationsPdfUseCase = mockk(relaxed = true),
            // Backlog UX #47/#48: marcadores de página + última página
            // vista, mismo criterio que annotationDao arriba.
            pageBookmarkDao = mockk(relaxed = true),
            lastViewedPageDao = mockk(relaxed = true),
            // Backlog UX #50: notas de Modo Estudio vinculadas.
            noteDao = mockk(relaxed = true),
        )
    }

    // Fuerza español (el emulador de CI arranca en inglés) y ajusta la densidad a pantallas chicas.
    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalResources provides localizedContext.resources,
                LocalDensity provides testViewportDensity(),
            ) { content() }
        }
    }

    @Test
    fun abrirDocumento_muestraElNombreDelArchivoEnLaBarraSuperior() {
        setContentWithLocale {
            ViewerScreen(documentId = "1", onBack = {}, viewModel = buildViewModel())
        }

        composeRule.onNodeWithText("Contrato_Servicios_2024.pdf").assertIsDisplayed()
    }

    @Test
    fun tocarFavorito_llamaAToggleFavoriteConElIdDelDocumentoAbierto() {
        val viewModel = buildViewModel()
        setContentWithLocale {
            ViewerScreen(documentId = "1", onBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Favorito").performClick()
        composeRule.waitForIdle()

        coVerify { favoritesRepository.toggleFavorite("1") }
    }
}
