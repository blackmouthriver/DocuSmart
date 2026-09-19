package com.docsmart.core.navegation

import com.docsmart.core.ui.components.DocumentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ronda 16: logica pura extraida de DocuSmartNavGraph (0% de cobertura).
 */
class NavigationLogicTest {
    private val allTemplates =
        listOf(
            NavRoutes.SplashMouthBlack.route,
            NavRoutes.SplashDocuSmart.route,
            NavRoutes.Onboarding.route,
            NavRoutes.Home.route,
            NavRoutes.Library.route,
            NavRoutes.Viewer.route,
            NavRoutes.Converter.route,
            NavRoutes.PdfTools.route,
            NavRoutes.Settings.route,
            NavRoutes.Premium.route,
            NavRoutes.Scanner.route,
            NavRoutes.ScanResult.route,
            NavRoutes.Security.route,
            NavRoutes.SecureFolder.route,
            NavRoutes.PdfPassword.route,
            NavRoutes.Study.route,
            NavRoutes.Agenda.route,
            NavRoutes.QrReader.route,
            NavRoutes.QrCreator.route,
            NavRoutes.QrHistory.route,
            NavRoutes.Trash.route,
        )

    @Test
    fun `primera vez va a Onboarding y ya visto va a Home`() {
        assertEquals(NavRoutes.Onboarding.route, startDestinationAfterSplash(onboardingCompleted = false))
        assertEquals(NavRoutes.Home.route, startDestinationAfterSplash(onboardingCompleted = true))
    }

    @Test
    fun `atras en el Visor cierra la Activity si no hay pantalla previa`() {
        assertTrue(shouldFinishActivityOnViewerBack(null))
    }

    @Test
    fun `atras en el Visor cierra la Activity si la previa es otro Visor`() {
        assertTrue(shouldFinishActivityOnViewerBack(NavRoutes.Viewer.route))
    }

    @Test
    fun `atras en el Visor vuelve a Home o Biblioteca si vienen de ahi`() {
        assertFalse(shouldFinishActivityOnViewerBack(NavRoutes.Home.route))
        assertFalse(shouldFinishActivityOnViewerBack(NavRoutes.Library.route))
        assertFalse(shouldFinishActivityOnViewerBack(NavRoutes.Converter.route))
    }

    @Test
    fun `ninguna ruta del grafo llega a analytics con la plantilla cruda`() {
        allTemplates.forEach { route ->
            val name = screenNameForRoute(route)

            assertFalse(name.contains("{") || name.contains("?") || name.contains("/"), "ruta cruda: $name")
            assertTrue(name.first().isUpperCase(), "nombre no normalizado: $name")
        }
    }

    @Test
    fun `agenda se registra como Agenda y no como su plantilla`() {
        assertEquals("Agenda", screenNameForRoute(NavRoutes.Agenda.route))
    }

    @Test
    fun `una ruta desconocida se devuelve tal cual`() {
        assertEquals("otra_pantalla", screenNameForRoute("otra_pantalla"))
    }

    @Test
    fun `los nombres de pantalla del mapa no se repiten`() {
        val names = SCREEN_NAMES_BY_ROUTE.values
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `la categoria del Convertidor cubre cada tipo y devuelve null solo sin conversion`() {
        assertEquals("Imagen", DocumentType.IMAGE.toConverterCategoryOrNull())
        assertEquals("PDF", DocumentType.PDF.toConverterCategoryOrNull())
        assertEquals("PDF", DocumentType.OCR.toConverterCategoryOrNull())
        assertEquals("Word", DocumentType.WORD.toConverterCategoryOrNull())
        assertEquals("Excel", DocumentType.EXCEL.toConverterCategoryOrNull())
        assertEquals("PowerPoint", DocumentType.POWERPOINT.toConverterCategoryOrNull())
        assertNull(DocumentType.TEXT.toConverterCategoryOrNull())
        assertNull(DocumentType.ZIP.toConverterCategoryOrNull())
    }

    @Test
    fun `Crear QR distingue imagen de documento`() {
        assertEquals("image", DocumentType.IMAGE.toQrFileType())
        DocumentType.entries.filter { it != DocumentType.IMAGE }.forEach {
            assertEquals("document", it.toQrFileType())
        }
    }

    @Test
    fun `las rutas sin argumentos usan la forma corta`() {
        assertEquals("converter", NavRoutes.Converter.createRoute())
        assertEquals("pdf_tools", NavRoutes.PdfTools.createRoute())
        assertEquals("agenda", NavRoutes.Agenda.createRoute())
        assertEquals("secure_folder", NavRoutes.SecureFolder.createRoute())
        assertEquals("qr_creator", NavRoutes.QrCreator.createRoute())
    }

    @Test
    fun `los argumentos simples se agregan sin codificar`() {
        assertEquals(
            "converter?initialType=IMAGE_TO_PDF",
            NavRoutes.Converter.createRoute(initialType = "IMAGE_TO_PDF"),
        )
        assertEquals("pdf_tools?initialTool=OCR", NavRoutes.PdfTools.createRoute(initialTool = "OCR"))
        assertEquals("study?tab=2", NavRoutes.Study.createRoute(tab = 2))
        assertEquals("study?tab=0", NavRoutes.Study.createRoute())
    }

    @Test
    fun `las plantillas de ruta declaran sus argumentos opcionales como query`() {
        assertTrue(NavRoutes.Converter.route.startsWith("converter?initialType={initialType}"))
        assertTrue(NavRoutes.Study.route.contains("tab={tab}"))
        assertTrue(NavRoutes.Study.route.contains("openNoteId={openNoteId}"))
        assertEquals("agenda?openEventId={openEventId}", NavRoutes.Agenda.route)
        assertEquals("viewer/{documentId}", NavRoutes.Viewer.route)
    }
}
