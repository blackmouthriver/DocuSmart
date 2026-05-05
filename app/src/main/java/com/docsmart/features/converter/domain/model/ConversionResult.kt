package com.docsmart.features.converter.domain.model

import java.io.File

sealed class ConversionResult {
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSizeKb: Long
    ) : ConversionResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ConversionResult()

    data object Loading : ConversionResult()
}