package com.docsmart.features.study.presentation

import com.docsmart.features.study.domain.TTS_MAX_PARAGRAPH_CHARS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ronda 16: al terminar cada párrafo, "Leer todo" volvía a encolar en el
 * motor TTS todos los párrafos posteriores (que ya estaban en la cola desde
 * que se tocó el botón), repitiendo el texto.
 */
class StudyTtsQueueTest {
    @Test
    fun `terminar un parrafo con toda la cola ya encolada no encola nada nuevo`() {
        val step =
            ttsQueueStepAfterUtterance(finishedIndex = 0, queuedUpTo = 2, lastIndex = 2, extractionComplete = true)

        assertTrue(step.toEnqueue.isEmpty())
        assertEquals(2, step.newQueuedUpTo)
        assertEquals(TtsQueueOutcome.KEEP_PLAYING, step.outcome)
    }

    @Test
    fun `solo se encolan los parrafos que aparecieron despues de armar la cola`() {
        val step =
            ttsQueueStepAfterUtterance(finishedIndex = 0, queuedUpTo = 2, lastIndex = 5, extractionComplete = false)

        assertEquals(listOf(3, 4, 5), step.toEnqueue.toList())
        assertEquals(5, step.newQueuedUpTo)
        assertEquals(TtsQueueOutcome.KEEP_PLAYING, step.outcome)
    }

    @Test
    fun `terminar el ultimo parrafo con la extraccion en curso espera mas texto`() {
        val step =
            ttsQueueStepAfterUtterance(finishedIndex = 2, queuedUpTo = 2, lastIndex = 2, extractionComplete = false)

        assertTrue(step.toEnqueue.isEmpty())
        assertEquals(TtsQueueOutcome.WAIT_FOR_MORE_TEXT, step.outcome)
    }

    @Test
    fun `terminar el ultimo parrafo con la extraccion completa termina la lectura`() {
        val step =
            ttsQueueStepAfterUtterance(finishedIndex = 2, queuedUpTo = 2, lastIndex = 2, extractionComplete = true)

        assertEquals(TtsQueueOutcome.FINISHED, step.outcome)
    }

    @Test
    fun `terminar el ultimo parrafo cuando ya llego texto nuevo lo encola y sigue leyendo`() {
        val step =
            ttsQueueStepAfterUtterance(finishedIndex = 2, queuedUpTo = 2, lastIndex = 4, extractionComplete = false)

        assertEquals(listOf(3, 4), step.toEnqueue.toList())
        assertEquals(TtsQueueOutcome.KEEP_PLAYING, step.outcome)
    }

    @Test
    fun `el agrupador de parrafos parte un parrafo demasiado largo`() {
        // Todas las lineas con el mismo interlineado: un unico parrafo de pagina.
        val chunks =
            (0 until 200).map { i ->
                StudyPdfChunk(
                    text = "Oracion numero $i de la pagina completa del libro. ",
                    y = 700f - i * 14f,
                    fontSize = 12f,
                )
            }

        val paragraphs = groupPdfChunksIntoParagraphs(chunks)

        assertTrue(paragraphs.size >= 2, "parrafos: ${paragraphs.size}")
        assertTrue(paragraphs.all { it.length <= TTS_MAX_PARAGRAPH_CHARS })
    }
}
