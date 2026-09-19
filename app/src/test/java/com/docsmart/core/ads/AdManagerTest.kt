package com.docsmart.core.ads

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `AdManager` en sí no se puede construir en un test unitario JVM puro: su
 * constructor evalúa `Handler(Looper.getMainLooper())` como propiedad de
 * clase, y `Looper.getMainLooper()` revienta fuera de un runtime Android
 * real (no hay Robolectric en este proyecto -- mismo límite ya documentado
 * para `CompressPdfUseCase`/`OcrPdfUseCase`). Lo que sí es lógica pura y
 * merece cobertura real es la decisión de cuándo mostrar un Interstitial,
 * extraída a `shouldShowInterstitial()` específicamente para poder
 * testearla.
 */
class AdManagerTest {
    @Test
    fun `no muestra el interstitial antes de alcanzar el minimo de conversiones`() {
        assertFalse(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS - 1,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `muestra el interstitial justo al alcanzar el minimo de conversiones y el intervalo`() {
        assertTrue(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `no muestra el interstitial si aun no paso el intervalo minimo, aunque haya conversiones de sobra`() {
        assertFalse(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS + 10,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS - 1,
            ),
        )
    }

    @Test
    fun `muestra el interstitial si sobran conversiones y ya paso de sobra el intervalo`() {
        assertTrue(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS + 10,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS + 60_000L,
            ),
        )
    }

    @Test
    fun `no muestra el interstitial sin conversiones ni tiempo transcurrido`() {
        assertFalse(shouldShowInterstitial(conversionCount = 0, timeSinceLastMs = 0L))
    }

    // ── canShowFullScreenAd (ronda 14) ──────────────────────────────────────
    // Hallazgo real de la auditoría general 2026-09-18: ni
    // onConversionCompleted() ni showRewardedAd() validaban que la Activity
    // recibida siguiera viva antes de show() -- crash real conocido de
    // AdMob (BadTokenException) si el usuario ya la abandonó. Extraída a
    // canShowFullScreenAd() por el mismo motivo que shouldShowInterstitial().

    @Test
    fun `canShowFullScreenAd es true para una activity viva`() {
        val activity = mockk<Activity>()
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false

        assertTrue(canShowFullScreenAd(activity))
    }

    @Test
    fun `canShowFullScreenAd es false si la activity ya esta terminando`() {
        val activity = mockk<Activity>()
        every { activity.isFinishing } returns true
        every { activity.isDestroyed } returns false

        assertFalse(canShowFullScreenAd(activity))
    }

    @Test
    fun `canShowFullScreenAd es false si la activity ya fue destruida`() {
        val activity = mockk<Activity>()
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns true

        assertFalse(canShowFullScreenAd(activity))
    }

    // ── Decisiones extraídas de AdManager (ronda 17) ────────────────────────

    @Test
    fun `al volverse Premium se vacia la cache de anuncios`() {
        assertEquals(AdCacheAction.CLEAR, adCacheActionOnPremiumChange(isPremium = true))
    }

    @Test
    fun `al dejar de ser Premium se recargan los anuncios`() {
        assertEquals(AdCacheAction.RELOAD, adCacheActionOnPremiumChange(isPremium = false))
    }

    @Test
    fun `un usuario Premium nunca ve un rewarded aunque haya uno cargado`() {
        assertEquals(
            RewardedRequestOutcome.PREMIUM_BLOCKED,
            rewardedRequestOutcome(isPremium = true, adLoaded = true),
        )
        assertEquals(
            RewardedRequestOutcome.PREMIUM_BLOCKED,
            rewardedRequestOutcome(isPremium = true, adLoaded = false),
        )
    }

    @Test
    fun `un usuario gratis sin rewarded cargado debe precargar`() {
        assertEquals(
            RewardedRequestOutcome.NO_AD_LOADED,
            rewardedRequestOutcome(isPremium = false, adLoaded = false),
        )
    }

    @Test
    fun `un usuario gratis con rewarded cargado puede mostrarlo`() {
        assertEquals(
            RewardedRequestOutcome.AD_AVAILABLE,
            rewardedRequestOutcome(isPremium = false, adLoaded = true),
        )
    }

    // Hallazgo ronda 17: un fallo de carga dejaba "Ver anuncio" deshabilitado
    // para siempre; ahora se reintenta con backoff acotado.
    @Test
    fun `adRetryDelayMs escala 15s, 30s y 60s`() {
        assertEquals(15_000L, adRetryDelayMs(1))
        assertEquals(30_000L, adRetryDelayMs(2))
        assertEquals(60_000L, adRetryDelayMs(3))
    }

    @Test
    fun `adRetryDelayMs deja de reintentar al agotar los intentos`() {
        assertEquals(null, adRetryDelayMs(4))
        assertEquals(null, adRetryDelayMs(0))
    }
}
