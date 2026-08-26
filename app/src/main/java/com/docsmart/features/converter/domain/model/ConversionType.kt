package com.docsmart.features.converter.domain.model

enum class ConversionType(
    val fromFormat     : String,
    val toFormat       : String,
    val fromExtensions : List<String>,
    val outputExtension: String,
    val isPremium      : Boolean = false
) {
    // ── Imagen ────────────────────────────────────────
    IMAGE_TO_PDF(
        fromFormat      = "Imagen",
        toFormat        = "PDF",
        fromExtensions  = listOf("jpg", "jpeg", "png", "webp", "bmp"),
        outputExtension = "pdf"
    ),
    IMAGE_TO_JPG(
        fromFormat      = "Imagen",
        toFormat        = "JPG",
        fromExtensions  = listOf("png", "webp", "bmp"),
        outputExtension = "jpg"
    ),
    IMAGE_TO_PNG(
        fromFormat      = "Imagen",
        toFormat        = "PNG",
        fromExtensions  = listOf("jpg", "jpeg", "webp", "bmp"),
        outputExtension = "png"
    ),
    IMAGE_TO_WEBP(
        fromFormat      = "Imagen",
        toFormat        = "WebP",
        fromExtensions  = listOf("jpg", "jpeg", "png", "bmp"),
        outputExtension = "webp"
    ),
    IMAGE_TO_BMP(
        fromFormat      = "Imagen",
        toFormat        = "BMP",
        fromExtensions  = listOf("jpg", "jpeg", "png", "webp"),
        outputExtension = "bmp"
    ),

    // ── PDF ───────────────────────────────────────────
    PDF_TO_IMAGE(
        fromFormat      = "PDF",
        toFormat        = "Imagen",
        fromExtensions  = listOf("pdf"),
        outputExtension = "jpg"
    ),
    PDF_TO_TXT(
        fromFormat      = "PDF",
        toFormat        = "TXT",
        fromExtensions  = listOf("pdf"),
        outputExtension = "txt"
    ),
    PDF_TO_WORD(
        fromFormat      = "PDF",
        toFormat        = "Word",
        fromExtensions  = listOf("pdf"),
        outputExtension = "docx"
    ),
    PDF_TO_HTML(
        fromFormat      = "PDF",
        toFormat        = "HTML",
        fromExtensions  = listOf("pdf"),
        outputExtension = "html"
    ),

    // ── Word ──────────────────────────────────────────
    WORD_TO_PDF(
        fromFormat      = "Word",
        toFormat        = "PDF",
        fromExtensions  = listOf("doc", "docx"),
        outputExtension = "pdf"
    ),
    WORD_TO_TXT(
        fromFormat      = "Word",
        toFormat        = "TXT",
        fromExtensions  = listOf("doc", "docx"),
        outputExtension = "txt"
    ),
    WORD_TO_HTML(
        fromFormat      = "Word",
        toFormat        = "HTML",
        fromExtensions  = listOf("doc", "docx"),
        outputExtension = "html"
    ),

    // ── Excel ─────────────────────────────────────────
    EXCEL_TO_PDF(
        fromFormat      = "Excel",
        toFormat        = "PDF",
        fromExtensions  = listOf("xls", "xlsx"),
        outputExtension = "pdf"
    ),
    EXCEL_TO_CSV(
        fromFormat      = "Excel",
        toFormat        = "CSV",
        fromExtensions  = listOf("xls", "xlsx"),
        outputExtension = "csv"
    ),
    EXCEL_TO_HTML(
        fromFormat      = "Excel",
        toFormat        = "HTML",
        fromExtensions  = listOf("xls", "xlsx"),
        outputExtension = "html"
    ),

    // ── PowerPoint ────────────────────────────────────
    PPT_TO_PDF(
        fromFormat      = "PowerPoint",
        toFormat        = "PDF",
        fromExtensions  = listOf("ppt", "pptx"),
        outputExtension = "pdf"
    ),
    PPT_TO_TXT(
        fromFormat      = "PowerPoint",
        toFormat        = "TXT",
        fromExtensions  = listOf("ppt", "pptx"),
        outputExtension = "txt"
    )
}

fun ConversionType.getCategoryLabel(): String = when (this) {
    ConversionType.IMAGE_TO_PDF,
    ConversionType.IMAGE_TO_JPG,
    ConversionType.IMAGE_TO_PNG,
    ConversionType.IMAGE_TO_WEBP,
    ConversionType.IMAGE_TO_BMP   -> "Imagen"

    ConversionType.PDF_TO_IMAGE,
    ConversionType.PDF_TO_TXT,
    ConversionType.PDF_TO_WORD,
    ConversionType.PDF_TO_HTML    -> "PDF"

    ConversionType.WORD_TO_PDF,
    ConversionType.WORD_TO_TXT,
    ConversionType.WORD_TO_HTML   -> "Word"

    ConversionType.EXCEL_TO_PDF,
    ConversionType.EXCEL_TO_CSV,
    ConversionType.EXCEL_TO_HTML  -> "Excel"

    ConversionType.PPT_TO_PDF,
    ConversionType.PPT_TO_TXT     -> "PowerPoint"
}