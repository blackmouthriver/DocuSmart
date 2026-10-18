package com.docsmart.features.library.presentation

import android.content.Intent
import android.net.Uri

/**
 * 1.1.5: "Agregar documentos" en Biblioteca. Abre el selector de archivos del
 * sistema (ACTION_OPEN_DOCUMENT, sin ningun permiso de almacenamiento) con
 * multi-seleccion y pide acceso persistente de lectura y escritura -- igual que
 * el boton "Abrir" de Inicio, para que despues "Eliminar" funcione sobre el
 * documento (ver DocumentRepository.deleteSafDocument).
 */
internal val PICKABLE_DOCUMENT_MIME_TYPES =
    arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/markdown",
        "application/zip",
        "application/x-zip-compressed",
    )

internal fun documentPickerIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, PICKABLE_DOCUMENT_MIME_TYPES)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
    }

/** Uris elegidas: varias vienen en `clipData`, una sola puede venir en `data`. */
internal fun pickedUris(result: Intent?): List<Uri> {
    val clip = result?.clipData
    return if (clip != null) {
        (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    } else {
        listOfNotNull(result?.data)
    }
}
