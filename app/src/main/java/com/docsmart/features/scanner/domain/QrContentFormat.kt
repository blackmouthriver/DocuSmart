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

// Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): un
// evento "de todo el día" (DTSTART;VALUE=DATE:20261225, sin hora) -- formato
// habitual en invitaciones de calendario reales -- no matchea
// ICAL_DATE_TIME_FORMAT (que exige la 'T' y la hora), así que
// parseIcalDateTime() devolvía null y "Agregar al calendario" no hacía
// nada, sin aviso.
private val ICAL_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

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

/**
 * Reverso de escapeWifiField()/escapeVCardField(). Cubre la unión de ambos
 * conjuntos de caracteres escapados (WIFI: agrega `"` sobre lo que ya
 * escapa vCard/iCalendar) -- deshacer `\"` es un no-op inofensivo para
 * payloads vCard/iCalendar, que nunca la generan.
 *
 * Hallazgo real de la revisión de correctitud adversarial de este mismo
 * lote (2026-09-16): la versión anterior encadenaba 6 `replace()`
 * independientes (uno por secuencia), deshaciendo `\\` recién al final "a
 * propósito" -- pero un valor con un backslash literal seguido de la letra
 * `n` (ej. SSID `Test\network`) se escapa a `Test\\network` (backslash
 * duplicado), y esas pasadas independientes de `replace()` no ven esa
 * secuencia como una unidad: el paso `"\\n" -> "\n"` (salto de línea) se
 * ejecuta ANTES del paso `"\\\\" -> "\\"`, así que encuentra el segundo
 * backslash del par + la `n` siguiente y los interpreta como el escape de
 * salto de línea, corrompiendo el valor a `Test\` + salto de línea +
 * `etwork`. Una sola pasada con regex (alternativas mutuamente excluyentes:
 * el carácter después del backslash determina cuál aplica sin ambigüedad)
 * evita el problema porque cada secuencia de 2 caracteres se consume una
 * sola vez, en un solo recorrido, sin reinterpretar el resultado de un
 * reemplazo en una pasada posterior.
 */
private val UNESCAPE_RESERVED_FIELD_PATTERN = Regex("\\\\\\\\|\\\\;|\\\\,|\\\\:|\\\\\"|\\\\n")

internal fun unescapeReservedField(value: String): String =
    UNESCAPE_RESERVED_FIELD_PATTERN.replace(value) { match ->
        when (match.value) {
            "\\\\" -> "\\"
            "\\;"  -> ";"
            "\\,"  -> ","
            "\\:"  -> ":"
            "\\\"" -> "\""
            "\\n"  -> "\n"
            else   -> match.value
        }
    }

private fun extractWifiField(payload: String, key: String): String? =
    Regex("$key:((?:\\\\.|[^;])*);").find(payload)?.groupValues?.get(1)?.let(::unescapeReservedField)

/**
 * Hallazgo real de la revisión general 2026-09-16 (#5): el Lector propio de
 * DocuSmart no reconocía los payloads `WIFI:`/`BEGIN:VCARD`/`BEGIN:VEVENT`
 * que su propio Creador genera (HU-43) -- caían en tipo TEXT y mostraban el
 * string crudo. Estos tres parsers son el reverso exacto de los
 * `toQrPayload()` de arriba, para que el Lector ofrezca una acción útil en
 * vez del texto sin procesar.
 */
fun parseWifiPayload(payload: String): QrWifiContent? {
    val ssid = extractWifiField(payload, "S")
    if (!payload.trim().startsWith("WIFI:", ignoreCase = true) || ssid == null) return null
    val password = extractWifiField(payload, "P") ?: ""
    val security = when (extractWifiField(payload, "T")?.uppercase()) {
        "WPA", "WPA2" -> QrWifiSecurity.WPA
        "WEP"         -> QrWifiSecurity.WEP
        else          -> QrWifiSecurity.NONE
    }
    return QrWifiContent(ssid, password, security)
}

fun parseVCardPayload(payload: String): QrContactContent? {
    if (!payload.contains("BEGIN:VCARD", ignoreCase = true)) return null
    var name = ""
    var phone = ""
    var email = ""
    payload.lines().forEach { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) return@forEach
        val key = line.substring(0, idx).substringBefore(';').trim().uppercase()
        val value = unescapeReservedField(line.substring(idx + 1).trim())
        when (key) {
            "FN"    -> name = value
            "TEL"   -> if (phone.isBlank()) phone = value
            "EMAIL" -> if (email.isBlank()) email = value
        }
    }
    return if (name.isBlank() && phone.isBlank() && email.isBlank()) null
    else QrContactContent(name, phone, email)
}

private fun parseIcalDateTime(value: String): LocalDateTime? {
    val clean = value.removeSuffix("Z")
    return try {
        LocalDateTime.parse(clean, ICAL_DATE_TIME_FORMAT)
    } catch (e: java.time.format.DateTimeParseException) {
        try {
            java.time.LocalDate.parse(clean, ICAL_DATE_FORMAT).atStartOfDay()
        } catch (e2: java.time.format.DateTimeParseException) {
            null
        }
    }
}

fun parseVEventPayload(payload: String): QrEventContent? {
    if (!payload.contains("BEGIN:VEVENT", ignoreCase = true)) return null
    var title = ""
    var location = ""
    var start: LocalDateTime? = null
    var end: LocalDateTime? = null
    payload.lines().forEach { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) return@forEach
        val key = line.substring(0, idx).substringBefore(';').trim().uppercase()
        val value = unescapeReservedField(line.substring(idx + 1).trim())
        when (key) {
            "SUMMARY"  -> title = value
            "LOCATION" -> location = value
            "DTSTART"  -> start = parseIcalDateTime(value)
            "DTEND"    -> end = parseIcalDateTime(value)
        }
    }
    return start?.let { s -> QrEventContent(title, location, s, end ?: s) }
}
