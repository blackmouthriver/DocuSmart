@file:Suppress("MatchingDeclarationName")

package com.docsmart.features.converter.presentation.components

import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import java.io.File

// Lógica pura (sin Compose ni Android) de las tarjetas de resultado del
// Convertidor, extraída de ConversionSuccess/BatchConversionSuccess para poder
// testearla en JVM.
// /

/** Familia de formato de un archivo de salida, decidida por su extensión. */
internal enum class OutputFileKind { PDF, IMAGE, WORD, EXCEL, POWERPOINT, TEXT, HTML, OTHER }

internal fun outputFileKindForExtension(extension: String): OutputFileKind =
    when (extension.lowercase()) {
        "pdf" -> OutputFileKind.PDF
        "jpg", "jpeg", "png", "webp", "bmp" -> OutputFileKind.IMAGE
        "doc", "docx" -> OutputFileKind.WORD
        "xls", "xlsx", "csv" -> OutputFileKind.EXCEL
        "ppt", "pptx" -> OutputFileKind.POWERPOINT
        "txt" -> OutputFileKind.TEXT
        "html" -> OutputFileKind.HTML
        else -> OutputFileKind.OTHER
    }

/** Archivos a compartir de una conversión: el principal más los extra (PDF a Imagen con varias páginas). */
internal fun filesToShare(result: ConversionResult.Success): List<File> {
    return listOf(result.outputFile) + result.extraFiles
}

/** Con más de un archivo se usa ACTION_SEND_MULTIPLE; con uno solo, ACTION_SEND. */
internal fun shouldShareMultiple(files: List<File>): Boolean = files.size > 1

/** Solo se comparten los archivos que todavía existen en disco. */
internal fun existingFilesOnly(files: List<File>): List<File> = files.filter { it.exists() }

/** Cantidad de archivos del lote que se convirtieron con éxito. */
internal fun batchSuccessCount(items: List<BatchConversionItem>): Int {
    return items.count { it.result is ConversionResult.Success }
}

/** Subtítulo de la fila de un lote: nombre de salida si hubo éxito, mensaje si falló, vacío si no hay resultado. */
internal fun batchRowSubtitle(result: ConversionResult): String =
    when (result) {
        is ConversionResult.Success -> result.outputFile.name
        is ConversionResult.Error -> result.message
        ConversionResult.Loading -> ""
    }

/** "Guardar todas" solo se ofrece si aún no se guardó y hay al menos un archivo convertido. */
internal fun canSaveBatch(
    savedToDownloads: Boolean,
    successCount: Int,
): Boolean = !savedToDownloads && successCount > 0
