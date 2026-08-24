package com.docsmart.features.scanner.presentation

// ── Tipos de contenido QR detectado ──────────────────────────────────────────
internal enum class QrContentType {
    URL, IMAGE, DOCUMENT, EMAIL, PHONE, TEXT
}

// El esquema (http/https/mailto/tel) se compara en minúsculas: algunos
// generadores de QR codifican el esquema en mayúsculas (ej. "HTTPS://..."),
// y sin normalizar esos códigos se clasificaban como TEXT en vez de URL —
// el usuario no veía el botón de "abrir en el navegador".
@Suppress("CyclomaticComplexMethod")
internal fun detectQrContentType(value: String): QrContentType {
    val lower = value.trim().lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".webp") ||
                    lower.endsWith(".gif") -> QrContentType.IMAGE
            lower.endsWith(".pdf") || lower.endsWith(".docx") ||
                    lower.endsWith(".xlsx") || lower.endsWith(".pptx") ||
                    lower.endsWith(".txt") -> QrContentType.DOCUMENT
            else -> QrContentType.URL
        }
        lower.startsWith("content://") || lower.startsWith("file://") -> when {
            lower.contains(".jpg") || lower.contains(".jpeg") ||
                    lower.contains(".png") || lower.contains(".webp") -> QrContentType.IMAGE
            else -> QrContentType.DOCUMENT
        }
        lower.startsWith("mailto:") -> QrContentType.EMAIL
        lower.startsWith("tel:")    -> QrContentType.PHONE
        else                        -> QrContentType.TEXT
    }
}
