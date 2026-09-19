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
