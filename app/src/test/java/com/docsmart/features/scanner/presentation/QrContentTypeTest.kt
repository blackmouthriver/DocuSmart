package com.docsmart.features.scanner.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Cubre el bug real encontrado en Escáner (docs/requirements/scanner.md):
 * la detección de esquema (http/https/mailto/tel) comparaba contra el
 * valor sin normalizar — un QR con esquema en mayúsculas (ej. "HTTPS://...",
 * generado así por algunas herramientas) se clasificaba como TEXT en vez
 * de URL, y el usuario no veía el botón de "abrir en el navegador".
 */
class QrContentTypeTest {

    @Test
    fun `URL en minusculas se detecta como URL`() {
        assertEquals(QrContentType.URL, detectQrContentType("https://docusmart.app"))
    }

    @Test
    fun `URL con esquema en mayusculas tambien se detecta como URL`() {
        assertEquals(QrContentType.URL, detectQrContentType("HTTPS://docusmart.app"))
        assertEquals(QrContentType.URL, detectQrContentType("HTTP://docusmart.app"))
    }

    @Test
    fun `URL con espacios alrededor se detecta como URL`() {
        assertEquals(QrContentType.URL, detectQrContentType("  https://docusmart.app  "))
    }

    @Test
    fun `URL que termina en extension de imagen se detecta como IMAGE`() {
        assertEquals(QrContentType.IMAGE, detectQrContentType("https://ejemplo.com/foto.PNG"))
        assertEquals(QrContentType.IMAGE, detectQrContentType("https://ejemplo.com/foto.jpg"))
    }

    @Test
    fun `URL que termina en extension de documento se detecta como DOCUMENT`() {
        assertEquals(QrContentType.DOCUMENT, detectQrContentType("https://ejemplo.com/reporte.PDF"))
        assertEquals(QrContentType.DOCUMENT, detectQrContentType("https://ejemplo.com/datos.xlsx"))
    }

    @Test
    fun `mailto se detecta como EMAIL sin importar mayusculas`() {
        assertEquals(QrContentType.EMAIL, detectQrContentType("mailto:hola@docusmart.app"))
        assertEquals(QrContentType.EMAIL, detectQrContentType("MAILTO:hola@docusmart.app"))
    }

    @Test
    fun `tel se detecta como PHONE sin importar mayusculas`() {
        assertEquals(QrContentType.PHONE, detectQrContentType("tel:+573001234567"))
        assertEquals(QrContentType.PHONE, detectQrContentType("TEL:+573001234567"))
    }

    @Test
    fun `content uri de imagen se detecta como IMAGE`() {
        assertEquals(QrContentType.IMAGE, detectQrContentType("content://media/external/images/1.png"))
    }

    @Test
    fun `content uri sin extension conocida se detecta como DOCUMENT`() {
        assertEquals(QrContentType.DOCUMENT, detectQrContentType("content://media/external/downloads/1"))
    }

    @Test
    fun `texto plano sin esquema reconocido se detecta como TEXT`() {
        assertEquals(QrContentType.TEXT, detectQrContentType("Hola, este es un QR de texto simple"))
    }
}
