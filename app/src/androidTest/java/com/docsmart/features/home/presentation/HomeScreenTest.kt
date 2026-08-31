package com.docsmart.features.home.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Flujos de prioridad Alta #6 y #7 de RF-QA-01 (ver compose-ui-testing.md):
 * Home -- ver recientes/favorito/accesos rápidos, y "Eliminar" mueve a la
 * papelera y desaparece de la lista.
 *
 * Nota de alcance: `HomeScreen` recibe los callbacks de navegación de los
 * accesos rápidos (`onSecurity`, `onScan`, etc.) ya resueltos desde afuera
 * (`DocuSmartNavGraph.kt` es quien navega de verdad) -- acá se verifica que
 * tocar el acceso invoca el callback correcto, no que la navegación real
 * ocurra (eso requeriría un NavController real, fuera del alcance de esta
 * prueba de UI aislada).
 *
 * Existe un `HomeViewModel` orfano en el paquete `converter.presentation`
 * (datos mock, sin ninguna referencia real en el código) -- no es el que
 * usa `HomeScreen` (mismo paquete `home.presentation`, resuelto sin
 * import); se flaguea aparte como código muerto, no se toca acá.
 */
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var trashRepository: TrashRepository
    private lateinit var favoritesRepository: FavoritesRepository

    private fun documentFixture(id: String, name: String) = DocumentUiModel(
        id   = id,
        name = name,
        type = DocumentType.PDF,
        size = "1.0 MB",
        date = "Hoy"
    )

    private fun buildViewModel(documents: List<DocumentUiModel>): HomeViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadRecentlyOpened(5) } returns documents

        trashRepository     = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)

        return HomeViewModel(
            adManager           = adManager,
            repository          = repository,
            trashRepository     = trashRepository,
            favoritesRepository = favoritesRepository
        )
    }

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Seguridad"/"Eliminar" vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // HomeScreen usa rememberLauncherForActivityResult() para el
            // picker "Abrir archivo" -- mismo motivo que
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
    fun verRecientes_muestraLosDocumentosRecientes() {
        val viewModel = buildViewModel(listOf(documentFixture("doc1", "Contrato.pdf")))

        setContentWithLocale { HomeScreen(viewModel = viewModel) }
        waitForText("Contrato.pdf")

        composeRule.onNodeWithText("Contrato.pdf").assertIsDisplayed()
    }

    @Test
    fun tocarFavorito_llamaAToggleFavoriteConElIdCorrecto() {
        val viewModel = buildViewModel(listOf(documentFixture("doc1", "Contrato.pdf")))
        coEvery { favoritesRepository.toggleFavorite("doc1") } returns true

        setContentWithLocale { HomeScreen(viewModel = viewModel) }
        waitForText("Contrato.pdf")

        composeRule.onNodeWithContentDescription("Agregar a favoritos").performClick()
        composeRule.waitForIdle()

        coVerify { favoritesRepository.toggleFavorite("doc1") }
    }

    @Test
    fun tocarAccesoRapidoSeguridad_llamaAlCallbackDeNavegacion() {
        val viewModel = buildViewModel(emptyList())
        var securityTapped = false

        setContentWithLocale {
            HomeScreen(viewModel = viewModel, onSecurity = { securityTapped = true })
        }
        waitForText("Seguridad")

        composeRule.onNodeWithText("Seguridad").performClick()
        composeRule.waitForIdle()

        assertTrue("onSecurity debería haberse invocado al tocar el acceso rápido", securityTapped)
    }

    @Test
    fun eliminarDocumento_muevoATrashYDesaparaceDeLaLista() {
        val viewModel = buildViewModel(listOf(documentFixture("doc1", "Contrato.pdf")))
        coEvery { trashRepository.moveToTrash("doc1") } returns true

        setContentWithLocale { HomeScreen(viewModel = viewModel) }
        waitForText("Contrato.pdf")

        composeRule.onNodeWithContentDescription("Más opciones").performClick()
        waitForText("Eliminar")

        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.waitForIdle()

        coVerify { trashRepository.moveToTrash("doc1") }
        composeRule.onAllNodesWithText("Contrato.pdf").assertCountEquals(0)
    }
}
