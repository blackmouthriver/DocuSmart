package com.docsmart.features.scanner.presentation

// ── Tipos de contenido QR detectado ──────────────────────────────────────────
internal enum class QrContentType {
    URL,
    IMAGE,
    DOCUMENT,
    EMAIL,
    PHONE,
    TEXT,
    WIFI,
    CONTACT,
    EVENT,
}

// El esquema (http/https/mailto/tel) se compara en minúsculas: algunos
// generadores de QR codifican el esquema en mayúsculas (ej. "HTTPS://..."),
// y sin normalizar esos códigos se clasificaban como TEXT en vez de URL —
// el usuario no veía el botón de "abrir en el navegador".
//
// Hallazgo real de la revisión general 2026-09-16 (#5): WIFI:/BEGIN:VCARD/
// BEGIN:VEVENT (los 3 formatos que el propio Creador genera desde HU-43,
// ver QrContentFormat.kt) caían en TEXT -- se detectan acá con el mismo
// criterio de prefijo que los demás tipos, mayúsculas o minúsculas.
@Suppress("CyclomaticComplexMethod")
internal fun detectQrContentType(value: String): QrContentType {
    val trimmed = value.trim()
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") ->
            when {
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".webp") ||
                    lower.endsWith(".gif") -> QrContentType.IMAGE
                lower.endsWith(".pdf") || lower.endsWith(".docx") ||
                    lower.endsWith(".xlsx") || lower.endsWith(".pptx") ||
                    lower.endsWith(".txt") -> QrContentType.DOCUMENT
                else -> QrContentType.URL
            }
        lower.startsWith("content://") || lower.startsWith("file://") ->
            when {
                lower.contains(".jpg") || lower.contains(".jpeg") ||
                    lower.contains(".png") || lower.contains(".webp") -> QrContentType.IMAGE
                else -> QrContentType.DOCUMENT
            }
        lower.startsWith("mailto:") -> QrContentType.EMAIL
        lower.startsWith("tel:") -> QrContentType.PHONE
        lower.startsWith("wifi:") -> QrContentType.WIFI
        trimmed.contains("BEGIN:VCARD", ignoreCase = true) -> QrContentType.CONTACT
        trimmed.contains("BEGIN:VEVENT", ignoreCase = true) -> QrContentType.EVENT
        else -> QrContentType.TEXT
    }
}

// Esquemas que "Abrir" puede lanzar con ACTION_VIEW. Todo lo demás (intent:,
// javascript:, file:, market:, sms:, esquemas propios de apps...) llega a la UI
// como TEXT y nunca debería alcanzar openUrl(); esta lista es la segunda línea
// de defensa por si un caller nuevo lo hace.
private val OPENABLE_SCHEMES = setOf("http", "https", "mailto", "tel")

/**
 * Ronda 16: Android resuelve el Intent por esquema en minúsculas -- un QR
 * "HTTPS://sitio.com" se detecta bien como URL (detectQrContentType compara en
 * minúsculas) pero `Uri.parse("HTTPS://...")` conserva las mayúsculas y
 * ACTION_VIEW no encuentra ningún navegador ("No se encontró una app...").
 * Recorta espacios y baja el esquema a minúsculas; el resto queda intacto.
 */
internal fun normalizeUriScheme(value: String): String {
    val trimmed = value.trim()
    val colon = trimmed.indexOf(':')
    return if (colon > 0) trimmed.substring(0, colon).lowercase() + trimmed.substring(colon) else trimmed
}

internal fun isOpenableScheme(value: String): Boolean {
    val trimmed = value.trim()
    val colon = trimmed.indexOf(':')
    return colon > 0 && trimmed.substring(0, colon).lowercase() in OPENABLE_SCHEMES
}

/** Solo http/https se puede descargar de red al "Cargar imagen" (no file://, content://, ftp://...). */
internal fun isRemoteHttpUrl(value: String): Boolean {
    val lower = value.trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

/** Autoridad de un `content://autoridad/ruta` (null si no es content://). */
internal fun contentUriAuthority(value: String): String? {
    val trimmed = value.trim()
    if (!trimmed.startsWith("content://", ignoreCase = true)) return null
    return trimmed
        .substring("content://".length)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .lowercase()
}

/**
 * Un QR de Documento/Imagen de un tercero puede traer un `content://` que
 * apunte a un proveedor de DocuSmart (ej. `com.docsmart.fileprovider`).
 * openDocumentExternally concede FLAG_GRANT_READ_URI_PERMISSION, así que
 * abrirlo le daría a la app elegida en el chooser lectura sobre datos
 * propios de la app. Se rechaza cualquier autoridad del propio paquete.
 */
internal fun isOwnContentUri(
    value: String,
    packageName: String,
): Boolean {
    val authority = contentUriAuthority(value) ?: return false
    val pkg = packageName.lowercase()
    return authority == pkg || authority.startsWith("$pkg.")
}
