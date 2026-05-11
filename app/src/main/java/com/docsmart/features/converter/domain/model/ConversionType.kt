package com.docsmart.features.converter.domain.model

enum class ConversionType(
    val label: String,
    val fromFormat: String,
    val toFormat: String,
    val fromExtensions: List<String>,
    val outputExtension: String,
    val isPremium: Boolean = false
) {
    IMAGE_TO_PDF(
        label = "Imagen → PDF",
        fromFormat = "Imagen",
        toFormat = "PDF",
        fromExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp"),
        outputExtension = "pdf"
    ),
    IMAGE_TO_JPG(
        label = "Imagen → JPG",
        fromFormat = "Imagen",
        toFormat = "JPG",
        fromExtensions = listOf("png", "webp", "bmp"),
        outputExtension = "jpg"
    ),
    IMAGE_TO_PNG(
        label = "Imagen → PNG",
        fromFormat = "Imagen",
        toFormat = "PNG",
        fromExtensions = listOf("jpg", "jpeg", "webp", "bmp"),
        outputExtension = "png"
    ),
    PDF_TO_IMAGE(
        label = "PDF → Imagen",
        fromFormat = "PDF",
        toFormat = "Imagen",
        fromExtensions = listOf("pdf"),
        outputExtension = "jpg"
    ),
    PDF_TO_TXT(
        label = "PDF → Texto",
        fromFormat = "PDF",
        toFormat = "TXT",
        fromExtensions = listOf("pdf"),
        outputExtension = "txt"
    ),
    WORD_TO_PDF(
        label = "Word → PDF",
        fromFormat = "Word",
        toFormat = "PDF",
        fromExtensions = listOf("doc", "docx"),
        outputExtension = "pdf"
    ),
    WORD_TO_TXT(
        label = "Word → Texto",
        fromFormat = "Word",
        toFormat = "TXT",
        fromExtensions = listOf("doc", "docx"),
        outputExtension = "txt"
    ),
    EXCEL_TO_PDF(
        label = "Excel → PDF",
        fromFormat = "Excel",
        toFormat = "PDF",
        fromExtensions = listOf("xls", "xlsx"),
        outputExtension = "pdf"
    ),
    EXCEL_TO_CSV(
        label = "Excel → CSV",
        fromFormat = "Excel",
        toFormat = "CSV",
        fromExtensions = listOf("xls", "xlsx"),
        outputExtension = "csv"
    ),
    PPT_TO_PDF(
        label = "PPT → PDF",
        fromFormat = "PowerPoint",
        toFormat = "PDF",
        fromExtensions = listOf("ppt", "pptx"),
        outputExtension = "pdf"
    )
}

// ── Categoría de cada tipo ────────────────────────────
fun ConversionType.getCategoryLabel(): String {
    return when (this) {
        ConversionType.IMAGE_TO_PDF,
        ConversionType.IMAGE_TO_JPG,
        ConversionType.IMAGE_TO_PNG -> "Imagen"

        ConversionType.PDF_TO_IMAGE,
        ConversionType.PDF_TO_TXT -> "PDF"

        ConversionType.WORD_TO_PDF,
        ConversionType.WORD_TO_TXT -> "Word"

        ConversionType.EXCEL_TO_PDF,
        ConversionType.EXCEL_TO_CSV -> "Excel"

        ConversionType.PPT_TO_PDF -> "PowerPoint"
    }
}