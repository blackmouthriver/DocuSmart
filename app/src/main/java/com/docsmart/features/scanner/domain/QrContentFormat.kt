package com.docsmart.features.scanner.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * HU-43 (backlog UX 2026-08-30/09-14): 3 tipos de contenido nuevos para el
 * Creador de QR -- Wi-Fi, Contacto y Evento de calendario. Cada uno arma un
 * payload en el formato estándar correspondiente (no una extensión propia
 * de DocuSmart) para que el lector nativo de la cámara de Android/iOS lo
 * reconozca y ofrezca "Conectar"/"Agregar contacto"/"Agregar al calendario"
 * -- RNF1.
 */
enum class QrWifiSecurity { WPA, WEP, NONE }

data class QrWifiContent(
    val ssid: String,
    val password: String,
    val security: QrWifiSecurity
)

data class QrContactContent(
    val name: String,
    val phone: String,
    val email: String
)

data class QrEventContent(
    val title: String,
    val location: String,
    val start: LocalDateTime,
    val end: LocalDateTime
)

/**
 * Formato `WIFI:` -- sin RFC oficial, pero es el de-facto estándar que
 * reconocen tanto Google Lens/cámara de Android como la cámara de iOS
 * desde hace varias versiones. Sin contraseña (`NONE`) se omite el campo
 * `P` -- una red abierta no tiene nada que descifrar.
 */
fun QrWifiContent.toQrPayload(): String {
    val type = when (security) {
        QrWifiSecurity.WPA  -> "WPA"
        QrWifiSecurity.WEP  -> "WEP"
        QrWifiSecurity.NONE -> "nopass"
    }
    val passwordField = if (security == QrWifiSecurity.NONE) {
        ""
    } else {
        "P:${escapeWifiField(password)};"
    }
    return "WIFI:T:$type;S:${escapeWifiField(ssid)};$passwordField;"
}

/**
 * vCard 3.0 (RFC 2426) -- formato estándar que la cámara nativa reconoce
 * como "Agregar contacto". Solo se colecta un campo "Nombre" (no
 * separado en nombre/apellido), así que `N` lleva ese valor entero como
 * único componente -- sigue siendo un vCard válido.
 */
fun QrContactContent.toQrPayload(): String = buildString {
    append("BEGIN:VCARD\n")
    append("VERSION:3.0\n")
    append("N:${escapeVCardField(name)};;;;\n")
    append("FN:${escapeVCardField(name)}\n")
    if (phone.isNotBlank()) append("TEL:${escapeVCardField(phone)}\n")
    if (email.isNotBlank()) append("EMAIL:${escapeVCardField(email)}\n")
    append("END:VCARD")
}

// Bug real encontrado 2026-09-14 (revisión pre-fusión HU-43): sin Locale
// explícito, DateTimeFormatter.ofPattern() usa los dígitos del locale por
// defecto del dispositivo (p.ej. árabe/persa/bengalí tienen sus propios
// glifos numéricos) -- un DTSTART/DTEND con esos dígitos rompe el parseo
// iCalendar en CUALQUIER lector, sin importar el locale de quien escanea
// (RNF1: el formato debe ser estándar). Locale.US fuerza dígitos ASCII.
private val ICAL_DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)

/**
 * iCalendar `VEVENT` (RFC 5545) -- formato estándar que la cámara nativa
 * reconoce como "Agregar al calendario". Usa hora local "flotante" (sin
 * sufijo `Z`) en vez de convertir a UTC -- válido según el RFC y evita
 * tener que resolver la zona horaria del dispositivo que escanea, que
 * DocuSmart no puede conocer de antemano.
 */
fun QrEventContent.toQrPayload(): String = buildString {
    append("BEGIN:VCALENDAR\n")
    append("VERSION:2.0\n")
    append("BEGIN:VEVENT\n")
    append("SUMMARY:${escapeVCardField(title)}\n")
    append("DTSTART:${start.format(ICAL_DATE_TIME_FORMAT)}\n")
    append("DTEND:${end.format(ICAL_DATE_TIME_FORMAT)}\n")
    if (location.isNotBlank()) append("LOCATION:${escapeVCardField(location)}\n")
    append("END:VEVENT\n")
    append("END:VCALENDAR")
}

/**
 * Escape del formato `WIFI:` -- `\`, `;`, `,`, `:` y `"` son caracteres
 * reservados del propio formato (separadores de campo/valor); sin
 * escaparlos, un SSID o contraseña que los contenga rompe el parseo del
 * lector nativo.
 */
internal fun escapeWifiField(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")

/**
 * Escape de vCard 3.0 (RFC 2426 §5.1) / iCalendar (RFC 5545 §3.3.11) --
 * ambos formatos comparten el mismo conjunto de caracteres reservados:
 * `\`, `;`, `,` y salto de línea. A diferencia de `WIFI:`, acá NO se
 * escapa `:` -- no es un carácter reservado dentro de un valor en estos
 * dos formatos (solo separa propiedad:valor a nivel de línea), y
 * escaparlo igual arriesgaría que un lector estricto no reconozca la
 * secuencia `\:` como válida.
 */
internal fun escapeVCardField(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
