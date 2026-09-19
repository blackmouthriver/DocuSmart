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
 * PdfToWordUseCase/RF-CONV-09).
 *
 * Los tests de `parseWordParagraphsWithHeadings()` se quitaron 2026-09-08
 * junto con esa función -- Lectura pasó a aceptar solo PDF (pedido
 * explícito del usuario: Word daba problemas con el parser propio de esta
 * pantalla).
 */
class StudyTextExtractionTest {
    // ── groupPdfChunksIntoParagraphs ──────────────────────────────────────

    @Test
    fun `lineas con poco espacio entre si quedan en el mismo parrafo`() {
        val chunks =
            listOf(
                StudyPdfChunk("Primera linea de un parrafo largo", y = 700f, fontSize = 12f),
                // gap 14 < 1.6*12
                StudyPdfChunk("que continua ajustado en la siguiente.", y = 686f, fontSize = 12f),
            )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(1, paragraphs.size)
        assertEquals(
            "Primera linea de un parrafo largo que continua ajustado en la siguiente.",
            paragraphs[0],
        )
    }

    @Test
    fun `un salto vertical grande crea un parrafo nuevo`() {
        val chunks =
            listOf(
                StudyPdfChunk("Primer parrafo.", y = 700f, fontSize = 12f),
                // gap 50 > 1.6*12
                StudyPdfChunk("Segundo parrafo.", y = 650f, fontSize = 12f),
            )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(2, paragraphs.size)
        assertEquals("Primer parrafo.", paragraphs[0])
        assertEquals("Segundo parrafo.", paragraphs[1])
    }

    @Test
    fun `parrafos muy cortos se descartan`() {
        val chunks =
            listOf(
                StudyPdfChunk("Ok", y = 700f, fontSize = 12f),
                StudyPdfChunk("Un parrafo real con suficiente longitud.", y = 650f, fontSize = 12f),
            )

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].contains("parrafo real"))
    }

    @Test
    fun `sin fragmentos no hay parrafos`() {
        assertEquals(emptyList<String>(), groupPdfChunksIntoParagraphs(emptyList()))
    }
}
