package com.docsmart.core.navegation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Splash    : NavRoutes("splash")
    data object Home      : NavRoutes("home")
    data object Library   : NavRoutes("library")
    data object Converter : NavRoutes("converter")
    data object PdfTools  : NavRoutes("pdf_tools")
    data object Settings  : NavRoutes("settings")
    data object Premium   : NavRoutes("premium")

    // ── Viewer con encoding seguro de URI ─────────────
    // El problema era que URIs como content://... contienen
    // caracteres especiales (://, /, %) que rompen el NavGraph.
    // Uri.encode() los convierte en caracteres seguros para la ruta.
    data object Viewer : NavRoutes("viewer/{documentId}") {
        fun createRoute(documentId: String): String {
            return "viewer/${Uri.encode(documentId)}"
        }
    }
}