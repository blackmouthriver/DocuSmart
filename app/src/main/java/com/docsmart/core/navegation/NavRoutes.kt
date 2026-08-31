package com.docsmart.core.navegation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object SplashMouthBlack : NavRoutes("splash_mouthblack")
    data object SplashDocuSmart  : NavRoutes("splash_docusmart")
    data object Home        : NavRoutes("home")
    data object Library     : NavRoutes("library")
    data object Converter : NavRoutes("converter?initialType={initialType}") {
        // Acceso rápido "Img→PDF" de Home: abre el Convertidor con un tipo ya
        // preseleccionado (nombre de ConversionType, p.ej. "IMAGE_TO_PDF") en
        // vez del genérico -- evita que ese acceso termine siendo un segundo
        // botón "Convertir" idéntico al CTA principal, sin distinguirse en
        // nada. `initialType = null` (la entrada genérica) sigue funcionando
        // igual que antes.
        fun createRoute(initialType: String? = null): String =
            if (initialType != null) "converter?initialType=$initialType" else "converter"
    }
    data object PdfTools    : NavRoutes("pdf_tools")
    data object Settings    : NavRoutes("settings")
    data object Premium     : NavRoutes("premium")
    data object Scanner     : NavRoutes("scanner")
    data object ScanResult  : NavRoutes("scan_result")
    data object Security    : NavRoutes("security")
    data object PdfPassword : NavRoutes("pdf_password")  // ← NUEVA
    data object Study : NavRoutes("study?tab={tab}") {
        // Acceso rápido a una pestaña específica de Estudio (Lectura=0,
        // Notas=1, Pomodoro=2) desde Home -- antes solo había un punto de
        // entrada genérico que siempre abría en Lectura.
        fun createRoute(tab: Int = 0): String = "study?tab=$tab"
    }
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