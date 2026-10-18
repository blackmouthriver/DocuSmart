package com.docsmart.features.library.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 1.1.5: una foto agregada con el selector del sistema es una referencia de
 * solo lectura; "Eliminar" nunca debe tratarla como una fila propia de
 * MediaStore (pediria borrar la foto original de la galeria).
 */
class PickedMediaUrisTest {
    @Test
    fun `reconoce el Uri del selector de fotos`() {
        assertTrue(
            PickedMediaUris.isPickedReference(
                "content://media/picker/0/com.android.providers.media.photopicker/media/1000157509",
            ),
        )
    }

    @Test
    fun `reconoce el Uri del selector en modo GET_CONTENT`() {
        assertTrue(
            PickedMediaUris.isPickedReference(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1000093965",
            ),
        )
    }

    @Test
    fun `una fila real de MediaStore no es una referencia del selector`() {
        assertFalse(PickedMediaUris.isPickedReference("content://media/external/images/media/1000157479"))
        assertFalse(PickedMediaUris.isPickedReference("content://media/external/downloads/12345"))
    }

    @Test
    fun `un documento SAF o un archivo de la app no son referencias del selector`() {
        val saf = "content://com.android.externalstorage.documents/document/primary%3ADocs%2Fa.pdf"
        assertFalse(PickedMediaUris.isPickedReference(saf))
        assertFalse(PickedMediaUris.isPickedReference("/data/user/0/com.docsmart/files/converted/a.pdf"))
    }
}
