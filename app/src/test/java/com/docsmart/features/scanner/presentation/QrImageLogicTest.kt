package com.docsmart.features.scanner.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class QrImageLogicTest {
    @Test
    fun `isQrCacheFileStale respeta el limite de una hora`() {
        val now = 10 * QR_CACHE_MAX_AGE_MILLIS

        isQrCacheFileStale(now - QR_CACHE_MAX_AGE_MILLIS - 1, now) shouldBe true
        isQrCacheFileStale(now - QR_CACHE_MAX_AGE_MILLIS, now) shouldBe false
        isQrCacheFileStale(now, now) shouldBe false
    }

    @Test
    fun `computeSampleSize no reduce imagenes pequenas`() {
        computeSampleSize(400, 300, 512) shouldBe 1
        computeSampleSize(512, 512, 512) shouldBe 1
    }

    @Test
    fun `computeSampleSize usa potencias de 2 sin bajar de la meta`() {
        computeSampleSize(1024, 1024, 512) shouldBe 2
        computeSampleSize(4000, 3000, 512) shouldBe 4
        computeSampleSize(4096, 4096, 512) shouldBe 8
    }

    @Test
    fun `computeSampleSize considera el lado mayor`() {
        computeSampleSize(4096, 100, 512) shouldBe 8
        computeSampleSize(100, 4096, 512) shouldBe 8
    }

    @Test
    fun `computeSampleSize con dimensiones invalidas devuelve 1`() {
        computeSampleSize(-1, -1, 512) shouldBe 1
        computeSampleSize(0, 0, 512) shouldBe 1
    }
}
