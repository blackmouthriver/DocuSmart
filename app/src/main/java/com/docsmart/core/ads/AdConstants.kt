package com.docsmart.core.ads

object AdConstants {

    // ── IDs de prueba de Google (usar en desarrollo) ──
    // Al publicar reemplazar con los IDs reales de AdMob

    // Banner
    const val BANNER_HOME_ID = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_LIBRARY_ID = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_TOOLS_ID = "ca-app-pub-3940256099942544/6300978111"

    // Interstitial
    const val INTERSTITIAL_CONVERSION_ID = "ca-app-pub-3940256099942544/1033173712"

    // ── Configuración de frecuencia ───────────────────
    // Mínimo de conversiones antes de mostrar interstitial
    const val INTERSTITIAL_MIN_CONVERSIONS = 2

    // Tiempo mínimo entre interstitials (milisegundos)
    const val INTERSTITIAL_MIN_INTERVAL_MS = 180_000L // 3 minutos
}