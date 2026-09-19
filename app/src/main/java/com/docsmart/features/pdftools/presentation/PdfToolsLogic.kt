package com.docsmart.features.pdftools.presentation

// Lógica pura de PdfToolsScreen (sin Compose ni Android), extraída para poder
// testearla en JVM.
// /

/**
 * Herramienta pedida por un acceso directo (argumento de navegación, nombre del
 * enum [PdfTool]). Devuelve null si el nombre es nulo, no existe o es
 * [PdfTool.NONE] (no es una herramienta real que se pueda abrir).
 */
internal fun parseInitialPdfTool(name: String?): PdfTool? {
    if (name == null) return null
    val tool = runCatching { PdfTool.valueOf(name) }.getOrNull()
    return if (tool == PdfTool.NONE) null else tool
}

/** El contador "usos hoy" solo se muestra a usuarios free que ya usaron la herramienta. */
internal fun shouldShowUsageCounter(
    isPremium: Boolean,
    useCount: Int,
): Boolean = !isPremium && useCount > 0

/** El contador se pinta como error al llegar (o pasar) el límite diario. */
internal fun isUsageLimitReached(
    useCount: Int,
    useLimit: Int,
): Boolean = useCount >= useLimit

/** Tamaño en KB (truncado) que se muestra en la tarjeta de resultado. */
internal fun fileSizeKb(sizeBytes: Long): Long = sizeBytes / 1024
