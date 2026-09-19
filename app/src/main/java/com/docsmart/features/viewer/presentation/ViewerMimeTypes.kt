package com.docsmart.features.viewer.presentation

import com.docsmart.core.ui.components.DocumentType

private const val PDF_MIME = "application/pdf"
private const val JPEG_MIME = "image/jpeg"
private const val OCTET_STREAM_MIME = "application/octet-stream"

// Logica pura extraida de ViewerViewModel (sin cambios de comportamiento) para
// poder testearla en JVM sin mockear ContentResolver.
internal fun detectDocumentType(mimeType: String): DocumentType =
    when {
        mimeType.contains("image") -> DocumentType.IMAGE
        mimeType.contains("pdf") -> DocumentType.PDF
        mimeType.contains("word") ||
            mimeType.contains("msword") ||
            mimeType.contains("wordprocessingml") -> DocumentType.WORD
        mimeType.contains("excel") ||
            mimeType.contains("sheet") ||
            mimeType.contains("spreadsheet") -> DocumentType.EXCEL
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> DocumentType.POWERPOINT
        mimeType.contains("text") -> DocumentType.TEXT
        else -> DocumentType.PDF
    }

internal fun resolveMimeType(uriString: String): String? =
    when {
        uriString.contains("image") -> JPEG_MIME
        uriString.endsWith(".pdf", ignoreCase = true) -> PDF_MIME
        uriString.endsWith(".docx", ignoreCase = true) ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        uriString.endsWith(".doc", ignoreCase = true) -> "application/msword"
        uriString.endsWith(".xlsx", ignoreCase = true) ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        uriString.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
        uriString.endsWith(".jpg", ignoreCase = true) ||
            uriString.endsWith(".jpeg", ignoreCase = true) -> JPEG_MIME
        uriString.endsWith(".png", ignoreCase = true) -> "image/png"
        uriString.endsWith(".txt", ignoreCase = true) -> "text/plain"
        else -> null
    }

internal fun resolveMimeTypeByExtension(fileName: String): String? {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "pdf" -> PDF_MIME
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "ppt" -> "application/vnd.ms-powerpoint"
        "jpg", "jpeg" -> JPEG_MIME
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        else -> null
    }
}

// Decide el mime a mostrar: el del ContentResolver si es concreto; si es nulo,
// generico (octet-stream) o con comodin, se prefiere el deducido por extension.
internal fun chooseDisplayMimeType(
    fromResolver: String?,
    fromExtension: String?,
): String =
    when {
        fromResolver == null -> fromExtension ?: OCTET_STREAM_MIME
        fromResolver == OCTET_STREAM_MIME -> fromExtension ?: fromResolver
        fromResolver.contains("*") -> fromExtension ?: fromResolver
        else -> fromResolver
    }
