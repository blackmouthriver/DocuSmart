package com.docsmart.features.pdftools.domain.model

import java.io.File

sealed class PdfToolResult {
    data class Success(
        val outputFile: File,
        val message: String
    ) : PdfToolResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : PdfToolResult()

    data object Loading : PdfToolResult()
}