package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RF-STU-08: solo cubre `buildPlainText` (lógica pura, sin Context ni
 * archivos reales) -- `exportAsTextFile`/`exportAsPdfFile` escriben a disco
 * y generan PDF con iText7, mismo límite ya documentado para otros use
 * cases de conversión/PDF (no cubiertos por unit test, requieren
 * instrumentación o un archivo temporal real).
 */
class StudyNotesExporterTest {

    @Test
    fun `una sola nota incluye titulo, fecha y texto`() {
        val note = SavedNote(id = "1", title = "Repaso", text = "Contenido de la nota", dateTime = "24/08/2026 · 10:00")

        val result = StudyNotesExporter.buildPlainText(listOf(note))

        assertTrue(result.contains("Repaso"))
        assertTrue(result.contains("24/08/2026 · 10:00"))
        assertTrue(result.contains("Contenido de la nota"))
    }

    @Test
    fun `varias notas quedan separadas por un separador`() {
        val first = SavedNote(id = "1", title = "A", text = "texto A", dateTime = "24/08/2026 · 10:00")
        val second = SavedNote(id = "2", title = "B", text = "texto B", dateTime = "24/08/2026 · 11:00")

        val result = StudyNotesExporter.buildPlainText(listOf(first, second))
        val parts = result.split("\n\n")

        assertTrue(result.indexOf("texto A") < result.indexOf("texto B"))
        assertTrue(parts.size >= 3, "debe haber al menos un bloque separador entre las dos notas")
    }

    @Test
    fun `sin notas devuelve texto vacio`() {
        assertEquals("", StudyNotesExporter.buildPlainText(emptyList()))
    }
}
