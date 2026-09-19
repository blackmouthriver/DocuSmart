package com.docsmart.features.viewer.presentation

import com.docsmart.core.ui.components.DocumentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ViewerMimeTypesTest {
    @Test
    fun `detectDocumentType clasifica por mime`() {
        assertEquals(DocumentType.IMAGE, detectDocumentType("image/jpeg"))
        assertEquals(DocumentType.PDF, detectDocumentType("application/pdf"))
        assertEquals(DocumentType.WORD, detectDocumentType("application/msword"))
        assertEquals(
            DocumentType.WORD,
            detectDocumentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(DocumentType.EXCEL, detectDocumentType("application/vnd.ms-excel"))
        assertEquals(
            DocumentType.EXCEL,
            detectDocumentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
        assertEquals(DocumentType.POWERPOINT, detectDocumentType("application/vnd.ms-powerpoint"))
        assertEquals(
            DocumentType.POWERPOINT,
            detectDocumentType("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        )
        assertEquals(DocumentType.TEXT, detectDocumentType("text/plain"))
    }

    @Test
    fun `detectDocumentType con mime desconocido cae a PDF`() {
        assertEquals(DocumentType.PDF, detectDocumentType("application/octet-stream"))
        assertEquals(DocumentType.PDF, detectDocumentType(""))
    }

    @Test
    fun `resolveMimeTypeByExtension mapea las extensiones conocidas sin distinguir mayusculas`() {
        assertEquals("application/pdf", resolveMimeTypeByExtension("a.PDF"))
        assertEquals("application/msword", resolveMimeTypeByExtension("a.doc"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            resolveMimeTypeByExtension("a.docx"),
        )
        assertEquals("application/vnd.ms-excel", resolveMimeTypeByExtension("a.xls"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            resolveMimeTypeByExtension("a.xlsx"),
        )
        assertEquals("application/vnd.ms-powerpoint", resolveMimeTypeByExtension("a.ppt"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            resolveMimeTypeByExtension("a.pptx"),
        )
        assertEquals("image/jpeg", resolveMimeTypeByExtension("a.jpg"))
        assertEquals("image/jpeg", resolveMimeTypeByExtension("a.jpeg"))
        assertEquals("image/png", resolveMimeTypeByExtension("a.png"))
        assertEquals("image/webp", resolveMimeTypeByExtension("a.webp"))
        assertEquals("image/gif", resolveMimeTypeByExtension("a.gif"))
        assertEquals("text/plain", resolveMimeTypeByExtension("a.txt"))
        assertEquals("text/markdown", resolveMimeTypeByExtension("a.md"))
        assertEquals("text/csv", resolveMimeTypeByExtension("a.csv"))
    }

    @Test
    fun `resolveMimeTypeByExtension devuelve null sin extension o con una desconocida`() {
        assertNull(resolveMimeTypeByExtension("sinextension"))
        assertNull(resolveMimeTypeByExtension("a.xyz"))
        assertNull(resolveMimeTypeByExtension(""))
    }

    @Test
    fun `resolveMimeType deduce por el final de la ruta`() {
        assertEquals("application/pdf", resolveMimeType("/data/x/doc.PDF"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            resolveMimeType("/data/x/doc.docx"),
        )
        assertEquals("application/msword", resolveMimeType("/data/x/doc.doc"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            resolveMimeType("/data/x/doc.xlsx"),
        )
        assertEquals("application/vnd.ms-excel", resolveMimeType("/data/x/doc.xls"))
        assertEquals("image/jpeg", resolveMimeType("/data/x/foto.jpg"))
        assertEquals("image/jpeg", resolveMimeType("/data/x/foto.JPEG"))
        assertEquals("image/png", resolveMimeType("/data/x/foto.png"))
        assertEquals("text/plain", resolveMimeType("/data/x/nota.txt"))
    }

    @Test
    fun `resolveMimeType trata cualquier ruta con image como jpeg y devuelve null si no reconoce`() {
        assertEquals("image/jpeg", resolveMimeType("content://media/external/images/media/12"))
        assertNull(resolveMimeType("/data/x/archivo.bin"))
    }

    @Test
    fun `chooseDisplayMimeType prefiere el mime concreto del resolver`() {
        assertEquals("application/pdf", chooseDisplayMimeType("application/pdf", "text/plain"))
    }

    @Test
    fun `chooseDisplayMimeType sin mime del resolver usa la extension o octet-stream`() {
        assertEquals("text/plain", chooseDisplayMimeType(null, "text/plain"))
        assertEquals("application/octet-stream", chooseDisplayMimeType(null, null))
    }

    @Test
    fun `chooseDisplayMimeType con octet-stream o comodin prefiere la extension`() {
        assertEquals("application/pdf", chooseDisplayMimeType("application/octet-stream", "application/pdf"))
        assertEquals("application/octet-stream", chooseDisplayMimeType("application/octet-stream", null))
        assertEquals("application/pdf", chooseDisplayMimeType("*/*", "application/pdf"))
        assertEquals("image/*", chooseDisplayMimeType("image/*", null))
    }
}
