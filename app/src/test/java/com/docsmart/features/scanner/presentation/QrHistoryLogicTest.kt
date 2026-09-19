package com.docsmart.features.scanner.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class QrHistoryLogicTest {
    @Test
    fun `una entrada protegida no muestra contenido`() {
        historyPreviewOf("PROTECTED", "ENC:abc") shouldBe HistoryPreview.Protected
    }

    @Test
    fun `wifi muestra solo el SSID`() {
        val preview = historyPreviewOf("TEXT", "WIFI:T:WPA;S:CASA;P:secreto123;;")

        preview shouldBe HistoryPreview.Wifi("CASA")
    }

    @Test
    fun `wifi con esquema en minusculas tambien se enmascara`() {
        historyPreviewOf("TEXT", "wifi:T:WPA;S:CASA;P:secreto123;;") shouldBe HistoryPreview.Wifi("CASA")
    }

    @Test
    fun `una contrasena que termina en S dos puntos no se toma como SSID`() {
        // Regresion: el regex sin anclar tomaba "secreto" (parte de la clave) como SSID.
        val preview = historyPreviewOf("TEXT", "WIFI:T:WPA;P:claveS:secreto;S:CASA;;")

        preview shouldBe HistoryPreview.Wifi("CASA")
    }

    @Test
    fun `wifi sin SSID extraible no cae al contenido crudo`() {
        val preview = historyPreviewOf("TEXT", "WIFI:T:WPA;P:secreto123;;")

        preview shouldBe HistoryPreview.Wifi(null)
    }

    @Test
    fun `wifi con SSID escapado se desescapa`() {
        historyPreviewOf("WIFI", "WIFI:T:WPA;S:Casa\\;Bruja;P:x;;") shouldBe HistoryPreview.Wifi("Casa;Bruja")
    }

    @Test
    fun `contacto muestra solo el nombre`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:Ana Perez\nTEL:3001234567\nEMAIL:ana@x.com\nEND:VCARD"

        historyPreviewOf("CONTACT", vcard) shouldBe HistoryPreview.Contact("Ana Perez")
    }

    @Test
    fun `contacto solo con nombre estructurado usa nombre y apellido`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nN:Perez;Ana;;;\nEND:VCARD"

        extractContactName(vcard) shouldBe "Ana Perez"
    }

    @Test
    fun `contacto sin nombre no expone telefono ni email`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nTEL:3001234567\nEND:VCARD"

        historyPreviewOf("TEXT", vcard) shouldBe HistoryPreview.Contact(null)
    }

    @Test
    fun `contacto con esquema en minusculas se enmascara`() {
        val vcard = "begin:vcard\nFN:Ana\nend:vcard"

        historyPreviewOf("TEXT", vcard) shouldBe HistoryPreview.Contact("Ana")
    }

    @Test
    fun `otro contenido se muestra tal cual`() {
        historyPreviewOf("URL", "https://example.com") shouldBe HistoryPreview.Plain("https://example.com")
        historyPreviewOf("TEXT", "hola") shouldBe HistoryPreview.Plain("hola")
    }

    @Test
    fun `extractWifiSsid devuelve nulo si no es un payload wifi`() {
        extractWifiSsid("hola mundo") shouldBe null
    }
}
