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

// Fase 2 de Lectura (2026-09-21): velocidad y salto de párrafo.
// El factor 1.0 equivale a la velocidad base que ya usaba la app (0.85 del motor).
internal const val BASE_SPEECH_RATE = 0.85f
internal val READING_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun nextReadingSpeed(current: Float): Float {
    val index = READING_SPEEDS.indexOfFirst { it >= current - 0.001f }
    return if (index < 0 || index == READING_SPEEDS.lastIndex) READING_SPEEDS.first() else READING_SPEEDS[index + 1]
}

// "1×", "1.25×"; sin ceros sobrantes.
internal fun readingSpeedLabel(speed: Float): String = "${speed.toString().removeSuffix(".0")}\u00D7"

// Párrafo al que salta "anterior"/"siguiente": acotado al documento; sin lectura
// iniciada (-1) se parte del primer párrafo.
internal fun steppedParagraph(
    current: Int,
    delta: Int,
    lastIndex: Int,
): Int = if (lastIndex < 0) -1 else (current.coerceAtLeast(0) + delta).coerceIn(0, lastIndex)

// Fase 4 (2026-09-22): partir un párrafo en "antes / frase que suena / después"
// para el resaltado en tiempo real de `onRangeStart`. Función pura (sin
// Compose) para poder testearla sola -- de ahí salió un bug real: el rango que
// llega es `start until end` (con `range.last == end - 1`, la convención
// habitual de Kotlin para índices), pero `String.substring(a, b)` trata `b`
// como EXCLUSIVO -- usar `range.last` directo como límite le comía la última
// letra de cada palabra resaltada (verificado en pantalla: "Linea" en vez de
// "Línea", "pagin" en vez de "pagina"). También se acota a los límites del
// texto (`coerceIn`) porque no todos los motores/voces reportan rangos
// consistentes -- antes de esto, un rango igual a la longitud del texto podía
// lanzar `StringIndexOutOfBoundsException` y tumbar el modo Texto.
internal data class HighlightedText(
    val before: String,
    val highlighted: String,
    val after: String,
)

internal fun splitForHighlight(
    text: String,
    range: IntRange?,
): HighlightedText {
    if (range == null) return HighlightedText(text, "", "")
    val start = range.first.coerceIn(0, text.length)
    val endExclusive = (range.last + 1).coerceIn(start, text.length)
    return HighlightedText(
        before = text.substring(0, start),
        highlighted = text.substring(start, endExclusive),
        after = text.substring(endExclusive),
    )
}
