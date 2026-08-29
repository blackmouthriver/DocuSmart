package com.docsmart.core.navegation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object SplashMouthBlack : NavRoutes("splash_mouthblack")
    data object SplashDocuSmart  : NavRoutes("splash_docusmart")
    data object Home        : NavRoutes("home")
    data object Library     : NavRoutes("library")
    data object Converter   : NavRoutes("converter")
    data object PdfTools    : NavRoutes("pdf_tools")
    data object Settings    : NavRoutes("settings")
    data object Premium     : NavRoutes("premium")
    data object Scanner     : NavRoutes("scanner")
    data object ScanResult  : NavRoutes("scan_result")
    data object Security    : NavRoutes("security")
    data object PdfPassword : NavRoutes("pdf_password")  // ← NUEVA
    data object Study       : NavRoutes("study")
    data object Qr          : NavRoutes("qr")
    data object QrReader    : NavRoutes("qr_reader")
    data object QrCreator   : NavRoutes("qr_creator")
    data object Onboarding  : NavRoutes("onboarding")
    data object SecureFolder : NavRoutes("secure_folder")
    data object Trash        : NavRoutes("trash") // RF-VIS-07
    data object Viewer : NavRoutes("viewer/{documentId}") {
        fun createRoute(documentId: String): String {
            return "viewer/${Uri.encode(documentId)}"
        }
    }
}