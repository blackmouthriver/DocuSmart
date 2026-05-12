package com.docsmart.core.ads

object AdConstants {

    // ── IDs de prueba de Google (usar en desarrollo) ──────────────────────────
    // Al publicar reemplazar con los IDs reales de AdMob

    // Banners
    const val BANNER_HOME_ID       = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_LIBRARY_ID    = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_TOOLS_ID      = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_CONVERTER_ID  = "ca-app-pub-3940256099942544/6300978111" // ← NUEVO
    const val BANNER_SETTINGS_ID   = "ca-app-pub-3940256099942544/6300978111" // ← NUEVO

    // Interstitial
    const val INTERSTITIAL_CONVERSION_ID = "ca-app-pub-3940256099942544/1033173712"

    // ── Configuración de frecuencia ───────────────────────────────────────────
    const val INTERSTITIAL_MIN_CONVERSIONS  = 2
    const val INTERSTITIAL_MIN_INTERVAL_MS  = 180_000L // 3 minutos
}