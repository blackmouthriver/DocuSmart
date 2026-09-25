package com.docsmart.features.study.domain

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * `detectIsFeminineVoice()`/`synthesizeToWavFile()` (VoiceGenderProbe.kt) son la única parte de la
 * detección de género de voz que necesita un `TextToSpeech` real -- el resto (parseWavPcm16,
 * estimateFundamentalFrequencyHz, isFeminineByPitch) ya está cubierto en JVM puro por
 * VoicePitchEstimatorTest con una onda sintética. El emulador de CI no tiene motor TTS (regla de
 * estabilidad #5), así que acá NO se sintetiza audio real: se mockea `TextToSpeech` con mockk-android
 * (mismo patrón ya usado en BillingManagerConnectionTest para clases de Android) y se simula el
 * callback de `synthesizeToFile()` a mano, escribiendo un WAV sintético real en el archivo de
 * trabajo -- ejercita el código real de esta clase (el puente entre el motor y el análisis de
 * tono) sin depender de que exista un motor de voz instalado.
 */
class VoiceGenderProbeInstrumentedTest {
    private val cacheDir get() = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val workFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        workFiles.forEach { it.delete() }
    }

    private fun newWorkFile(): File = File(cacheDir, "voice_gender_probe_test_${System.nanoTime()}.wav").also { workFiles += it }

    private fun testVoice(name: String = "voz-de-prueba") =
        Voice(name, Locale.forLanguageTag("es-ES"), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet())

    /** WAV PCM16 mono válido con una onda senoidal de [frequencyHz] -- mismo generador que VoicePitchEstimatorTest. */
    private fun sineWavBytes(
        frequencyHz: Float,
        sampleRateHz: Int = 16000,
        durationMillis: Int = 300,
    ): ByteArray {
        val sampleCount = sampleRateHz * durationMillis / 1000
        val amplitude = 20000
        val samples =
            ShortArray(sampleCount) { i ->
                (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRateHz)).toInt().toShort()
            }
        val dataSize = samples.size * 2
        val bytes = mutableListOf<Byte>()

        fun ascii(s: String) = s.forEach { bytes.add(it.code.toByte()) }

        fun le32(v: Int) {
            bytes.add((v and 0xFF).toByte())
            bytes.add(((v shr 8) and 0xFF).toByte())
            bytes.add(((v shr 16) and 0xFF).toByte())
            bytes.add(((v shr 24) and 0xFF).toByte())
        }

        fun le16(v: Int) {
            bytes.add((v and 0xFF).toByte())
            bytes.add(((v shr 8) and 0xFF).toByte())
        }
        ascii("RIFF")
        le32(36 + dataSize)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)
        le16(1)
        le16(1)
        le32(sampleRateHz)
        le32(sampleRateHz * 2)
        le16(2)
        le16(16)
        ascii("data")
        le32(dataSize)
        samples.forEach { le16(it.toInt()) }
        return bytes.toByteArray()
    }

    /** TTS mockeado: `setOnUtteranceProgressListener` se captura para poder disparar onDone/onError a mano. */
    private fun mockTts(): Pair<TextToSpeech, CapturingSlot<UtteranceProgressListener>> {
        val tts = mockk<TextToSpeech>(relaxed = true)
        val listenerSlot = slot<UtteranceProgressListener>()
        // setOnUtteranceProgressListener() devuelve TextToSpeech.SUCCESS/ERROR (int), no Unit.
        every { tts.setOnUtteranceProgressListener(capture(listenerSlot)) } returns TextToSpeech.SUCCESS
        every { tts.voice = any() } just Runs
        return tts to listenerSlot
    }

    @Test
    fun `una voz aguda con sintesis exitosa se detecta como femenina`() =
        runBlocking {
            val (tts, listenerSlot) = mockTts()
            val fileSlot = slot<File>()
            val idSlot = slot<String>()
            every { tts.synthesizeToFile(any(), any(), capture(fileSlot), capture(idSlot)) } answers {
                fileSlot.captured.writeBytes(sineWavBytes(frequencyHz = 210f))
                listenerSlot.captured.onDone(idSlot.captured)
                TextToSpeech.SUCCESS
            }
            val workFile = newWorkFile()

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertEquals(true, result)
            assertFalse("el archivo de trabajo debe borrarse siempre", workFile.exists())
        }

    @Test
    fun `una voz grave con sintesis exitosa se detecta como masculina`() =
        runBlocking {
            val (tts, listenerSlot) = mockTts()
            val fileSlot = slot<File>()
            val idSlot = slot<String>()
            every { tts.synthesizeToFile(any(), any(), capture(fileSlot), capture(idSlot)) } answers {
                fileSlot.captured.writeBytes(sineWavBytes(frequencyHz = 120f))
                listenerSlot.captured.onDone(idSlot.captured)
                TextToSpeech.SUCCESS
            }
            val workFile = newWorkFile()

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertEquals(false, result)
            assertFalse(workFile.exists())
        }

    @Test
    fun `si el motor no acepta encolar la sintesis devuelve null sin invocar el listener`() =
        runBlocking {
            val (tts, _) = mockTts()
            every {
                tts.synthesizeToFile(any<CharSequence>(), any<Bundle>(), any<File>(), any<String>())
            } returns TextToSpeech.ERROR
            val workFile = newWorkFile()

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertNull(result)
            assertFalse(workFile.exists())
        }

    @Test
    fun `si el motor reporta onError la sintesis se da por fallida`() =
        runBlocking {
            val (tts, listenerSlot) = mockTts()
            val idSlot = slot<String>()
            every {
                tts.synthesizeToFile(any<CharSequence>(), any<Bundle>(), any<File>(), capture(idSlot))
            } answers {
                listenerSlot.captured.onError(idSlot.captured)
                TextToSpeech.SUCCESS
            }
            val workFile = newWorkFile()

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertNull(result)
            assertFalse(workFile.exists())
        }

    @Test
    fun `un wav corrupto en el archivo resultante no lanza excepcion y devuelve null`() =
        runBlocking {
            val (tts, listenerSlot) = mockTts()
            val fileSlot = slot<File>()
            val idSlot = slot<String>()
            every { tts.synthesizeToFile(any(), any(), capture(fileSlot), capture(idSlot)) } answers {
                fileSlot.captured.writeBytes("no es un wav de verdad".toByteArray())
                listenerSlot.captured.onDone(idSlot.captured)
                TextToSpeech.SUCCESS
            }
            val workFile = newWorkFile()

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertNull(result)
            assertFalse(workFile.exists())
        }

    @Test
    fun `una excepcion durante la sintesis se captura, devuelve null y borra igual el archivo`() =
        runBlocking {
            val (tts, _) = mockTts()
            every {
                tts.synthesizeToFile(any<CharSequence>(), any<Bundle>(), any<File>(), any<String>())
            } throws RuntimeException("motor caido")
            val workFile = newWorkFile()
            // El archivo ya existía con contenido de una corrida anterior -- debe borrarse igual.
            workFile.writeBytes(byteArrayOf(1, 2, 3))

            val result = detectIsFeminineVoice(tts, testVoice(), workFile)

            assertNull(result)
            assertFalse(workFile.exists())
        }
}
