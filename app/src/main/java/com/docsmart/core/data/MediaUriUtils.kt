package com.docsmart.core.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * Bug real reportado por un tester 2026-09-11: el mismo archivo físico podía
 * tener DOS Uri de MediaStore distintos según de dónde lo leyera la app --
 * `content://media/external/downloads/<id>` (`loadDocumentsFromDownloads()`),
 * `content://media/external/images/media/<id>` (`loadImagesFromMediaStore()`),
 * o `content://media/external/file/<id>` (resultado de `MediaStore.getMediaUri()`
 * al abrir un documento con "Abrir"). Las tres apuntan al mismo `_id` en la
 * tabla real de MediaStore (todas las colecciones son vistas sobre una misma
 * tabla "files"), pero como el resto de la app compara identidad de documento
 * por IGUALDAD DE STRING (`trashedIds`, `byId[it]`, `seen.add(it.id)`), un
 * mismo archivo con dos formas de Uri distintas se veía como dos documentos
 * distintos -- "Eliminar" solo tachaba una de las dos, la otra seguía
 * apareciendo en Biblioteca/Recientes.
 *
 * Se normaliza cualquier Uri de la autoridad real de MediaStore a la
 * colección genérica `MediaStore.Files` (que acepta cualquier `_id` válido de
 * esa tabla, sin importar el tipo real de archivo) -- así el mismo archivo
 * siempre resuelve al mismo string sin importar por qué consulta se haya
 * encontrado.
 */
fun canonicalMediaUri(uri: Uri): Uri {
    if (uri.authority != MediaStore.AUTHORITY) return uri
    return try {
        ContentUris.withAppendedId(
            MediaStore.Files.getContentUri("external"),
            ContentUris.parseId(uri)
        )
    } catch (e: UnsupportedOperationException) {
        // ContentUris.parseId() lanza esto si el último segmento del Uri no
        // es un ID numérico válido -- Uri de autoridad "media" pero con otra
        // forma inesperada, se deja sin normalizar en vez de fallar.
        uri
    }
}
