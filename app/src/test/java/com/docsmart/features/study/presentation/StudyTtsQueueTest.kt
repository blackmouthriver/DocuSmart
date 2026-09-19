package com.docsmart.features.study.presentation

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
    fun `un parrafo corto no se parte`() {
        assertEquals(listOf("Hola mundo."), splitForSpeech("Hola mundo."))
    }

    @Test
    fun `un parrafo mas largo que el limite se parte en oraciones completas`() {
        val sentence = "Esta es una oracion de prueba. "
        val text = sentence.repeat(300).trim()

        val parts = splitForSpeech(text)

        assertTrue(parts.size >= 3, "partes: ${parts.size}")
        assertTrue(parts.all { it.length <= TTS_MAX_PARAGRAPH_CHARS })
        assertTrue(parts.all { it.endsWith(".") })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test
    fun `un texto largo sin puntuacion se parte en espacios sin perder palabras`() {
        val text = "palabra ".repeat(1000).trim()

        val parts = splitForSpeech(text, maxLength = 100)

        assertTrue(parts.all { it.length <= 100 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test
    fun `un texto largo sin espacios se corta de forma dura sin colgarse`() {
        val text = "x".repeat(250)

        val parts = splitForSpeech(text, maxLength = 100)

        assertEquals(listOf(100, 100, 50), parts.map { it.length })
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
