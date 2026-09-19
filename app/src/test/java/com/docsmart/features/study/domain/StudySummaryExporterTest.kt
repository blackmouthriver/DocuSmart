package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Resumen local (2026-09-08): solo cubre `buildPlainText` (lógica pura) --
 * `exportAsTextFile` escribe a disco, mismo límite ya documentado para
 * `StudyNotesExporter`.
 */
class StudySummaryExporterTest {
    @Test
    fun `incluye el nombre del documento y cada oracion con vineta`() {
        val result =
            StudySummaryExporter.buildPlainText(
                documentName = "It - Stephen King.pdf",
                sentences = listOf("Primera oración clave.", "Segunda oración clave."),
            )

        assertTrue(result.contains("It - Stephen King.pdf"))
        assertTrue(result.contains("• Primera oración clave."))
        assertTrue(result.contains("• Segunda oración clave."))
    }
}
