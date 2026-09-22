package com.docsmart.features.study.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingControlsLogicTest {
    @Test
    fun `la velocidad avanza por la lista y vuelve al inicio`() {
        nextReadingSpeed(0.75f) shouldBe 1f
        nextReadingSpeed(1f) shouldBe 1.25f
        nextReadingSpeed(1.5f) shouldBe 2f
        nextReadingSpeed(2f) shouldBe 0.75f
    }

    @Test
    fun `una velocidad guardada desconocida cae al inicio`() {
        nextReadingSpeed(3f) shouldBe 0.75f
    }

    @Test
    fun `la etiqueta de velocidad no lleva ceros sobrantes`() {
        readingSpeedLabel(1f) shouldBe "1×"
        readingSpeedLabel(1.25f) shouldBe "1.25×"
        readingSpeedLabel(0.75f) shouldBe "0.75×"
    }

    @Test
    fun `la velocidad base equivale al factor uno`() {
        (BASE_SPEECH_RATE * 1f) shouldBe 0.85f
    }

    @Test
    fun `saltar de parrafo se acota al documento`() {
        steppedParagraph(current = 3, delta = 1, lastIndex = 9) shouldBe 4
        steppedParagraph(current = 3, delta = -1, lastIndex = 9) shouldBe 2
        steppedParagraph(current = 0, delta = -1, lastIndex = 9) shouldBe 0
        steppedParagraph(current = 9, delta = 1, lastIndex = 9) shouldBe 9
    }

    @Test
    fun `sin lectura iniciada se parte del primer parrafo`() {
        steppedParagraph(current = -1, delta = 1, lastIndex = 9) shouldBe 1
        steppedParagraph(current = -1, delta = -1, lastIndex = 9) shouldBe 0
    }

    @Test
    fun `sin texto no hay a donde saltar`() {
        steppedParagraph(current = -1, delta = 1, lastIndex = -1) shouldBe -1
    }
}
