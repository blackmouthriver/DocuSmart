package com.docsmart.features.scanner.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.test.forceLocale
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Media #13 de RF-QA-01 (ver compose-ui-testing.md): Crear
 * QR (RF-SEC-09/10) -- generar con/sin contraseña. Solo cubre `QrCreatorScreen`,
 * no `QrReaderScreen`: leer un QR depende 100% de CameraX + Google ML Kit en
 * vivo (`AndroidView`/`ProcessCameraProvider`/`BarcodeScanning`), sin código
 * propio que testear ahí -- mismo criterio ya documentado para el Escáner
 * (fila 12 de la tabla) en vez de forzar una prueba frágil sobre hardware.
 *
 * `QrViewModel` no tiene estado propio (todo vive en `remember` dentro del
 * Composable) -- solo se mockea `AdManager` para no mostrar el banner de
 * anuncios (mismo patrón que el resto de pantallas de contenido).
 */
class QrCreatorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): QrViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        return QrViewModel(adManager = adManager)
    }

    // Fuerza español -- el emulador de CI arranca en inglés por defecto
    // (ver com.docsmart.core.ui.test.forceLocale). QrCreatorScreen usa
    // rememberLauncherForActivityResult() para elegir imagen/documento --
    // mismo motivo que ConverterScreenTest/PdfToolsScreenTest para
    // reproveer estos dos apuntando a la Activity real.
    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun crearQrDeUrl_sinContrasena_generaElCodigo() {
        setContentWithLocale {
            QrCreatorScreen(viewModel = buildViewModel())
        }
        waitForText("URL del sitio web")

        composeRule.onNodeWithText("URL del sitio web").performTextInput("ejemplo.com")
        composeRule.onNodeWithText("Generar QR").performClick()

        waitForText("Tu código QR")
        composeRule.onNodeWithText("Tu código QR").assertIsDisplayed()
    }

    @Test
    fun crearQrDeTexto_conContrasena_muestraInsigniaProtegido() {
        setContentWithLocale {
            QrCreatorScreen(viewModel = buildViewModel())
        }
        waitForText("URL del sitio web")

        composeRule.onNodeWithText("Texto").performClick()
        waitForText("Texto del mensaje")
        composeRule.onNodeWithText("Texto del mensaje").performTextInput("Hola DocuSmart")

        // El Switch de "Proteger con contraseña" no tiene contentDescription
        // propia -- es el único elemento Toggleable de la pantalla.
        composeRule.onNode(isToggleable()).performClick()
        waitForText("Contraseña")
        composeRule.onNodeWithText("Contraseña").performTextInput("1234")

        composeRule.onNodeWithText("Generar QR").performScrollTo().performClick()

        waitForText("Tu código QR")
        waitForText("Protegido")
        // La insignia "Protegido" queda debajo del código QR generado, fuera
        // del viewport visible de la Column con scroll -- hay que llevarla a
        // la vista antes de comprobar que se muestra (mismo tipo de hallazgo
        // ya documentado en PdfToolsScreenTest para un elemento fuera de
        // pantalla, aquí en un Column con scroll en vez de una LazyColumn).
        composeRule.onNodeWithText("Protegido").performScrollTo().assertIsDisplayed()
    }
}
