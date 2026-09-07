package com.docsmart.features.study.domain

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exportar el resumen automático (2026-09-08) como texto plano, para
 * guardarlo en Descargas o compartirlo -- mismo patrón de nombre/ubicación
 * ya usado por `StudyNotesExporter`.
 */
object StudySummaryExporter {

    private const val EXPORT_DIR_NAME = "study_exports"

    internal fun buildPlainText(documentName: String, sentences: List<String>): String =
        buildString {
            append(documentName)
            append("\n\n")
            sentences.forEach { sentence ->
                append("• ")
                append(sentence)
                append("\n\n")
            }
        }.trim()

    fun exportAsTextFile(context: Context, documentName: String, sentences: List<String>): File {
        val dir = File(context.filesDir, EXPORT_DIR_NAME).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "DocuSmart_Resumen_$timestamp.txt")
        file.writeText(buildPlainText(documentName, sentences))
        return file
    }
}
