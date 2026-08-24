package com.docsmart.core.ads

object AdConstants {

    // ── Banners ───────────────────────────────────────────────────────────────
    // ID de prueba oficial de Google — reemplazar cada BANNER_*_ID por su
    // propio ID real de AdMob al publicar (deben ser distintos entre sí para
    // medir rendimiento por placement).
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

    const val BANNER_HOME_ID       = TEST_BANNER_ID
    const val BANNER_LIBRARY_ID    = TEST_BANNER_ID
    const val BANNER_TOOLS_ID      = TEST_BANNER_ID
    const val BANNER_CONVERTER_ID  = TEST_BANNER_ID
    const val BANNER_SETTINGS_ID   = TEST_BANNER_ID

    // ── Interstitial ──────────────────────────────────────────────────────────
    const val INTERSTITIAL_CONVERSION_ID = "ca-app-pub-3940256099942544/1033173712"

    // ── Rewarded (nuevo) ──────────────────────────────────────────────────────
    // ID de prueba de Google para Rewarded Ads
    const val REWARDED_UNLOCK_ID = "ca-app-pub-3940256099942544/5224354917"

    // ── Configuración de frecuencia ───────────────────────────────────────────
    const val INTERSTITIAL_MIN_CONVERSIONS = 2
    const val INTERSTITIAL_MIN_INTERVAL_MS = 180_000L // 3 minutos
}