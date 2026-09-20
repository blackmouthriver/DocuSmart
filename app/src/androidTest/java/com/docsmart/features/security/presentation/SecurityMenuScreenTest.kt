package com.docsmart.features.security.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 20: menú de Seguridad (Carpeta Segura / Contraseña PDF). Se usa un
 * usuario Premium para que no se monte el banner de anuncios real (AdMob).
 */
class SecurityMenuScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): SecurityMenuViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        return SecurityMenuViewModel(adManager)
    }

    @Test
    fun muestraLasDosOpcionesConSusInsignias() {
        composeRule.setContentEsScaled { SecurityMenuScreen(viewModel = buildViewModel()) }
        composeRule.waitForText(esText(R.string.security_title))

        composeRule.onNodeWithText(esText(R.string.security_subtitle)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_what_to_do)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_secure_folder)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_pin_required)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_pdf_password)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_no_pin)).assertExists()
    }

    @Test
    fun tocarCadaTarjetaYAtras_invocanSusCallbacks() {
        var secure = 0
        var pdf = 0
        var back = 0
        composeRule.setContentEsScaled {
            SecurityMenuScreen(
                onBack = { back++ },
                onSecureFolder = { secure++ },
                onPdfPassword = { pdf++ },
                viewModel = buildViewModel(),
            )
        }
        composeRule.waitForText(esText(R.string.security_title))

        composeRule.onNodeWithText(esText(R.string.security_secure_folder)).performClick()
        composeRule.onNodeWithText(esText(R.string.security_pdf_password)).performClick()
        composeRule.onNodeWithText(esText(R.string.general_back)).performClick()

        assertEquals(1, secure)
        assertEquals(1, pdf)
        assertEquals(1, back)
    }
}
