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

// RF-CONV-08: resultado de un archivo dentro de una conversión por lotes —
// conserva el nombre original para poder mostrar "archivo.docx → archivo.pdf"
// aunque la conversión haya fallado (en ese caso no hay outputFile que lo diga).
data class BatchConversionItem(
    val originalFileName: String,
    val result: ConversionResult
)