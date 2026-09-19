package com.docsmart.features.scanner.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    val security: QrWifiSecurity,
)

data class QrContactContent(
    val name: String,
    val phone: String,
    val email: String,
)

data class QrEventContent(
    val title: String,
    val location: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/**
 * Formato `WIFI:` -- sin RFC oficial, pero es el de-facto estándar que
 * reconocen tanto Google Lens/cámara de Android como la cámara de iOS
 * desde hace varias versiones. Sin contraseña (`NONE`) se omite el campo
 * `P` -- una red abierta no tiene nada que descifrar.
 */
fun QrWifiContent.toQrPayload(): String {
    val type =
        when (security) {
            QrWifiSecurity.WPA -> "WPA"
            QrWifiSecurity.WEP -> "WEP"
            QrWifiSecurity.NONE -> "nopass"
        }
    val passwordField =
        if (security == QrWifiSecurity.NONE) {
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
fun QrContactContent.toQrPayload(): String =
    buildString {
        // Ronda 16: RFC 2426 §2.1 exige CRLF como fin de línea (antes LF a
        // secas; los lectores lo toleraban, los estrictos no). Además se
        // recortan los espacios de los campos (el teclado suele dejar un
        // espacio final tras el autocompletado del nombre).
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val cleanEmail = email.trim()
        append("BEGIN:VCARD$CRLF")
        append("VERSION:3.0$CRLF")
        append("N:${escapeVCardField(cleanName)};;;;$CRLF")
        append("FN:${escapeVCardField(cleanName)}$CRLF")
        if (cleanPhone.isNotEmpty()) append("TEL:${escapeVCardField(cleanPhone)}$CRLF")
        if (cleanEmail.isNotEmpty()) append("EMAIL:${escapeVCardField(cleanEmail)}$CRLF")
        append("END:VCARD")
    }

private const val CRLF = "\r\n"

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
fun QrEventContent.toQrPayload(): String =
    buildString {
        val cleanLocation = location.trim()
        append("BEGIN:VCALENDAR$CRLF")
        append("VERSION:2.0$CRLF")
        append("BEGIN:VEVENT$CRLF")
        append("SUMMARY:${escapeVCardField(title.trim())}$CRLF")
        append("DTSTART:${start.format(ICAL_DATE_TIME_FORMAT)}$CRLF")
        append("DTEND:${end.format(ICAL_DATE_TIME_FORMAT)}$CRLF")
        if (cleanLocation.isNotEmpty()) append("LOCATION:${escapeVCardField(cleanLocation)}$CRLF")
        append("END:VEVENT$CRLF")
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
        .replace("\r\n", "\n")
        .replace("\r", "\n")
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
private val UNESCAPE_RESERVED_FIELD_PATTERN = Regex("\\\\\\\\|\\\\;|\\\\,|\\\\:|\\\\\"|\\\\[nN]")

internal fun unescapeReservedField(value: String): String =
    UNESCAPE_RESERVED_FIELD_PATTERN.replace(value) { match ->
        when (match.value) {
            "\\\\" -> "\\"
            "\\;" -> ";"
            "\\," -> ","
            "\\:" -> ":"
            "\\\"" -> "\""
            "\\n", "\\N" -> "\n"
            else -> match.value
        }
    }

// Ronda 16: el regex anterior (`S:...;` sin ancla) buscaba la clave en
// cualquier parte del payload. Un WIFI:T:WPA;S:CASA;P:x;; funcionaba, pero un
// campo H:/E:/I: de un generador de terceros, o un valor que terminara en la
// letra de otra clave (`...;P:xS:`), podía tomar el campo equivocado. Se ancla
// a inicio de campo (justo tras `WIFI:` o tras un `;` no escapado).
private fun extractWifiField(
    payload: String,
    key: String,
): String? {
    val trimmed = payload.trim()
    val body = if (trimmed.startsWith("WIFI:", ignoreCase = true)) trimmed.substring("WIFI:".length) else trimmed
    return Regex("(?:^|;)$key:((?:\\\\.|[^;])*)(?:;|$)")
        .find(body)
        ?.groupValues
        ?.get(1)
        ?.let(::stripWifiQuotes)
        ?.let(::unescapeReservedField)
}

// Los valores que son solo hex se envuelven en comillas dobles en el formato
// (para distinguir "cadena" de "hex"): las comillas no son parte del valor.
private fun stripWifiQuotes(value: String): String =
    if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value.substring(1, value.length - 1)
    } else {
        value
    }

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
    val type = extractWifiField(payload, "T")?.uppercase().orEmpty()
    val security =
        when (type) {
            "WEP" -> QrWifiSecurity.WEP
            "", "NOPASS" -> if (password.isEmpty()) QrWifiSecurity.NONE else QrWifiSecurity.WPA
            // WPA, WPA2, WPA3, SAE, WPA2-EAP... todo lo que no sea WEP/abierta
            // se conecta con una clave: antes "SAE" (WPA3, lo que emite Android
            // moderno) caía en NONE y se perdía la seguridad de la red.
            else -> QrWifiSecurity.WPA
        }
    return QrWifiContent(ssid, password, security)
}

// RFC 6350 §3.2 / RFC 5545 §3.1: una línea larga se "pliega" con CRLF + un
// espacio o tab al inicio de la continuación. Sin desplegar, los QR de
// contactos/eventos de terceros con líneas largas (frecuente en NOTE/ADR)
// dejaban un valor cortado.
private val FOLDED_LINE_PATTERN = Regex("\r?\n[ \t]")

private fun unfoldedLines(payload: String): List<String> = payload.replace(FOLDED_LINE_PATTERN, "").lines()

// Nombre de propiedad sin parámetros ni prefijo de grupo: `item1.TEL;TYPE=CELL`
// (Apple/iOS agrega el grupo) -> `TEL`.
private fun propertyKey(rawKey: String): String =
    rawKey
        .substringBefore(';')
        .trim()
        .substringAfterLast('.')
        .uppercase()

// vCard: `N:Familia;Nombre;Segundo;Prefijo;Sufijo` -> "Nombre Familia".
// Solo se usa cuando el contacto de un tercero no trae FN (FN es obligatorio
// en 3.0 pero los vCard 2.1 y algunos generadores lo omiten).
private fun structuredNameToDisplay(value: String): String {
    val parts = value.split(';')
    val family = parts.getOrElse(0) { "" }.trim()
    val given = parts.getOrElse(1) { "" }.trim()
    return listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
}

fun parseVCardPayload(payload: String): QrContactContent? {
    if (!payload.contains("BEGIN:VCARD", ignoreCase = true)) return null
    var name = ""
    var structuredName = ""
    var phone = ""
    var email = ""
    unfoldedLines(payload).forEach { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) return@forEach
        val key = propertyKey(line.substring(0, idx))
        val rawValue = line.substring(idx + 1).trim()
        when (key) {
            "FN" -> name = unescapeReservedField(rawValue)
            "N" -> structuredName = structuredNameToDisplay(rawValue).let(::unescapeReservedField)
            "TEL" ->
                if (phone.isBlank()) {
                    phone = unescapeReservedField(rawValue).removePrefixIgnoreCase("tel:").trim()
                }
            "EMAIL" -> if (email.isBlank()) email = unescapeReservedField(rawValue)
        }
    }
    val finalName = name.ifBlank { structuredName }
    return if (finalName.isBlank() && phone.isBlank() && email.isBlank()) {
        null
    } else {
        QrContactContent(finalName, phone, email)
    }
}

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

/**
 * Ronda 16: un valor con sufijo `Z` es UTC (RFC 5545 §3.3.5) y antes se
 * interpretaba como hora local del dispositivo (se quitaba la `Z` sin
 * convertir): un evento de un tercero a las 15:00Z aparecía a las 15:00 en
 * cualquier zona en vez de las 10:00 de Bogotá. Ahora se convierte a la zona
 * de [zone]. Los valores flotantes (sin `Z`, como los que genera la propia
 * app) y los de solo fecha no se tocan.
 */
internal fun parseIcalDateTime(
    value: String,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDateTime? {
    val trimmedValue = value.trim()
    val isUtc = trimmedValue.endsWith("Z", ignoreCase = true)
    val clean = trimmedValue.dropLast(if (isUtc) 1 else 0)
    return try {
        val parsed = LocalDateTime.parse(clean, ICAL_DATE_TIME_FORMAT)
        if (isUtc) parsed.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime() else parsed
    } catch (e: java.time.format.DateTimeParseException) {
        try {
            java.time.LocalDate
                .parse(clean, ICAL_DATE_FORMAT)
                .atStartOfDay()
        } catch (e2: java.time.format.DateTimeParseException) {
            null
        }
    }
}

fun parseVEventPayload(
    payload: String,
    zone: ZoneId = ZoneId.systemDefault(),
): QrEventContent? {
    if (!payload.contains("BEGIN:VEVENT", ignoreCase = true)) return null
    var title = ""
    var location = ""
    var start: LocalDateTime? = null
    var end: LocalDateTime? = null
    unfoldedLines(payload).forEach { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) return@forEach
        val key = propertyKey(line.substring(0, idx))
        val value = unescapeReservedField(line.substring(idx + 1).trim())
        when (key) {
            "SUMMARY" -> title = value
            "LOCATION" -> location = value
            "DTSTART" -> start = parseIcalDateTime(value, zone)
            "DTEND" -> end = parseIcalDateTime(value, zone)
        }
    }
    return start?.let { s -> QrEventContent(title, location, s, end ?: s) }
}

private val URL_WITH_SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val WHITESPACE_PATTERN = Regex("\\s+")

/**
 * Ronda 16: el creador decidía si anteponer `https://` con
 * `startsWith("http")`, así que "httpbin.org" (o cualquier dominio que empiece
 * con "http") quedaba SIN esquema y "www.ejemplo.com " con un espacio final
 * generaba `https://www.ejemplo.com ` (URL inválida). Ahora: se recortan los
 * espacios, se antepone `https://` solo si NO hay ya un esquema `xxx://` y los
 * espacios internos se codifican como `%20`.
 */
fun buildUrlQrPayload(input: String): String {
    val trimmed = input.trim().replace(WHITESPACE_PATTERN, "%20")
    return if (URL_WITH_SCHEME_PATTERN.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
}

/** `mailto:` -- sin duplicar el esquema si el usuario ya lo escribió y sin espacios crudos. */
fun buildEmailQrPayload(input: String): String {
    val trimmed = input.trim().replace(WHITESPACE_PATTERN, "%20")
    val address = if (trimmed.startsWith("mailto:", ignoreCase = true)) trimmed.substring("mailto:".length) else trimmed
    return "mailto:$address"
}

/**
 * `tel:` (RFC 3966) -- el teclado de teléfono permite espacios, guiones y
 * paréntesis ("+57 (300) 123-4567"); los lectores nativos marcan el número
 * tal cual, así que se dejan solo dígitos, `*`, `#` y un `+` inicial. Si tras
 * limpiar no queda nada (el usuario escribió letras), se conserva lo escrito.
 */
fun buildPhoneQrPayload(input: String): String {
    val trimmed = input.trim()
    val number = if (trimmed.startsWith("tel:", ignoreCase = true)) trimmed.substring("tel:".length).trim() else trimmed
    val cleaned =
        buildString {
            number.forEachIndexed { index, ch ->
                if (ch.isPhoneQrChar(isFirst = index == 0)) {
                    append(ch)
                }
            }
        }
    return "tel:${cleaned.ifEmpty { number }}"
}

private fun Char.isPhoneQrChar(isFirst: Boolean): Boolean {
    return this in '0'..'9' || this == '*' || this == '#' || (this == '+' && isFirst)
}

private const val NON_ASCII_START = 128

// ── Codificación ZXing (pura, sin Bitmap) ────────────────────────────────────

private const val QR_QUIET_ZONE_MODULES = 4
private const val QR_TARGET_SIZE_PX = 512
private const val QR_MIN_MODULE_PX = 8

/**
 * Matriz de módulos (incluida la zona de silencio de 4 módulos que exige
 * ISO 18004) para [content], o null si no cabe en ningún QR.
 *
 * - Sin logo se usa corrección de errores M (~15%, más robusta al impreso
 *   que la L por defecto de ZXing) y se cae a L solo si no entra.
 * - Con logo se usa H (~30%, el logo tapa ~7% del área) y se cae a Q.
 * - CHARACTER_SET=UTF-8 solo si hay caracteres no ASCII: con el hint ZXing
 *   agrega una cabecera ECI que algunos lectores viejos no entienden, y para
 *   ASCII puro es innecesaria. Sin el hint los no-Latin-1 se convertían en
 *   "?" en silencio (bug ya corregido en la ronda 5 de auditoría).
 */
fun encodeQrMatrix(
    content: String,
    hasLogo: Boolean,
): BitMatrix? {
    val levels =
        if (hasLogo) {
            listOf(ErrorCorrectionLevel.H, ErrorCorrectionLevel.Q)
        } else {
            listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)
        }
    val needsUtf8 = content.any { it.code >= NON_ASCII_START }
    for (level in levels) {
        val hints =
            buildMap<EncodeHintType, Any> {
                if (needsUtf8) put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, level)
                put(EncodeHintType.MARGIN, QR_QUIET_ZONE_MODULES)
            }
        try {
            // width/height 0: ZXing devuelve la matriz sin escalar (1 px por
            // módulo), el escalado entero lo hace qrPixelScale()/renderQrPixels().
            return MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: WriterException) {
            // Contenido demasiado largo para este nivel: se prueba el siguiente.
            continue
        }
    }
    return null
}

/**
 * Píxeles por módulo: apunta a ~512 px de lado, pero nunca menos de 8 px por
 * módulo -- con el 512 fijo anterior un QR denso (texto largo/contraseña) caía
 * a 2-3 px por módulo, ilegible al imprimirlo o al reescalarlo.
 */
fun qrPixelScale(moduleCount: Int): Int = maxOf(QR_MIN_MODULE_PX, QR_TARGET_SIZE_PX / moduleCount.coerceAtLeast(1))

/** ARGB por píxel de la matriz escalada [scale] veces; lado = `matrix.width * scale`. */
fun renderQrPixels(
    matrix: BitMatrix,
    scale: Int,
    moduleColor: Int,
    backgroundColor: Int,
): IntArray {
    val side = matrix.width * scale
    val pixels = IntArray(side * side)
    for (y in 0 until side) {
        val rowStart = y * side
        val moduleY = y / scale
        for (x in 0 until side) {
            pixels[rowStart + x] = if (matrix[x / scale, moduleY]) moduleColor else backgroundColor
        }
    }
    return pixels
}
