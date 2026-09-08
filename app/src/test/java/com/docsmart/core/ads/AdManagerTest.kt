package com.docsmart.core.ads

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
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS
            )
        )
    }

    @Test
    fun `muestra el interstitial justo al alcanzar el minimo de conversiones y el intervalo`() {
        assertTrue(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS
            )
        )
    }

    @Test
    fun `no muestra el interstitial si aun no paso el intervalo minimo, aunque haya conversiones de sobra`() {
        assertFalse(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS + 10,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS - 1
            )
        )
    }

    @Test
    fun `muestra el interstitial si sobran conversiones y ya paso de sobra el intervalo`() {
        assertTrue(
            shouldShowInterstitial(
                conversionCount = AdConstants.INTERSTITIAL_MIN_CONVERSIONS + 10,
                timeSinceLastMs = AdConstants.INTERSTITIAL_MIN_INTERVAL_MS + 60_000L
            )
        )
    }

    @Test
    fun `no muestra el interstitial sin conversiones ni tiempo transcurrido`() {
        assertFalse(shouldShowInterstitial(conversionCount = 0, timeSinceLastMs = 0L))
    }
}
