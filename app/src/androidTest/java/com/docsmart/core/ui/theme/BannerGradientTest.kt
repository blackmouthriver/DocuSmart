package com.docsmart.core.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Degradado de banners con texto blanco: contraste garantizado en todos los acentos y temas. */
class BannerGradientTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private var accent by mutableStateOf(AccentColor.BLUE)
    private var banner: List<Color> = emptyList()
    private var plain: List<Color> = emptyList()

    private fun contrastWithWhite(color: Color): Float = 1.05f / (color.luminance() + 0.05f)

    private fun render() {
        composeRule.setContent {
            DocuSmartTheme(darkTheme = dark, accentColor = accent) {
                banner = rememberBannerGradient()
                plain = rememberAccentGradient()
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun cadaAcento_enClaroYOscuro_elBannerTieneContrasteParaTextoBlanco() {
        render()
        listOf(false, true).forEach { isDark ->
            AccentColor.entries.forEach { entry ->
                composeRule.runOnIdle {
                    dark = isDark
                    accent = entry
                }
                composeRule.waitForIdle()
                assertEquals(3, banner.size)
                val ratio = contrastWithWhite(banner[1])
                assertTrue("${entry.label} dark=$isDark: $ratio", ratio >= 5f)
            }
        }
    }

    @Test
    fun elDegradadoSimple_sigueDevolviendoTresTonos() {
        render()
        assertEquals(3, plain.size)
    }
}
