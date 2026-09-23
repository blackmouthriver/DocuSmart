package com.docsmart.features.study.domain

// Ronda 16, movido a domain el 2026-09-22: partir texto largo en fragmentos
// aptos para TextToSpeech.speak() es lógica pura de texto (sin Android UI),
// y tanto `presentation` (StudyScreen/StudyTtsQueue) como la extracción OCR
// de `domain` (StudyOcrTextExtractor) la necesitan -- vivía en
// `presentation.StudyTtsQueue` y eso violaba la regla de arquitectura
// "domain no depende de presentation" en cuanto el OCR empezó a usarla.

// Android limita cada llamada a TextToSpeech.speak() a
// TextToSpeech.getMaxSpeechInputLength() (4000 caracteres). Un PDF con
// interlineado uniforme (sin huecos entre líneas) se agrupa en un único
// "párrafo" de toda la página, que puede pasar ese límite: speak() devuelve
// ERROR y ese texto se omitía en silencio de "Leer todo". Se deja margen bajo
// el límite real.
internal const val TTS_MAX_PARAGRAPH_CHARS = 3500

internal fun splitForSpeech(
    text: String,
    maxLength: Int = TTS_MAX_PARAGRAPH_CHARS,
): List<String> {
    if (text.length <= maxLength) return listOf(text)
    val parts = mutableListOf<String>()
    var remaining = text
    while (remaining.length > maxLength) {
        val window = remaining.substring(0, maxLength)
        val sentenceEnd = SENTENCE_BREAKS.maxOf { window.lastIndexOf(it) }
        val cutAt =
            when {
                // Corte tras el signo de puntuación, si cae en la segunda mitad.
                sentenceEnd > maxLength / 2 -> sentenceEnd + 1
                else -> window.lastIndexOf(' ').takeIf { it > 0 } ?: maxLength
            }
        parts.add(remaining.substring(0, cutAt).trim())
        remaining = remaining.substring(cutAt).trim()
    }
    if (remaining.isNotEmpty()) parts.add(remaining)
    return parts.filter { it.isNotEmpty() }
}

private val SENTENCE_BREAKS = listOf(". ", "? ", "! ", "; ")
