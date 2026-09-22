@file:Suppress("MatchingDeclarationName")

package com.docsmart.features.study.domain

// Pedido explícito del usuario 2026-09-22: "las voces femeninas tienen
// nombres y personajes masculinos... discrimina para que voces femeninas
// tengan personajes femeninos y voces masculinas personajes masculinos".
// `android.speech.tts.Voice` NO expone el género de una voz (confirmado
// contra el SDK real: solo trae name/locale/quality/latency/features, y en
// este motor -- Google TTS -- `features` solo trae metadata de red, sin
// ninguna pista de género). La única forma real de saberlo es escuchar la
// voz: se sintetiza una muestra corta a un archivo WAV
// (`TextToSpeech.synthesizeToFile`, ver VoiceGenderProbe.kt) y se estima su
// tono fundamental (F0) acá, por autocorrelación -- una voz grave se
// clasifica como masculina, una aguda como femenina, el mismo criterio que
// usa la fonética del habla para separar los rangos típicos de voz adulta
// (masculino ~85-180Hz, femenino ~165-255Hz).
//
// Funciones puras (sin `android.speech.tts.*`/`File`) para poder testearlas
// en JVM puro con una onda senoidal sintética de frecuencia conocida --
// mismo criterio ya documentado en este proyecto para `mapOcrBoxToPdf`
// (OcrPdfUseCase) y `horizontalScalingPercent`.

internal data class WavAudio(
    val sampleRateHz: Int,
    val samples: ShortArray,
)

private data class WavHeader(
    val sampleRateHz: Int,
    val channels: Int,
    val dataOffset: Int,
    val dataSize: Int,
)

/**
 * Parsea un WAV PCM de 16 bits (el formato que escribe
 * `TextToSpeech.synthesizeToFile`). Recorre los chunks del contenedor RIFF en
 * vez de asumir el header canónico de 44 bytes -- algunos encoders agregan
 * chunks extra ("LIST", etc.) antes de "data". `null` si no es un WAV PCM16
 * válido (formato distinto, header truncado, sin chunk "data").
 */
internal fun parseWavPcm16(bytes: ByteArray): WavAudio? {
    val header = readWavHeader(bytes) ?: return null
    return readPcm16Samples(bytes, header)?.let { WavAudio(header.sampleRateHz, it) }
}

private fun readWavHeader(bytes: ByteArray): WavHeader? {
    if (bytes.size < 12 || !bytes.matchesAscii(0, "RIFF") || !bytes.matchesAscii(8, "WAVE")) return null

    var pos = 12
    var sampleRate = 0
    var bitsPerSample = 0
    var channels = 1
    var dataOffset = -1
    var dataSize = 0
    while (pos + 8 <= bytes.size) {
        val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
        val chunkSize = readLeInt(bytes, pos + 4)
        val chunkStart = pos + 8
        when (chunkId) {
            "fmt " ->
                if (chunkStart + 16 <= bytes.size) {
                    channels = readLeShort(bytes, chunkStart + 2)
                    sampleRate = readLeInt(bytes, chunkStart + 4)
                    bitsPerSample = readLeShort(bytes, chunkStart + 14)
                }
            "data" -> {
                dataOffset = chunkStart
                dataSize = chunkSize
            }
        }
        // Los chunks RIFF quedan alineados a palabra (2 bytes) -- un tamaño
        // impar deja 1 byte de relleno antes del siguiente chunk.
        pos = chunkStart + chunkSize + (chunkSize and 1)
    }
    return if (dataOffset < 0 || sampleRate <= 0 || bitsPerSample != 16) {
        null
    } else {
        WavHeader(sampleRate, channels, dataOffset, dataSize)
    }
}

private fun readPcm16Samples(
    bytes: ByteArray,
    header: WavHeader,
): ShortArray? {
    val end = (header.dataOffset + header.dataSize).coerceIn(header.dataOffset, bytes.size)
    val totalSamples = (end - header.dataOffset) / 2
    if (totalSamples <= 0) return null
    val raw =
        ShortArray(totalSamples) { i ->
            val idx = header.dataOffset + i * 2
            ((bytes[idx].toInt() and 0xFF) or (bytes[idx + 1].toInt() shl 8)).toShort()
        }
    return if (header.channels == 2 && raw.size >= 2) {
        ShortArray(raw.size / 2) { i -> ((raw[2 * i].toInt() + raw[2 * i + 1].toInt()) / 2).toShort() }
    } else {
        raw
    }
}

private fun ByteArray.matchesAscii(
    offset: Int,
    text: String,
): Boolean {
    if (offset + text.length > size) return false
    return text.indices.all { i -> this[offset + i] == text[i].code.toByte() }
}

private fun readLeInt(
    bytes: ByteArray,
    offset: Int,
): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun readLeShort(
    bytes: ByteArray,
    offset: Int,
): Int = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

// Rango de tono fundamental humano adulto a buscar (Hz) -- fuera de esto es
// ruido o silencio, no una voz.
private const val MIN_F0_HZ = 70
private const val MAX_F0_HZ = 400

// Autocorrelación normalizada por debajo de esto = la señal no es lo
// bastante periódica como para confiar en el tono estimado (silencio,
// consonante sorda, ruido) -- se descarta en vez de arriesgar un género mal
// asignado con una lectura de tono poco confiable.
private const val MIN_VOICED_CORRELATION = 0.35

/**
 * Estima el tono fundamental (F0, en Hz) de [samples] por autocorrelación
 * normalizada -- el pico de correlación entre la señal y una copia
 * desplazada [lag] muestras marca el período de la onda periódica dominante
 * (la vibración de las cuerdas vocales). `null` si el clip es demasiado
 * corto para el rango de F0 buscado, o si no hay suficiente señal periódica
 * para confiar en la estimación.
 *
 * Bug real encontrado verificando en dispositivo (2026-09-22): calcular la
 * autocorrelación sobre el clip COMPLETO de una sola vez diluye el resultado
 * -- una frase corta trae silencio inicial/final y consonantes sordas que no
 * son periódicas, y promediadas con el resto bajan la correlación por debajo
 * del umbral aunque la mayor parte de la voz sea perfectamente audible (3 de
 * 11 voces reales del dispositivo fallaban así, todas con `f0=null` pese a
 * sintetizar y parsear el WAV sin problema).
 *
 * El clip completo se intenta primero (es el cálculo más estable: una sola
 * medición sobre toda la señal, sin depender de dónde caen los bordes de una
 * ventana) y solo si eso falla se analiza en ventanas cortas
 * ([FRAME_DURATION_MS]) tomando la MEDIANA de F0 de las que sí resultan
 * periódicas ("con voz") -- verificado en dispositivo real que analizar TODAS
 * las voces por ventana, aunque el clip completo ya funcionara, hacía que
 * alguna voz borderline (cerca del umbral de género) cambiara de resultado
 * entre una corrida y otra según qué consonante cayera en el borde de cada
 * ventana. Como fallback (clip completo ya falló, algo hay que devolver) esa
 * misma inestabilidad es aceptable: es la única forma de sacarle un tono a
 * esas voces, y solo se usa cuando la medición simple no da ninguno.
 */
internal fun estimateFundamentalFrequencyHz(
    samples: ShortArray,
    sampleRateHz: Int,
): Float? {
    val lagRange = validLagRange(samples, sampleRateHz) ?: return null
    val wholeClipHz = bestAutocorrelationLag(samples, lagRange)?.let { sampleRateHz.toFloat() / it }
    return wholeClipHz ?: medianVoicedFrequencyHz(samples, sampleRateHz, lagRange)
}

private fun medianVoicedFrequencyHz(
    samples: ShortArray,
    sampleRateHz: Int,
    lagRange: IntRange,
): Float? {
    val voicedFrequencies = voicedFrameFrequencies(samples, sampleRateHz, lagRange).sorted()
    return voicedFrequencies.getOrNull(voicedFrequencies.size / 2)
}

// Duración de cada ventana de análisis y el salto entre ventanas consecutivas
// (50% de solape) -- suficientemente corta para que una ventana caiga entera
// dentro de un tramo con voz real, suficientemente larga para contener varios
// períodos del tono buscado (hasta MIN_F0_HZ = 70Hz, un período dura ~14ms).
private const val FRAME_DURATION_MS = 50
private const val FRAME_HOP_MS = 25

private fun voicedFrameFrequencies(
    samples: ShortArray,
    sampleRateHz: Int,
    lagRange: IntRange,
): List<Float> {
    // La ventana tiene que ser bastante más larga que el lag más grande que se
    // busca (si no, casi no quedan muestras para correlacionar en ese
    // extremo) -- se toma la mayor entre la duración objetivo y el doble del
    // lag máximo.
    val frameSize = maxOf(sampleRateHz * FRAME_DURATION_MS / 1000, lagRange.last * 2).coerceAtMost(samples.size)
    val hopSize = maxOf(sampleRateHz * FRAME_HOP_MS / 1000, 1)
    val frequencies = mutableListOf<Float>()
    var pos = 0
    while (pos + frameSize <= samples.size) {
        val frame = samples.copyOfRange(pos, pos + frameSize)
        bestAutocorrelationLag(frame, lagRange)?.let { frequencies.add(sampleRateHz.toFloat() / it) }
        pos += hopSize
    }
    return frequencies
}

private fun validLagRange(
    samples: ShortArray,
    sampleRateHz: Int,
): IntRange? {
    if (sampleRateHz <= 0) return null
    val minLag = sampleRateHz / MAX_F0_HZ
    val maxLag = sampleRateHz / MIN_F0_HZ
    return if (minLag < 1 || maxLag <= minLag || samples.size <= maxLag + minLag) null else minLag..maxLag
}

private fun bestAutocorrelationLag(
    samples: ShortArray,
    lagRange: IntRange,
): Int? {
    var bestLag = -1
    var bestScore = 0.0
    for (lag in lagRange) {
        var dot = 0.0
        var energy = 0.0
        val limit = samples.size - lag
        for (i in 0 until limit) {
            val a = samples[i].toDouble()
            val b = samples[i + lag].toDouble()
            dot += a * b
            energy += a * a
        }
        if (energy <= 0.0) continue
        val score = dot / energy
        if (score > bestScore) {
            bestScore = score
            bestLag = lag
        }
    }
    return if (bestLag <= 0 || bestScore < MIN_VOICED_CORRELATION) null else bestLag
}

// Divisoria estándar entre los rangos típicos de F0 adulto masculino
// (~85-180Hz) y femenino (~165-255Hz) -- la franja 165-180Hz es ambigua por
// naturaleza (hay solapamiento real entre personas), 165Hz es el corte más
// citado en fonética del habla para clasificar en dos grupos.
internal const val GENDER_PITCH_THRESHOLD_HZ = 165f

internal fun isFeminineByPitch(f0: Float): Boolean = f0 >= GENDER_PITCH_THRESHOLD_HZ
