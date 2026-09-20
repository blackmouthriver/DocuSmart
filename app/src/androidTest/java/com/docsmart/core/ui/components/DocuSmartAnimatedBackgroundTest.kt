package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: fondo animado global. Es decorativo (capa 0 detrás del contenido):
 * las pruebas verifican que se compone y avanza frames sin fallar, y que no
 * intercepta los toques del contenido que lo cubre.
 *
 * Nota: la rama `reduceMotion` depende de `ANIMATOR_DURATION_SCALE` del
 * dispositivo (0 en CI con `disable-animations`), no se puede forzar desde la
 * prueba sin permisos de escritura de Settings.
 */
class DocuSmartAnimatedBackgroundTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun seComponeYAvanzaLosFramesDeLaAnimacionSinFallar() {
        composeRule.setContent {
            MaterialTheme { DocuSmartAnimatedBackground(modifier = Modifier.fillMaxSize()) }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onRoot().assertExists()
    }

    @Test
    fun noInterceptaLosToquesDelContenidoQueLoCubre() {
        var taps = 0
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    DocuSmartAnimatedBackground(modifier = Modifier.fillMaxSize())
                    Text("Contenido", modifier = Modifier.clickable { taps++ })
                }
            }
        }

        composeRule.onNodeWithText("Contenido").performClick()
        composeRule.waitForIdle()

        assertEquals(1, taps)
    }
}
