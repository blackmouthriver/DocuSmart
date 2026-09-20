package com.docsmart.features.scanner.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.test.forceLocale
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ScannerScreen.kt: pantalla de espera mientras arranca el escáner de ML Kit.
 * El contexto de prueba (envuelto para fijar el locale) NO es una Activity, así
 * que la pantalla toma la rama de "sin Activity": limpia la sesión y vuelve
 * atrás sin abrir jamás el escáner real (cámara/Google Play Services).
 */
class ScannerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(id)

    @Test
    fun sinActivity_limpiaLaSesionYVuelveAtras() {
        val session = mockk<ScanSessionViewModel>(relaxed = true)
        var backCount = 0
        val viewModel = ScannerViewModel(mockk<SoundEffectPlayer>(relaxed = true))

        composeRule.setContentEs {
            ScannerScreen(
                onBack = { backCount++ },
                onScanComplete = {},
                viewModel = viewModel,
                scanSessionViewModel = session,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.scanner_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.scanner_hint)).assertExists()
        verify(atLeast = 1) { session.clearSession() }
        assertEquals(1, backCount)
    }

    @Test
    fun conError_muestraElMensajeYElBotonVolver() {
        val session = mockk<ScanSessionViewModel>(relaxed = true)
        var backCount = 0
        val viewModel = ScannerViewModel(mockk<SoundEffectPlayer>(relaxed = true))
        viewModel.onError("No se pudo iniciar el escáner")

        composeRule.setContentEs {
            ScannerScreen(
                onBack = { backCount++ },
                onScanComplete = {},
                viewModel = viewModel,
                scanSessionViewModel = session,
            )
        }
        composeRule.waitForIdle()
        val backAfterLaunch = backCount

        composeRule.onNodeWithText("No se pudo iniciar el escáner").assertExists()
        composeRule.onNodeWithText(str(R.string.general_back)).performClick()

        assertEquals(backAfterLaunch + 1, backCount)
    }

    @Test
    fun sinError_muestraElIndicadorDeCargaSinBotonVolver() {
        val session = mockk<ScanSessionViewModel>(relaxed = true)
        val viewModel = ScannerViewModel(mockk<SoundEffectPlayer>(relaxed = true))

        composeRule.setContentEs {
            ScannerScreen(onBack = {}, onScanComplete = {}, viewModel = viewModel, scanSessionViewModel = session)
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText(str(R.string.general_back)).fetchSemanticsNodes().isEmpty())
    }
}
