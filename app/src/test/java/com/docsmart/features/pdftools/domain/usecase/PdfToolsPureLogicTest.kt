package com.docsmart.features.pdftools.domain.usecase

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Ronda 17: decisiones puras extraídas de CompressPdfUseCase, OcrPdfUseCase y
 * SplitPdfUseCase (el resto de cada use case necesita PdfRenderer/ML Kit, que
 * no existen en JVM puro).
 */
class PdfToolsPureLogicTest {
    // ── Comprimir ───────────────────────────────────────────────────────────

    @Test
    fun `reductionPercent calcula el porcentaje ahorrado`() {
        reductionPercent(1000L, 400L) shouldBe 60
        reductionPercent(1000L, 1000L) shouldBe 0
    }

    @Test
    fun `reductionPercent trunca en vez de redondear`() {
        reductionPercent(3L, 2L) shouldBe 33
    }

    @Test
    fun `reductionPercent es negativo si el resultado creció`() {
        reductionPercent(1000L, 1200L) shouldBe -20
    }

    @Test
    fun `reductionPercent con original vacio no divide por cero`() {
        reductionPercent(0L, 10L) shouldBe 0
    }

    @Test
    fun `shouldKeepOriginal conserva el original si el comprimido no es mas chico`() {
        shouldKeepOriginal(100L, 100L) shouldBe true
        shouldKeepOriginal(100L, 101L) shouldBe true
        shouldKeepOriginal(100L, 99L) shouldBe false
    }

    // ── OCR ─────────────────────────────────────────────────────────────────

    @Test
    fun `ocrOutcomeFor un archivo vacio es error de generacion aunque no haya paginas`() {
        ocrOutcomeFor(0L, 0, 0) shouldBe OcrOutcome.GENERATE_ERROR
        ocrOutcomeFor(0L, 3, 10) shouldBe OcrOutcome.GENERATE_ERROR
    }

    @Test
    fun `ocrOutcomeFor sin paginas procesadas el PDF ya tenia texto`() {
        ocrOutcomeFor(500L, 0, 0) shouldBe OcrOutcome.ALREADY_HAS_TEXT
    }

    @Test
    fun `ocrOutcomeFor con paginas pero sin palabras no reconocio nada`() {
        ocrOutcomeFor(500L, 2, 0) shouldBe OcrOutcome.NO_TEXT_FOUND
    }

    @Test
    fun `ocrOutcomeFor con paginas y palabras es exito`() {
        ocrOutcomeFor(500L, 2, 15) shouldBe OcrOutcome.SUCCESS
    }

    @Test
    fun `ocrFontSizeFor acota la altura a un rango razonable`() {
        ocrFontSizeFor(1f) shouldBe 2f
        ocrFontSizeFor(12f) shouldBe 12f
        ocrFontSizeFor(500f) shouldBe 200f
    }

    @Test
    fun `ocrFontSizeFor respeta limites personalizados`() {
        ocrFontSizeFor(5f, minSize = 8f, maxSize = 20f) shouldBe 8f
        ocrFontSizeFor(30f, minSize = 8f, maxSize = 20f) shouldBe 20f
    }

    // ── Dividir ─────────────────────────────────────────────────────────────

    @Test
    fun `clampSplitRange deja un rango valido tal cual`() {
        clampSplitRange(1, 2, 10) shouldBe (1 to 2)
    }

    @Test
    fun `clampSplitRange sube desde a la primera pagina`() {
        clampSplitRange(0, 2, 10) shouldBe (1 to 2)
        clampSplitRange(-5, 3, 10) shouldBe (1 to 3)
    }

    @Test
    fun `clampSplitRange corrige un rango invertido a una sola pagina`() {
        clampSplitRange(5, 3, 10) shouldBe (5 to 5)
    }

    @Test
    fun `clampSplitRange acota hasta al total de paginas`() {
        clampSplitRange(8, 50, 10) shouldBe (8 to 10)
    }

    @Test
    fun `clampSplitRange con desde mayor al total usa la ultima pagina`() {
        clampSplitRange(20, 30, 10) shouldBe (10 to 10)
    }

    @Test
    fun `clampSplitRange en un PDF de una pagina siempre devuelve esa pagina`() {
        clampSplitRange(1, 2, 1) shouldBe (1 to 1)
        clampSplitRange(3, 3, 1) shouldBe (1 to 1)
    }
}
