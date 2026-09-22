package com.docsmart.features.study.domain

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

// Parte Android-específica de la detección de género por tono (ver
// VoicePitchEstimator.kt para la parte pura/testeable) -- no es testeable en
// JVM puro (necesita un `TextToSpeech` real), mismo límite ya documentado en
// este proyecto para el resto de `OcrPdfUseCase`.

private const val PROBE_UTTERANCE_ID = "study_gender_probe"

// Frase corta pero con suficiente señal sonora (vocales sostenidas) para que
// la autocorrelación tenga con qué trabajar -- no es texto que el usuario
// vaya a escuchar nunca, solo se analiza el archivo WAV resultante.
private const val PROBE_TEXT = "Hola, ¿cómo estás?"

/** Sintetiza [voice] diciendo una frase corta a [outputFile] (WAV) y espera a que termine. */
private suspend fun synthesizeToWavFile(
    tts: TextToSpeech,
    voice: Voice,
    outputFile: File,
): Boolean =
    suspendCancellableCoroutine { cont ->
        val listener =
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == PROBE_UTTERANCE_ID && cont.isActive) cont.resume(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == PROBE_UTTERANCE_ID && cont.isActive) cont.resume(false)
                }
            }
        tts.setOnUtteranceProgressListener(listener)
        tts.voice = voice
        val queued = tts.synthesizeToFile(PROBE_TEXT, null, outputFile, PROBE_UTTERANCE_ID)
        if (queued != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
    }

/**
 * Sintetiza una muestra corta de [voice] y estima si es femenina por su tono
 * fundamental (ver VoicePitchEstimator.kt). `null` si algo falla (motor no
 * disponible, archivo vacío o corrupto, audio sin suficiente señal sonora
 * para estimar el tono) -- el llamador cae al reparto sin discriminar por
 * género en ese caso, nunca a una excepción.
 */
internal suspend fun detectIsFeminineVoice(
    tts: TextToSpeech,
    voice: Voice,
    workFile: File,
): Boolean? =
    try {
        if (!synthesizeToWavFile(tts, voice, workFile)) {
            null
        } else {
            val audio = parseWavPcm16(workFile.readBytes())
            val f0 = audio?.let { estimateFundamentalFrequencyHz(it.samples, it.sampleRateHz) }
            f0?.let { isFeminineByPitch(it) }
        }
    } catch (e: Exception) {
        Timber.e("Estudio: error detectando el género de ${voice.name} (${e.javaClass.simpleName})")
        null
    } finally {
        workFile.delete()
    }
