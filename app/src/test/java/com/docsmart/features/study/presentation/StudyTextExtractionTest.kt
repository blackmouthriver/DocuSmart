package com.docsmart.features.study.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Modo Estudio antes dividía el texto de un PDF por CADA salto de línea
 * (`pageText.split("\n")`), así que una oración que el PDF ajusta en 2-3
 * líneas visuales se leía en voz alta como 2-3 "párrafos" cortados a mitad
 * de frase. `groupPdfChunksIntoParagraphs()` agrupa por el espaciado
 * vertical real entre líneas (misma heurística ya verificada en
 * PdfToWordUseCase/RF-CONV-09), y `parseWordParagraphsWithHeadings()`
 * agrega la misma detección de encabezado que ya tiene el Visor de Word
 * (antes Estudio no distinguía encabezados en absoluto).
 */
class StudyTextExtractionTest {

    // ── groupPdfChunksIntoParagraphs ──────────────────────────────────────

    @Test
    fun `lineas con poco espacio entre si quedan en el mismo parrafo`() {
        val chunks = listOf(
            StudyPdfChunk("Primera linea de un parrafo largo", y = 700f, fontSize = 12f),
            StudyPdfChunk("que continua ajustado en la siguiente.", y = 686f, fontSize = 12f) // gap 14 < 1.6*12
        )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(1, paragraphs.size)
        assertEquals(
            "Primera linea de un parrafo largo que continua ajustado en la siguiente.",
            paragraphs[0]
        )
    }

    @Test
    fun `un salto vertical grande crea un parrafo nuevo`() {
        val chunks = listOf(
            StudyPdfChunk("Primer parrafo.", y = 700f, fontSize = 12f),
            StudyPdfChunk("Segundo parrafo.", y = 650f, fontSize = 12f) // gap 50 > 1.6*12
        )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(2, paragraphs.size)
        assertEquals("Primer parrafo.", paragraphs[0])
        assertEquals("Segundo parrafo.", paragraphs[1])
    }

    @Test
    fun `parrafos muy cortos se descartan`() {
        val chunks = listOf(
            StudyPdfChunk("Ok", y = 700f, fontSize = 12f),
            StudyPdfChunk("Un parrafo real con suficiente longitud.", y = 650f, fontSize = 12f)
        )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].contains("parrafo real"))
    }

    @Test
    fun `sin fragmentos no hay parrafos`() {
        assertEquals(emptyList<String>(), groupPdfChunksIntoParagraphs(emptyList()))
    }

    // ── parseWordParagraphsWithHeadings ───────────────────────────────────

    @Test
    fun `detecta el indice de un parrafo con estilo de encabezado`() {
        val xml = """
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Titulo principal</w:t></w:r></w:p>
            <w:p><w:r><w:t>Parrafo normal despues del titulo.</w:t></w:r></w:p>
        """.trimIndent()

        val (paragraphs, headingIndices) = parseWordParagraphsWithHeadings(xml)

        assertEquals(2, paragraphs.size)
        assertEquals(setOf(0), headingIndices)
        assertEquals("Titulo principal", paragraphs[0])
    }

    @Test
    fun `detecta encabezado con el identificador de estilo real que escribe Word en espanol`() {
        // Bug real encontrado en dispositivo: Word en español escribe
        // w:val="Ttulo1" (tilde quitada), no "Heading1" como se asumía.
        val xml = """<w:p><w:pPr><w:pStyle w:val="Ttulo1"/></w:pPr><w:r><w:t>Titulo del documento</w:t></w:r></w:p>"""

        val (paragraphs, headingIndices) = parseWordParagraphsWithHeadings(xml)

        assertEquals(1, paragraphs.size)
        assertEquals(setOf(0), headingIndices)
    }

    @Test
    fun `parrafo sin estilo de encabezado no se marca como tal`() {
        val xml = "<w:p><w:r><w:t>Solo texto normal, nada de encabezado.</w:t></w:r></w:p>"

        val (paragraphs, headingIndices) = parseWordParagraphsWithHeadings(xml)

        assertEquals(1, paragraphs.size)
        assertTrue(headingIndices.isEmpty())
    }

    @Test
    fun `parrafos muy cortos no cuentan para el indice de encabezados`() {
        val xml = """
            <w:p><w:r><w:t>Ok</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Titulo real</w:t></w:r></w:p>
        """.trimIndent()

        val (paragraphs, headingIndices) = parseWordParagraphsWithHeadings(xml)

        assertEquals(1, paragraphs.size)
        assertEquals(setOf(0), headingIndices)
    }
}
