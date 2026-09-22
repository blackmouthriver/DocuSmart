package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Movido desde `presentation.StudyTtsQueueTest` el 2026-09-22 junto con
 * `splitForSpeech` (ver StudyTextSplitter.kt): el OCR de páginas escaneadas,
 * en `domain`, también necesita partir texto largo para TextToSpeech.
 */
class StudyTextSplitterTest {
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
}
