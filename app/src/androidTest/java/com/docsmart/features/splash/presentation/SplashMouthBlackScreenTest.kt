package com.docsmart.features.splash.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 23: `SplashMouthBlackScreen` (0% de cobertura). `SplashScreensTest`
 * ya la renderiza con el valor real (ambiental) de
 * `Settings.Global.ANIMATOR_DURATION_SCALE` -- en el emulador de CI esa
 * escala viene en 0, así que ese test SIEMPRE cae en la rama corta
 * (`reduceMotion = true`) y nunca ejerce las animaciones (`keyframes`,
 * los 3 `launch` y el `delay(1150)`), sin importar el dispositivo real.
 *
 * Este archivo usa el parámetro `reduceMotionOverride` (agregado en esta
 * ronda para poder testear ambas ramas de forma determinística, sin
 * depender de un ajuste real del sistema) para forzar cada rama por
 * separado.
 */
class SplashMouthBlackScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun conAnimacionCompleta_muestraElLogoYNavegaAlTerminar() {
        var finished = false
        composeRule.setContent {
            SplashMouthBlackScreen(onFinished = { finished = true }, reduceMotionOverride = false)
        }

        waitForText("mouthblack")
        composeRule.onNodeWithText("V1.0").assertIsDisplayed()
        assertTrue(!finished)

        // La secuencia completa (keyframes + delay(1150) + delay(250)) dura
        // ~1.4s -- se da margen generoso sin depender de tiempos reales largos.
        composeRule.waitUntil(timeoutMillis = 5_000) { finished }
        assertTrue(finished)
    }

    @Test
    fun conMovimientoReducidoForzado_saltaLaAnimacionYNavegaRapido() {
        var finished = false
        composeRule.setContent {
            SplashMouthBlackScreen(onFinished = { finished = true }, reduceMotionOverride = true)
        }

        waitForText("mouthblack")
        composeRule.onNodeWithText("V1.0").assertIsDisplayed()

        // Sin animación: solo el delay(250) final antes de navegar.
        composeRule.waitUntil(timeoutMillis = 3_000) { finished }
        assertTrue(finished)
    }

    // Sin overrides los tests de arriba no ejercen la rama por defecto
    // (reduceMotionOverride == null): DocuSmartNavGraph llama así en
    // producción, dejando que se use el valor real del sistema.
    @Test
    fun sinOverride_usaElValorRealDelSistemaYNavegaAlTerminar() {
        var finished = false
        composeRule.setContent {
            SplashMouthBlackScreen(onFinished = { finished = true })
        }

        waitForText("mouthblack")
        composeRule.onNodeWithText("V1.0").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 5_000) { finished }
        assertTrue(finished)
    }
}
