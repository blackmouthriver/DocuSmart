package com.docsmart.core.ads

object AdConstants {
    // ── Banners ───────────────────────────────────────────────────────────────
    // IDs reales de AdMob, creados 2026-09-10 en apps.admob.com -- uno
    // distinto por pantalla para medir rendimiento por placement en la
    // consola de AdMob.
    const val BANNER_HOME_ID = "ca-app-pub-1109506701099935/4789414908"
    const val BANNER_LIBRARY_ID = "ca-app-pub-1109506701099935/2031389628"
    const val BANNER_TOOLS_ID = "ca-app-pub-1109506701099935/9718307957"
    const val BANNER_CONVERTER_ID = "ca-app-pub-1109506701099935/5719353191"
    const val BANNER_SETTINGS_ID = "ca-app-pub-1109506701099935/7826641788"

    // Backlog UX 2026-08-30 (HU-UX-07): banner de anuncios consistente en
    // pantallas de contenido -- se dejan fuera a propósito Premium
    // (paywall), Contraseña PDF/Carpeta Segura/Papelera (acción rápida o
    // de seguridad) y los splash.
    const val BANNER_SCAN_RESULT_ID = "ca-app-pub-1109506701099935/8153944846"
    const val BANNER_SECURITY_ID = "ca-app-pub-1109506701099935/8966769965"
    const val BANNER_VIEWER_ID = "ca-app-pub-1109506701099935/9485996369"
    const val BANNER_STUDY_ID = "ca-app-pub-1109506701099935/3887396772"
    const val BANNER_QR_ID = "ca-app-pub-1109506701099935/3363441149"

    // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
    // G5): Agenda reutilizaba BANNER_STUDY_ID por no tener bloque propio
    // creado en AdMob todavía -- mezclaba las métricas de impresiones de
    // ambas pantallas en la consola. Bloque creado por el usuario el
    // 2026-09-18.
    const val BANNER_AGENDA_ID = "ca-app-pub-1109506701099935/9228781384"

    // ── Interstitial ──────────────────────────────────────────────────────────
    const val INTERSTITIAL_CONVERSION_ID = "ca-app-pub-1109506701099935/7653688296"

    // ── Rewarded ──────────────────────────────────────────────────────────────
    const val REWARDED_UNLOCK_ID = "ca-app-pub-1109506701099935/2920588016"

    // ── Configuración de frecuencia ───────────────────────────────────────────
    const val INTERSTITIAL_MIN_CONVERSIONS = 2
    const val INTERSTITIAL_MIN_INTERVAL_MS = 180_000L // 3 minutos
}
