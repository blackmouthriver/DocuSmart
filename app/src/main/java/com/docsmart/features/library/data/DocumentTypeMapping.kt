package com.docsmart.features.library.data

import com.docsmart.core.ui.components.DocumentType

// Logica pura extraida de DocumentRepository (sin cambios de comportamiento)
// para poder testearla en JVM.
internal fun mimeToDocumentType(
    mime: String,
    name: String,
): DocumentType =
    when {
        mime.contains("pdf") -> DocumentType.PDF
        mime.contains("word") || mime.contains("msword") -> DocumentType.WORD
        mime.contains("excel") || mime.contains("sheet") -> DocumentType.EXCEL
        mime.contains("powerpoint") || mime.contains("presentation") -> DocumentType.POWERPOINT
        mime.contains("image") -> DocumentType.IMAGE
        mime.contains("text") -> DocumentType.TEXT
        else -> extensionToDocumentType(name.substringAfterLast("."))
    }

internal fun extensionToDocumentType(ext: String): DocumentType =
    when (ext.lowercase()) {
        "pdf" -> DocumentType.PDF
        "doc", "docx" -> DocumentType.WORD
        "xls", "xlsx" -> DocumentType.EXCEL
        "ppt", "pptx" -> DocumentType.POWERPOINT
        "jpg", "jpeg", "png", "webp", "gif" -> DocumentType.IMAGE
        "txt", "md" -> DocumentType.TEXT
        "zip", "rar", "7z" -> DocumentType.ZIP
        else -> DocumentType.PDF
    }

// Unidad de tamano elegida por formatSize() del repositorio.
internal enum class SizeUnit { BYTES, KB, MB }

internal fun sizeUnitFor(bytes: Long): SizeUnit =
    when {
        bytes < 1024 -> SizeUnit.BYTES
        bytes < 1024 * 1024 -> SizeUnit.KB
        else -> SizeUnit.MB
    }
