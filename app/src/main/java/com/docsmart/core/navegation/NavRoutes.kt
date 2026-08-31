package com.docsmart.core.navegation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object SplashMouthBlack : NavRoutes("splash_mouthblack")
    data object SplashDocuSmart  : NavRoutes("splash_docusmart")
    data object Home        : NavRoutes("home")
    data object Library     : NavRoutes("library")
    data object Converter : NavRoutes(
        "converter?initialType={initialType}&initialFileUri={initialFileUri}" +
            "&initialFileCategory={initialFileCategory}"
    ) {
        // Acceso rápido "Img→PDF" de Home: abre el Convertidor con un tipo ya
        // preseleccionado (nombre de ConversionType, p.ej. "IMAGE_TO_PDF") en
        // vez del genérico -- evita que ese acceso termine siendo un segundo
        // botón "Convertir" idéntico al CTA principal, sin distinguirse en
        // nada. `initialType = null` (la entrada genérica) sigue funcionando
        // igual que antes.
        //
        // `initialFileUri`/`initialFileCategory` (backlog UX 2026-08-30,
        // HU-UX-02): atajo "Convertir" desde el menú "⋮" de un archivo ya
        // elegido -- a diferencia de `initialType`, acá no se fija un
        // `ConversionType` exacto (el archivo puede tener varios destinos
        // posibles, p.ej. un PDF puede ir a Imagen/TXT/Word/HTML), solo se
        // precarga el archivo para que quede adjunto en cuanto el usuario
        // elija cuál de esos destinos quiere, sin tener que volver a
        // buscarlo.
        fun createRoute(
            initialType       : String? = null,
            initialFileUri    : String? = null,
            initialFileCategory: String? = null
        ): String {
            val params = buildList {
                initialType?.let { add("initialType=$it") }
                initialFileUri?.let { add("initialFileUri=${Uri.encode(it)}") }
                initialFileCategory?.let { add("initialFileCategory=${Uri.encode(it)}") }
            }
            return if (params.isEmpty()) "converter" else "converter?${params.joinToString("&")}"
        }
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
    data object QrCreator : NavRoutes(
        "qr_creator?initialFileUri={initialFileUri}&initialFileType={initialFileType}" +
            "&initialFileName={initialFileName}"
    ) {
        // Acceso rápido "Crear QR" desde el menú "⋮" de un archivo ya elegido
        // (backlog UX 2026-08-30, HU-UX-01): salta el picker de contenido y
        // llega directo con el archivo adjunto. `initialFileType` es "image"
        // o "document" -- decide qué chip preseleccionar (Imagen/Documento),
        // ya que ambos comparten el mismo mecanismo de adjuntar un archivo.
        fun createRoute(
            initialFileUri : String? = null,
            initialFileType: String? = null,
            initialFileName: String? = null
        ): String {
            val params = buildList {
                initialFileUri?.let { add("initialFileUri=${Uri.encode(it)}") }
                initialFileType?.let { add("initialFileType=$it") }
                initialFileName?.let { add("initialFileName=${Uri.encode(it)}") }
            }
            return if (params.isEmpty()) "qr_creator" else "qr_creator?${params.joinToString("&")}"
        }
    }
    data object Onboarding  : NavRoutes("onboarding")
    data object SecureFolder : NavRoutes("secure_folder")
    data object Trash        : NavRoutes("trash") // RF-VIS-07
    data object Viewer : NavRoutes("viewer/{documentId}") {
        fun createRoute(documentId: String): String {
            return "viewer/${Uri.encode(documentId)}"
        }
    }
}