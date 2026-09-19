package com.docsmart.features.pdftools.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PdfToolsLogicTest {
    @Test
    fun `parseInitialPdfTool resuelve un nombre valido del enum`() {
        parseInitialPdfTool("OCR") shouldBe PdfTool.OCR
        parseInitialPdfTool("SIGN") shouldBe PdfTool.SIGN
    }

    @Test
    fun `parseInitialPdfTool devuelve null para nulo o nombres desconocidos`() {
        parseInitialPdfTool(null) shouldBe null
        parseInitialPdfTool("") shouldBe null
        parseInitialPdfTool("ocr") shouldBe null
        parseInitialPdfTool("INEXISTENTE") shouldBe null
    }

    @Test
    fun `parseInitialPdfTool rechaza NONE porque no es una herramienta real`() {
        parseInitialPdfTool("NONE") shouldBe null
    }

    @Test
    fun `shouldShowUsageCounter solo aplica a usuarios free con al menos un uso`() {
        shouldShowUsageCounter(isPremium = false, useCount = 1) shouldBe true
        shouldShowUsageCounter(isPremium = false, useCount = 0) shouldBe false
        shouldShowUsageCounter(isPremium = true, useCount = 5) shouldBe false
    }

    @Test
    fun `isUsageLimitReached se activa al llegar al limite`() {
        isUsageLimitReached(2, 3) shouldBe false
        isUsageLimitReached(3, 3) shouldBe true
        isUsageLimitReached(4, 3) shouldBe true
    }

    @Test
    fun `fileSizeKb trunca los bytes a KB`() {
        fileSizeKb(0L) shouldBe 0L
        fileSizeKb(1023L) shouldBe 0L
        fileSizeKb(1024L) shouldBe 1L
        fileSizeKb(5 * 1024L + 500L) shouldBe 5L
    }
}
