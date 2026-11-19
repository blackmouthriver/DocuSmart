package com.docsmart.core.navegation

import android.content.Intent
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.pdftools.presentation.PdfTool
import com.docsmart.webLinkRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** W-18 (App Links): enlaces de docusmart.online que abren una herramienta de la app. */
class WebToolLinksTest {
    @Test
    fun `una herramienta en español abre la herramienta de la app`() {
        assertEquals("pdf_tools?initialTool=MERGE", routeForWebLink("https", "docusmart.online", "/unir-pdf"))
        assertEquals(
            "converter?initialType=IMAGE_TO_PDF",
            routeForWebLink("https", "docusmart.online", "/imagen-a-pdf"),
        )
        assertEquals(NavRoutes.Scanner.route, routeForWebLink("https", "docusmart.online", "/escanear-documento"))
    }

    @Test
    fun `la misma herramienta en otros idiomas va al mismo lugar`() {
        val merge = "pdf_tools?initialTool=MERGE"
        assertEquals(merge, routeForWebLink("https", "docusmart.online", "/en/merge-pdf"))
        assertEquals(merge, routeForWebLink("https", "docusmart.online", "/de/pdf-zusammenfuegen"))
        assertEquals(merge, routeForWebLink("https", "docusmart.online", "/ja/merge-pdf"))
    }

    @Test
    fun `tolera barra final, mayúsculas del host y la extensión html`() {
        val merge = "pdf_tools?initialTool=MERGE"
        assertEquals(merge, routeForWebLink("https", "DocuSmart.Online", "/unir-pdf/"))
        assertEquals(merge, routeForWebLink("https", "docusmart.online", "/unir-pdf.html"))
    }

    @Test
    fun `no toca otros enlaces del sitio ni otros sitios`() {
        assertNull(routeForWebLink("https", "docusmart.online", "/"))
        assertNull(routeForWebLink("https", "docusmart.online", "/privacidad"))
        assertNull(routeForWebLink("https", "docusmart.online", "/guias/unir-pdf-en-el-celular"))
        assertNull(routeForWebLink("https", "docusmart.online", "/comprimir-imagen")) // solo en la web
        assertNull(routeForWebLink("https", "evil.example", "/unir-pdf"))
        assertNull(routeForWebLink("http", "docusmart.online", "/unir-pdf"))
        assertNull(routeForWebLink("content", "docusmart.online", "/unir-pdf"))
        assertNull(routeForWebLink("https", "docusmart.online", null))
    }

    @Test
    fun `solo un VIEW se trata como enlace`() {
        assertNotNull(webLinkRoute(Intent.ACTION_VIEW, "https", "docusmart.online", "/unir-pdf"))
        assertNull(webLinkRoute(Intent.ACTION_SEND, "https", "docusmart.online", "/unir-pdf"))
        assertNull(webLinkRoute(null, "https", "docusmart.online", "/unir-pdf"))
    }

    @Test
    fun `cada destino apunta a una herramienta o tipo de conversión que existe`() {
        for ((tool, route) in WebToolLinks.ROUTES) {
            when {
                route.startsWith("pdf_tools?initialTool=") ->
                    PdfTool.valueOf(route.substringAfter("=")).also { assertTrue(it != PdfTool.NONE, tool) }
                route.startsWith("converter?initialType=") -> ConversionType.valueOf(route.substringAfter("="))
                else ->
                    assertTrue(
                        route in setOf(NavRoutes.Scanner.route, NavRoutes.PdfPassword.route, "qr_creator"),
                        "$tool -> $route",
                    )
            }
        }
        for (tool in WebToolLinks.PATHS.values) assertTrue(tool in WebToolLinks.ROUTES, tool)
    }

    @Test
    fun `el intent-filter del manifiesto declara exactamente las mismas rutas`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val block = manifest.substringAfter("android:autoVerify=\"true\"").substringBefore("</intent-filter>")
        val declared = Regex("android:path=\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(WebToolLinks.PATHS.keys, declared)
        assertTrue(block.contains("android:host=\"docusmart.online\""))
        assertTrue(block.contains("android:scheme=\"https\""))
        assertTrue(block.contains("android.intent.category.BROWSABLE"))
    }
}
