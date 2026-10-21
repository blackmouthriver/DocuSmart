package com.docsmart.features.library.data

/**
 * Fotos agregadas desde el selector de fotos del sistema (1.1.5).
 *
 * Su Uri (`content://media/picker/...` o `content://media/picker_get_content/...`)
 * es una REFERENCIA con acceso de solo lectura que el usuario concedio: la foto
 * original sigue siendo suya, en su galeria. "Eliminar" desde Biblioteca o la
 * Papelera solo debe quitarla de DocuSmart (historial + permiso persistente),
 * nunca pedir a Android que borre la foto real -- por eso estos ids no se
 * tratan como filas de MediaStore al borrar (ver `DocumentRepository.deleteDocument`
 * y `TrashRepository.isMediaStoreId`).
 */
internal object PickedMediaUris {
    private const val PICKER_PREFIX = "content://media/picker"

    fun isPickedReference(documentId: String): Boolean = documentId.startsWith(PICKER_PREFIX)
}
