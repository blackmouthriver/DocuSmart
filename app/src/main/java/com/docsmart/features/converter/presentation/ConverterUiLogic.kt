@file:Suppress("MatchingDeclarationName")

package com.docsmart.features.converter.presentation

import com.docsmart.features.converter.domain.model.ConversionType

// Lógica pura (sin Compose ni Android) de ConverterScreen, extraída para poder
// testearla en JVM: agrupación de tipos por categoría, MIME del selector de
// archivos, tipo inicial de navegación y nivel del contador diario.
// /

/** Nivel visual del indicador de límite diario. */
internal enum class LimitLevel { OK, WARNING, REACHED }

/** Fracción usada del límite diario, acotada a [0, 1]. Un límite no positivo cuenta como agotado. */
internal fun conversionLimitProgress(
    count: Int,
    limit: Int,
): Float = if (limit <= 0) 1f else (count.toFloat() / limit).coerceIn(0f, 1f)

/** REACHED al agotar el límite, WARNING desde el 60% de uso, OK en cualquier otro caso. */
internal fun conversionLimitLevel(progress: Float): LimitLevel =
    when {
        progress >= 1f -> LimitLevel.REACHED
        progress >= 0.6f -> LimitLevel.WARNING
        else -> LimitLevel.OK
    }

/**
 * Resuelve el tipo pasado por navegación (nombre de [ConversionType]) al abrir
 * la pantalla desde un acceso directo. Devuelve null si ya hay un tipo
 * seleccionado (no pisar una elección manual) o si el nombre no coincide con
 * ningún tipo real.
 */
internal fun resolveInitialType(
    initialType: String?,
    currentType: ConversionType?,
): ConversionType? {
    if (initialType == null || currentType != null) return null
    return ConversionType.entries.find { it.name == initialType }
}

internal fun ConversionType.getCategoryForUi(): String =
    when (this) {
        ConversionType.IMAGE_TO_PDF,
        ConversionType.IMAGE_TO_JPG,
        ConversionType.IMAGE_TO_PNG,
        ConversionType.IMAGE_TO_WEBP,
        ConversionType.IMAGE_TO_BMP,
        -> "Imagen"
        ConversionType.PDF_TO_IMAGE,
        ConversionType.PDF_TO_TXT,
        ConversionType.PDF_TO_WORD,
        ConversionType.PDF_TO_HTML,
        -> "PDF"
        ConversionType.WORD_TO_PDF,
        ConversionType.WORD_TO_TXT,
        ConversionType.WORD_TO_HTML,
        -> "Word"
        ConversionType.EXCEL_TO_PDF,
        ConversionType.EXCEL_TO_CSV,
        ConversionType.EXCEL_TO_HTML,
        -> "Excel"
        ConversionType.PPT_TO_PDF,
        ConversionType.PPT_TO_TXT,
        -> "PowerPoint"
    }

internal fun getMimeForType(type: ConversionType): String =
    when (type) {
        ConversionType.IMAGE_TO_PDF,
        ConversionType.IMAGE_TO_JPG,
        ConversionType.IMAGE_TO_PNG,
        ConversionType.IMAGE_TO_WEBP,
        ConversionType.IMAGE_TO_BMP,
        -> "image/*"
        ConversionType.PDF_TO_IMAGE,
        ConversionType.PDF_TO_TXT,
        ConversionType.PDF_TO_WORD,
        ConversionType.PDF_TO_HTML,
        -> "application/pdf"
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada, #25,
        // latente -- WORD_TO_* está oculto de la grilla hoy): "application/
        // msword" solo cubre .doc legado -- un .docx real (el formato Word
        // más común) tiene otro MIME
        // (application/vnd.openxmlformats-officedocument.wordprocessingml.document),
        // así que muchos proveedores de documentos lo filtraban fuera del
        // selector. Excel/PowerPoint ya usan "*/*" para evitar este mismo
        // problema -- mismo criterio acá.
        ConversionType.WORD_TO_PDF,
        ConversionType.WORD_TO_TXT,
        ConversionType.WORD_TO_HTML,
        -> "*/*"
        ConversionType.EXCEL_TO_PDF,
        ConversionType.EXCEL_TO_CSV,
        ConversionType.EXCEL_TO_HTML,
        -> "*/*"
        ConversionType.PPT_TO_PDF,
        ConversionType.PPT_TO_TXT,
        -> "*/*"
    }
