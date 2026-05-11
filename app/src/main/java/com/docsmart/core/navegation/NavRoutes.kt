package com.docsmart.core.navegation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Splash      : NavRoutes("splash")
    data object Home        : NavRoutes("home")
    data object Library     : NavRoutes("library")
    data object Converter   : NavRoutes("converter")
    data object PdfTools    : NavRoutes("pdf_tools")
    data object Settings    : NavRoutes("settings")
    data object Premium     : NavRoutes("premium")
    data object Scanner     : NavRoutes("scanner")
    data object ScanResult  : NavRoutes("scan_result")
    data object Security    : NavRoutes("security")
    data object Study       : NavRoutes("study")

    data object Viewer : NavRoutes("viewer/{documentId}") {
        fun createRoute(documentId: String): String {
            return "viewer/${Uri.encode(documentId)}"
        }
    }
}