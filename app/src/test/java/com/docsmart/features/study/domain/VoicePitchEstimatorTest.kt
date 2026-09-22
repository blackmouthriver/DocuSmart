package com.docsmart.features.study.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Fase 4 (2026-09-22), pedido explícito del usuario ("discrimina para que
// voces femeninas tengan personajes femeninos..."): funciones puras de
// estimación de tono, verificadas con una onda senoidal SINTÉTICA de
// frecuencia conocida (no se puede sintetizar audio real de una voz en un
// test JVM puro, pero una senoidal es la señal más simple posible con un
// tono fundamental exacto y verificable) -- mismo criterio de "onda
// sintética" ya usado en este proyecto para verificar el resto de la
// tubería de audio/imagen sin depender de assets reales.
class VoicePitchEstimatorTest {
    private fun sineWave(
        frequencyHz: Float,
        sampleRateHz: Int,
        durationMillis: Int,
        amplitude: Int = 20000,
    ): ShortArray {
        val sampleCount = sampleRateHz * durationMillis / 1000
        return ShortArray(sampleCount) { i ->
            (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRateHz)).toInt().toShort()
        }
    }

    private fun assertCloseTo(
        actual: Float,
        expected: Double,
        toleranceHz: Double,
    ) {
        (abs(actual - expected) <= toleranceHz) shouldBe true
    }

    @Test
    fun `reconoce el tono de una onda grave, rango masculino`() {
        val samples = sineWave(frequencyHz = 120f, sampleRateHz = 16000, durationMillis = 300)

        val f0 = estimateFundamentalFrequencyHz(samples, 16000)

        requireNotNull(f0)
        assertCloseTo(f0, 120.0, toleranceHz = 3.0)
        isFeminineByPitch(f0) shouldBe false
    }

    @Test
    fun `reconoce el tono de una onda aguda, rango femenino`() {
        val samples = sineWave(frequencyHz = 210f, sampleRateHz = 16000, durationMillis = 300)

        val f0 = estimateFundamentalFrequencyHz(samples, 16000)

        requireNotNull(f0)
        assertCloseTo(f0, 210.0, toleranceHz = 4.0)
        isFeminineByPitch(f0) shouldBe true
    }

    // Bug real de dispositivo (2026-09-22): 3 de 11 voces reales daban
    // `f0=null` porque la autocorrelación sobre el clip COMPLETO se diluía
    // con silencio inicial/final -- este test reproduce esa forma de onda
    // (silencio + tono + silencio) para verificar que el análisis por
    // ventanas cortas SÍ encuentra el tono pese al silencio.
    @Test
    fun `un tono rodeado de silencio se detecta igual, aunque el clip completo diluya la correlacion`() {
        val silence = ShortArray(16000 / 4) // 250ms de silencio.
        val voiced = sineWave(frequencyHz = 180f, sampleRateHz = 16000, durationMillis = 300)
        val samples = silence + voiced + silence

        val f0 = estimateFundamentalFrequencyHz(samples, 16000)

        requireNotNull(f0)
        assertCloseTo(f0, 180.0, toleranceHz = 4.0)
    }

    @Test
    fun `un clip demasiado corto no lanza excepcion y devuelve null`() {
        val samples = sineWave(frequencyHz = 150f, sampleRateHz = 16000, durationMillis = 5)

        estimateFundamentalFrequencyHz(samples, 16000) shouldBe null
    }

    @Test
    fun `silencio total no devuelve un tono inventado`() {
        val samples = ShortArray(16000 / 2)

        estimateFundamentalFrequencyHz(samples, 16000) shouldBe null
    }

    @Test
    fun `una frecuencia de muestreo invalida no lanza excepcion`() {
        val samples = sineWave(frequencyHz = 150f, sampleRateHz = 16000, durationMillis = 300)

        estimateFundamentalFrequencyHz(samples, 0) shouldBe null
        estimateFundamentalFrequencyHz(samples, -16000) shouldBe null
    }

    @Test
    fun `la divisoria de genero es exactamente el umbral documentado`() {
        isFeminineByPitch(GENDER_PITCH_THRESHOLD_HZ) shouldBe true
        isFeminineByPitch(GENDER_PITCH_THRESHOLD_HZ - 1f) shouldBe false
    }

    // ── parseWavPcm16 ──────────────────────────────────────────────────────
    private fun buildWavPcm16(
        sampleRateHz: Int,
        channels: Int,
        samples: ShortArray,
    ): ByteArray {
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
        le16(1) // PCM
        le16(channels)
        le32(sampleRateHz)
        le32(sampleRateHz * channels * 2)
        le16(channels * 2)
        le16(16)
        ascii("data")
        le32(dataSize)
        samples.forEach { le16(it.toInt()) }
        return bytes.toByteArray()
    }

    @Test
    fun `parsea un wav mono PCM16 valido`() {
        val samples = shortArrayOf(1, -1, 100, -100, 32000)
        val wav = buildWavPcm16(sampleRateHz = 22050, channels = 1, samples = samples)

        val audio = parseWavPcm16(wav)

        requireNotNull(audio)
        audio.sampleRateHz shouldBe 22050
        audio.samples.toList() shouldBe samples.toList()
    }

    @Test
    fun `un wav estereo se reduce a mono promediando canales`() {
        // Canal izquierdo 10, derecho 20 -> mono 15; izquierdo -10, derecho 10 -> mono 0.
        val stereo = shortArrayOf(10, 20, -10, 10)
        val wav = buildWavPcm16(sampleRateHz = 16000, channels = 2, samples = stereo)

        val audio = parseWavPcm16(wav)

        requireNotNull(audio)
        audio.samples.toList() shouldBe listOf<Short>(15, 0)
    }

    @Test
    fun `un archivo que no es RIFF WAVE devuelve null sin lanzar excepcion`() {
        parseWavPcm16(ByteArray(10)) shouldBe null
        parseWavPcm16("no es un wav de verdad".toByteArray()) shouldBe null
    }

    @Test
    fun `un wav sin chunk data devuelve null`() {
        val bytes = "RIFF????WAVEfmt ".toByteArray(Charsets.US_ASCII)
        parseWavPcm16(bytes) shouldBe null
    }
}
