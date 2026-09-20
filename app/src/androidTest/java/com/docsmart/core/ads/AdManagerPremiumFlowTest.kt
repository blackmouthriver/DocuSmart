package com.docsmart.core.ads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.premium.PremiumManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 20: `AdManager` real (con `Handler`/`Looper` de Android) pero SIN
 * cargar anuncios: nunca se llama a `initialize()` y el estado Premium solo
 * cambia de "no premium" a "premium" (que vacía la caché en vez de recargar).
 * Los caminos que pedirían un anuncio real (rewarded sin anuncio cargado
 * fuera de Premium) quedan sin cubrir a propósito.
 */
class AdManagerPremiumFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val premiumFlow = MutableStateFlow(false)

    private fun buildManager(): AdManager {
        val premiumManager = mockk<PremiumManager>(relaxed = true)
        every { premiumManager.isPremium } returns premiumFlow
        every { premiumManager.isPaidPremium } returns MutableStateFlow(false)
        every { premiumManager.trialEndsAtMillis } returns MutableStateFlow<Long?>(null)
        every { premiumManager.autoTrialDaysRemaining } returns MutableStateFlow<Int?>(3)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return AdManager(context, premiumManager)
    }

    @Test
    fun estadoInicial_exponeLosPassthroughDePremium() {
        val manager = buildManager()

        assertFalse(manager.isPremium.value)
        assertFalse(manager.isPaidPremium.value)
        assertNull(manager.trialEndsAtMillis.value)
        assertEquals(3, manager.autoTrialDaysRemaining.value)
        assertFalse(manager.isInitialized.value)
        assertFalse(manager.isRewardedReady.value)
    }

    @Test
    fun conversionSinAnuncioCargado_noHaceNadaNiPideAnuncios() {
        val manager = buildManager()

        // Sin interstitial cargado nunca se muestra nada (y no se intenta cargar uno).
        manager.onConversionCompleted(composeRule.activity)
        manager.onConversionCompleted(composeRule.activity)
        manager.onConversionCompleted(composeRule.activity)

        assertFalse(manager.isRewardedReady.value)
    }

    @Test
    fun getAdRequest_construyeUnaSolicitudSinRed() {
        assertNotNull(buildManager().getAdRequest())
    }

    @Test
    fun alPasarAPremium_losAnunciosSeBloqueanYElRewardedFalla() {
        composeRule.setContent {}
        val manager = buildManager()

        premiumFlow.value = true
        composeRule.waitUntil(5_000) { manager.isPremium.value }

        var failed = 0
        var rewarded = 0
        manager.showRewardedAd(composeRule.activity, onRewarded = { rewarded++ }, onFailed = { failed++ })
        manager.onConversionCompleted(composeRule.activity)

        assertEquals(1, failed)
        assertEquals(0, rewarded)
        assertFalse(manager.isRewardedReady.value)
    }
}
