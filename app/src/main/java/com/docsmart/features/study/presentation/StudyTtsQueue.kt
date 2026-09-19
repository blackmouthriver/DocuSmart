package com.docsmart.features.study.presentation

// Ronda 16: lógica pura de la cola de "Leer todo", extraída de StudyScreen
// para poder testearla sin TextToSpeech ni Compose.
//
// Bug real que corrige: al tocar "Leer todo" se encolan TODOS los párrafos
// desde el punto de retomo hasta el último (QUEUE_ADD), pero el callback
// onDone de cada párrafo volvía a encolar `index + 1..lastIndex` completos --
// en un documento de N párrafos, terminar el primero re-encolaba los N-1
// restantes, terminar el segundo otros N-2, etc., y el motor TTS repetía el
// texto varias veces. Solo hay que encolar lo que todavía NO está en la cola
// (los párrafos que aparecieron después, por la extracción incremental).
internal enum class TtsQueueOutcome {
    // Todavía hay párrafos encolados detrás del que acaba de terminar.
    KEEP_PLAYING,

    // Se leyó todo lo extraído hasta ahora, pero el PDF sigue procesándose.
    WAIT_FOR_MORE_TEXT,

    // Se leyó todo el documento y la extracción terminó.
    FINISHED,
}

internal data class TtsQueueStep(
    // Índices de párrafo que hay que agregar ahora a la cola (vacío = ninguno).
    val toEnqueue: IntRange,
    // Nuevo "último índice ya encolado" que el llamador debe recordar.
    val newQueuedUpTo: Int,
    val outcome: TtsQueueOutcome,
)

internal fun ttsQueueStepAfterUtterance(
    finishedIndex: Int,
    queuedUpTo: Int,
    lastIndex: Int,
    extractionComplete: Boolean,
): TtsQueueStep {
    val toEnqueue = if (lastIndex > queuedUpTo) (queuedUpTo + 1)..lastIndex else IntRange.EMPTY
    val newQueuedUpTo = maxOf(queuedUpTo, lastIndex)
    val outcome =
        when {
            finishedIndex < newQueuedUpTo -> TtsQueueOutcome.KEEP_PLAYING
            !extractionComplete -> TtsQueueOutcome.WAIT_FOR_MORE_TEXT
            else -> TtsQueueOutcome.FINISHED
        }
    return TtsQueueStep(toEnqueue, newQueuedUpTo, outcome)
}

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
