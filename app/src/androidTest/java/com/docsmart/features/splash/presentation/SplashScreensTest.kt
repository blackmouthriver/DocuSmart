package com.docsmart.features.splash.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Baja #18 de RF-QA-01 (ver compose-ui-testing.md):
 * Splash -- transición automática a la siguiente pantalla.
 *
 * Ninguna de las dos pantallas tiene ViewModel/Hilt -- ambas leen
 * `Settings.Global.ANIMATOR_DURATION_SCALE` real del dispositivo para
 * decidir `reduceMotion` (con la escala en 0 en este dispositivo/CI, ver
 * `disable-animations: true`, saltan directo a los valores finales de
 * animación) y llaman a `onFinished()` tras un `delay(250)` fijo, sin
 * depender de que la animación realmente corra.
 */
class SplashScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Fuerza español -- "ESCANEA · ORGANIZA" viene de stringResource(), y
    // el emulador de CI arranca en inglés por defecto (ver
    // com.docsmart.core.ui.test.forceLocale).
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
    fun splashMouthBlack_muestraLogoYNavegaAutomaticamente() {
        var finished = false
        setContentWithLocale {
            SplashMouthBlackScreen(onFinished = { finished = true })
        }
        waitForText("mouthblack")
        composeRule.onNodeWithText("V1.0").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 5_000) { finished }
        assertTrue(finished)
    }

    @Test
    fun splashDocuSmart_muestraLogoYNavegaAutomaticamente() {
        var finished = false
        setContentWithLocale {
            SplashDocuSmartScreen(onFinished = { finished = true })
        }
        waitForText("docusmart")
        composeRule.onNodeWithText("ESCANEA · ORGANIZA").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 5_000) { finished }
        assertTrue(finished)
    }
}
