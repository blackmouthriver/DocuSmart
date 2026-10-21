package com.docsmart.features.library.presentation

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1.5: "Agregar documentos". Se prueba en el dispositivo porque usa clases
 * reales de Android (Intent, ClipData, Uri) que un test JVM puro no trae.
 */
class DocumentPickingTest {
    private val first = Uri.parse("content://docs/document/1")
    private val second = Uri.parse("content://docs/document/2")

    @Test
    fun variosDocumentosVienenEnClipData() {
        val clip = ClipData.newRawUri("docs", first).apply { addItem(ClipData.Item(second)) }
        val result = Intent().apply { clipData = clip }

        assertEquals(listOf(first, second), pickedUris(result))
    }

    @Test
    fun unSoloDocumentoPuedeVenirEnData() {
        assertEquals(listOf(first), pickedUris(Intent().apply { data = first }))
    }

    @Test
    fun sinResultadoNoHayDocumentos() {
        assertTrue(pickedUris(null).isEmpty())
        assertTrue(pickedUris(Intent()).isEmpty())
    }

    @Test
    fun elSelectorPideMultiseleccionYAccesoPersistenteDeLecturaYEscritura() {
        val intent = documentPickerIntent()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        val expected =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        assertEquals(expected, intent.flags and expected)
        assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/pdf"))
    }
}
