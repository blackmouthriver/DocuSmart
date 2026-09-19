package com.docsmart.features.pdftools.domain.model

import java.io.File

sealed class PdfToolResult {
    data class Success(
        val outputFile: File,
        val message: String,
    ) : PdfToolResult()

    // HU-53 (extraer imágenes embebidas): la única herramienta de las 15 que
    // produce N archivos de salida en vez de uno -- variante separada en vez
    // de forzar outputFiles: List<File> en Success (rompería los 14 call
    // sites existentes que asumen un único archivo).
    data class MultiSuccess(
        val outputFiles: List<File>,
        val message: String,
    ) : PdfToolResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : PdfToolResult()

    data object Loading : PdfToolResult()
}
