package com.docsmart.features.scanner.domain

import java.time.LocalDateTime
import java.util.Locale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * HU-43 (backlog UX 2026-08-30/09-14): verifica que los payloads generados
 * cumplan el formato estándar exacto (WIFI:/vCard 3.0/iCalendar) que espera
 * el lector nativo de la cámara -- RNF1.
 */
class QrContentFormatTest {

    private lateinit var originalLocale: Locale

    @BeforeEach
    fun saveLocale() {
        originalLocale = Locale.getDefault()
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `Evento usa digitos ASCII en DTSTART-DTEND sin importar el locale por defecto`() {
        // Bug real encontrado 2026-09-14 (revisión pre-fusión): un
        // DateTimeFormatter sin Locale explícito usa los dígitos del locale
        // por defecto de la JVM/dispositivo (árabe/persa/bengalí tienen sus
        // propios glifos numéricos) -- rompería el parseo iCalendar en
        // cualquier lector, sin importar el locale de quien escanea.
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))

        val start = LocalDateTime.of(2026, 12, 25, 9, 0)
        val end   = LocalDateTime.of(2026, 12, 25, 10, 30)
        val payload = QrEventContent("Reunión", "Oficina", start, end).toQrPayload()

        assert(payload.contains("DTSTART:20261225T090000")) {
            "DTSTART debe usar dígitos ASCII incluso con locale ar-SA por defecto, fue: $payload"
        }
        assert(payload.contains("DTEND:20261225T103000")) {
            "DTEND debe usar dígitos ASCII incluso con locale ar-SA por defecto, fue: $payload"
        }
    }

    @Test
    fun `Wifi con WPA arma el payload WIFI con los 3 campos`() {
        val payload = QrWifiContent("MiCasa", "clave123", QrWifiSecurity.WPA).toQrPayload()

        assertEquals("WIFI:T:WPA;S:MiCasa;P:clave123;;", payload)
    }

    @Test
    fun `Wifi sin contraseña omite el campo P`() {
        val payload = QrWifiContent("RedAbierta", "", QrWifiSecurity.NONE).toQrPayload()

        assertEquals("WIFI:T:nopass;S:RedAbierta;;", payload)
    }

    @Test
    fun `Wifi escapa punto y coma y dos puntos en el SSID`() {
        val payload = QrWifiContent("Red;raro:nombre", "pass", QrWifiSecurity.WEP).toQrPayload()

        assertEquals("WIFI:T:WEP;S:Red\\;raro\\:nombre;P:pass;;", payload)
    }

    @Test
    fun `Contacto arma un vCard 3-0 valido con los 3 campos`() {
        val payload = QrContactContent("Ana Pérez", "+573000000", "ana@ejemplo.com").toQrPayload()

        assertEquals(
            "BEGIN:VCARD\nVERSION:3.0\nN:Ana Pérez;;;;\nFN:Ana Pérez\nTEL:+573000000\nEMAIL:ana@ejemplo.com\nEND:VCARD",
            payload
        )
    }

    @Test
    fun `Contacto sin telefono ni email omite esos campos`() {
        val payload = QrContactContent("Solo Nombre", "", "").toQrPayload()

        assertEquals("BEGIN:VCARD\nVERSION:3.0\nN:Solo Nombre;;;;\nFN:Solo Nombre\nEND:VCARD", payload)
    }

    @Test
    fun `Contacto no escapa dos puntos en el nombre`() {
        val payload = QrContactContent("Nombre: raro", "", "").toQrPayload()

        assert(payload.contains("N:Nombre: raro;;;;")) {
            "vCard no debe escapar ':' -- no es un caracter reservado en ese formato"
        }
    }

    @Test
    fun `Evento arma un VEVENT valido con fecha-hora en formato iCalendar`() {
        val start = LocalDateTime.of(2026, 12, 25, 9, 0)
        val end   = LocalDateTime.of(2026, 12, 25, 10, 30)
        val payload = QrEventContent("Reunión", "Oficina", start, end).toQrPayload()

        assertEquals(
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Reunión\n" +
                "DTSTART:20261225T090000\nDTEND:20261225T103000\nLOCATION:Oficina\n" +
                "END:VEVENT\nEND:VCALENDAR",
            payload
        )
    }

    @Test
    fun `Evento sin lugar omite el campo LOCATION`() {
        val start = LocalDateTime.of(2026, 1, 1, 0, 0)
        val end   = LocalDateTime.of(2026, 1, 1, 1, 0)
        val payload = QrEventContent("Año nuevo", "", start, end).toQrPayload()

        assertEquals(
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Año nuevo\n" +
                "DTSTART:20260101T000000\nDTEND:20260101T010000\nEND:VEVENT\nEND:VCALENDAR",
            payload
        )
    }

    @Test
    fun `escapeWifiField escapa los 5 caracteres reservados`() {
        assertEquals("a\\\\b\\;c\\,d\\:e\\\"f", escapeWifiField("a\\b;c,d:e\"f"))
    }

    @Test
    fun `escapeVCardField no toca los dos puntos`() {
        assertEquals("a\\\\b\\;c\\,d:e\\nf", escapeVCardField("a\\b;c,d:e\nf"))
    }
}
