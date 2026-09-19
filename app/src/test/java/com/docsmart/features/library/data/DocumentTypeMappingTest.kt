package com.docsmart.features.library.data

import com.docsmart.core.ui.components.DocumentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentTypeMappingTest {
    @Test
    fun `mimeToDocumentType reconoce cada familia de documentos`() {
        assertEquals(DocumentType.PDF, mimeToDocumentType("application/pdf", "a"))
        assertEquals(DocumentType.WORD, mimeToDocumentType("application/msword", "a"))
        assertEquals(
            DocumentType.WORD,
            mimeToDocumentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "a"),
        )
        assertEquals(DocumentType.EXCEL, mimeToDocumentType("application/vnd.ms-excel", "a"))
        assertEquals(
            DocumentType.EXCEL,
            mimeToDocumentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "a"),
        )
        assertEquals(DocumentType.POWERPOINT, mimeToDocumentType("application/vnd.ms-powerpoint", "a"))
        assertEquals(
            DocumentType.POWERPOINT,
            mimeToDocumentType("application/vnd.openxmlformats-officedocument.presentationml.presentation", "a"),
        )
        assertEquals(DocumentType.IMAGE, mimeToDocumentType("image/png", "a"))
        assertEquals(DocumentType.TEXT, mimeToDocumentType("text/plain", "a"))
        assertEquals(DocumentType.TEXT, mimeToDocumentType("text/markdown", "a"))
    }

    @Test
    fun `mimeToDocumentType con mime desconocido usa la extension del nombre`() {
        assertEquals(DocumentType.ZIP, mimeToDocumentType("application/octet-stream", "backup.zip"))
        assertEquals(DocumentType.WORD, mimeToDocumentType("", "informe.DOCX"))
    }

    @Test
    fun `mimeToDocumentType sin punto en el nombre cae al valor por defecto PDF`() {
        assertEquals(DocumentType.PDF, mimeToDocumentType("", "sinextension"))
    }

    @Test
    fun `extensionToDocumentType ignora mayusculas y cubre todas las extensiones`() {
        assertEquals(DocumentType.PDF, extensionToDocumentType("PDF"))
        assertEquals(DocumentType.WORD, extensionToDocumentType("doc"))
        assertEquals(DocumentType.WORD, extensionToDocumentType("docx"))
        assertEquals(DocumentType.EXCEL, extensionToDocumentType("xls"))
        assertEquals(DocumentType.EXCEL, extensionToDocumentType("XLSX"))
        assertEquals(DocumentType.POWERPOINT, extensionToDocumentType("ppt"))
        assertEquals(DocumentType.POWERPOINT, extensionToDocumentType("pptx"))
        for (ext in listOf("jpg", "jpeg", "png", "webp", "gif")) {
            assertEquals(DocumentType.IMAGE, extensionToDocumentType(ext), ext)
        }
        assertEquals(DocumentType.TEXT, extensionToDocumentType("txt"))
        assertEquals(DocumentType.TEXT, extensionToDocumentType("md"))
        for (ext in listOf("zip", "rar", "7z")) {
            assertEquals(DocumentType.ZIP, extensionToDocumentType(ext), ext)
        }
        assertEquals(DocumentType.PDF, extensionToDocumentType("xyz"))
        assertEquals(DocumentType.PDF, extensionToDocumentType(""))
    }

    @Test
    fun `sizeUnitFor respeta los limites de KB y MB`() {
        assertEquals(SizeUnit.BYTES, sizeUnitFor(0))
        assertEquals(SizeUnit.BYTES, sizeUnitFor(1023))
        assertEquals(SizeUnit.KB, sizeUnitFor(1024))
        assertEquals(SizeUnit.KB, sizeUnitFor(1024L * 1024 - 1))
        assertEquals(SizeUnit.MB, sizeUnitFor(1024L * 1024))
        assertEquals(SizeUnit.MB, sizeUnitFor(5L * 1024 * 1024 * 1024))
    }
}
