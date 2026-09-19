package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Locale

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
        val end = LocalDateTime.of(2026, 12, 25, 10, 30)
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
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Ana Pérez;;;;\r\nFN:Ana Pérez\r\n" +
                "TEL:+573000000\r\nEMAIL:ana@ejemplo.com\r\nEND:VCARD",
            payload,
        )
    }

    @Test
    fun `Contacto sin telefono ni email omite esos campos`() {
        val payload = QrContactContent("Solo Nombre", "", "").toQrPayload()

        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Solo Nombre;;;;\r\nFN:Solo Nombre\r\nEND:VCARD", payload)
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
        val end = LocalDateTime.of(2026, 12, 25, 10, 30)
        val payload = QrEventContent("Reunión", "Oficina", start, end).toQrPayload()

        assertEquals(
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Reunión\r\n" +
                "DTSTART:20261225T090000\r\nDTEND:20261225T103000\r\nLOCATION:Oficina\r\n" +
                "END:VEVENT\r\nEND:VCALENDAR",
            payload,
        )
    }

    @Test
    fun `Evento sin lugar omite el campo LOCATION`() {
        val start = LocalDateTime.of(2026, 1, 1, 0, 0)
        val end = LocalDateTime.of(2026, 1, 1, 1, 0)
        val payload = QrEventContent("Año nuevo", "", start, end).toQrPayload()

        assertEquals(
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Año nuevo\r\n" +
                "DTSTART:20260101T000000\r\nDTEND:20260101T010000\r\nEND:VEVENT\r\nEND:VCALENDAR",
            payload,
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

    // Hallazgo real de la revisión de correctitud adversarial de este mismo
    // lote (2026-09-16): un backslash literal seguido de "n" (ej. SSID
    // "Test\network") se escapa a "Test\\network" (backslash duplicado) --
    // la versión anterior de unescapeReservedField() (6 replace()
    // independientes) confundía el segundo backslash del par + la "n"
    // siguiente con el escape de salto de línea, corrompiendo el valor.
    @Test
    fun `parseWifiPayload hace roundtrip con un backslash literal seguido de n`() {
        val original = QrWifiContent("Test\\network", "clave\\normal", QrWifiSecurity.WPA)

        val parsed = parseWifiPayload(original.toQrPayload())

        assertEquals(original, parsed)
    }

    // ── Parsers (hallazgo real #5, revisión general 2026-09-16) ──────────────
    // El Lector propio de DocuSmart no reconocía sus propios payloads
    // WIFI:/vCard/VEVENT -- estos tests cubren el roundtrip toQrPayload() ->
    // parse*Payload() y algunos casos borde de formato no generado por la app.

    @Test
    fun `parseWifiPayload hace roundtrip con toQrPayload`() {
        val original = QrWifiContent("Red;raro:nombre", "clave\"con,simbolos", QrWifiSecurity.WPA)
        val parsed = parseWifiPayload(original.toQrPayload())

        assertEquals(original, parsed)
    }

    @Test
    fun `parseWifiPayload sin contrasena devuelve seguridad NONE y password vacio`() {
        val parsed = parseWifiPayload(QrWifiContent("RedAbierta", "", QrWifiSecurity.NONE).toQrPayload())

        assertEquals(QrWifiContent("RedAbierta", "", QrWifiSecurity.NONE), parsed)
    }

    @Test
    fun `parseWifiPayload devuelve null si no empieza con WIFI`() {
        assertEquals(null, parseWifiPayload("no es un payload de wifi"))
    }

    @Test
    fun `parseVCardPayload hace roundtrip con toQrPayload`() {
        val original = QrContactContent("Ana Pérez", "+573000000", "ana@ejemplo.com")
        val parsed = parseVCardPayload(original.toQrPayload())

        assertEquals(original, parsed)
    }

    @Test
    fun `parseVCardPayload sin telefono ni email deja esos campos vacios`() {
        val parsed = parseVCardPayload(QrContactContent("Solo Nombre", "", "").toQrPayload())

        assertEquals(QrContactContent("Solo Nombre", "", ""), parsed)
    }

    @Test
    fun `parseVCardPayload devuelve null si no contiene BEGIN VCARD`() {
        assertEquals(null, parseVCardPayload("texto cualquiera"))
    }

    @Test
    fun `parseVEventPayload hace roundtrip con toQrPayload`() {
        val start = LocalDateTime.of(2026, 12, 25, 9, 0)
        val end = LocalDateTime.of(2026, 12, 25, 10, 30)
        val original = QrEventContent("Reunión", "Oficina", start, end)

        val parsed = parseVEventPayload(original.toQrPayload())

        assertEquals(original, parsed)
    }

    @Test
    fun `parseVEventPayload sin DTSTART devuelve null`() {
        val payload = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Sin fecha\nEND:VEVENT\nEND:VCALENDAR"

        assertEquals(null, parseVEventPayload(payload))
    }

    @Test
    fun `parseVEventPayload sin DTEND usa DTSTART como fin`() {
        val payload =
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Puntual\n" +
                "DTSTART:20260101T100000\nEND:VEVENT\nEND:VCALENDAR"

        val parsed = parseVEventPayload(payload)

        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), parsed?.start)
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), parsed?.end)
    }

    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): un
    // evento "de todo el día" (VALUE=DATE, sin hora) no matcheaba el
    // formato datetime esperado -- parseVEventPayload devolvía null y
    // "Agregar al calendario" no hacía nada, sin aviso.
    @Test
    fun `parseVEventPayload con evento de todo el dia -sin hora- usa medianoche`() {
        val payload =
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Feriado\n" +
                "DTSTART;VALUE=DATE:20261225\nDTEND;VALUE=DATE:20261226\nEND:VEVENT\nEND:VCALENDAR"

        val parsed = parseVEventPayload(payload)

        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), parsed?.start)
        assertEquals(LocalDateTime.of(2026, 12, 26, 0, 0), parsed?.end)
    }
}
