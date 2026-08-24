package com.docsmart.features.converter.domain.model

import java.io.File

sealed class ConversionResult {
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSizeKb: Int,
        val extraFiles: List<File> = emptyList() // ← para PDF→Imágenes múltiples
    ) : ConversionResult()

    data class Error(val message: String) : ConversionResult()
    data object Loading : ConversionResult()
}