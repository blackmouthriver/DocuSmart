package com.docsmart.features.pdftools.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PdfToolsUiLogicTest {
    // ── Combinar ────────────────────────────────────────────────────────────

    @Test
    fun `Combinar exige al menos dos PDFs`() {
        canExecuteMerge(0) shouldBe false
        canExecuteMerge(1) shouldBe false
        canExecuteMerge(2) shouldBe true
        canExecuteMerge(7) shouldBe true
    }

    // ── Dividir ─────────────────────────────────────────────────────────────

    @Test
    fun `splitPageCount cuenta ambos extremos del rango`() {
        splitPageCount(1, 1) shouldBe 1
        splitPageCount(2, 5) shouldBe 4
    }

    @Test
    fun `splitPageCount de un rango invertido es cero y no negativo`() {
        splitPageCount(5, 2) shouldBe 0
    }

    @Test
    fun `un rango de una sola pagina es valido y solo el invertido no`() {
        isInvalidSplitRange(3, 3) shouldBe false
        isInvalidSplitRange(2, 3) shouldBe false
        isInvalidSplitRange(4, 3) shouldBe true
    }

    @Test
    fun `canExecuteSplit necesita PDF y rango valido`() {
        canExecuteSplit(hasPdf = true, fromPage = 1, toPage = 1) shouldBe true
        canExecuteSplit(hasPdf = true, fromPage = 4, toPage = 3) shouldBe false
        canExecuteSplit(hasPdf = false, fromPage = 1, toPage = 3) shouldBe false
    }

    @Test
    fun `los botones menos de Dividir respetan sus pisos`() {
        canDecreaseSplitFrom(1) shouldBe false
        canDecreaseSplitFrom(2) shouldBe true
        canDecreaseSplitTo(fromPage = 3, toPage = 3) shouldBe false
        canDecreaseSplitTo(fromPage = 3, toPage = 4) shouldBe true
    }

    // ── Condiciones de ejecucion por herramienta ────────────────────────────

    @Test
    fun `Editar texto ignora busquedas vacias o en blanco`() {
        canExecuteEditText(true, "hola") shouldBe true
        canExecuteEditText(true, "") shouldBe false
        canExecuteEditText(true, "   ") shouldBe false
        canExecuteEditText(false, "hola") shouldBe false
    }

    @Test
    fun `Marca de agua ignora textos vacios o en blanco`() {
        canExecuteWatermark(true, "BORRADOR") shouldBe true
        canExecuteWatermark(true, " ") shouldBe false
        canExecuteWatermark(false, "BORRADOR") shouldBe false
    }

    @Test
    fun `Rellenar formulario necesita campos detectados`() {
        canExecuteFillForm(true, 3) shouldBe true
        canExecuteFillForm(true, 0) shouldBe false
        canExecuteFillForm(false, 3) shouldBe false
    }

    @Test
    fun `Censurar necesita al menos un rectangulo`() {
        canExecuteRedact(true, 1) shouldBe true
        canExecuteRedact(true, 0) shouldBe false
        canExecuteRedact(false, 1) shouldBe false
    }

    @Test
    fun `Firmar necesita firma capturada`() {
        canExecuteSign(true, true) shouldBe true
        canExecuteSign(true, false) shouldBe false
        canExecuteSign(false, true) shouldBe false
    }

    @Test
    fun `Reordenar con una sola pagina no se ofrece`() {
        canExecuteReorder(true, 2) shouldBe true
        canExecuteReorder(true, 1) shouldBe false
        canExecuteReorder(false, 5) shouldBe false
    }

    @Test
    fun `Comparar necesita los dos PDFs`() {
        canExecuteCompare(hasPdfA = true, hasPdfB = true) shouldBe true
        canExecuteCompare(hasPdfA = true, hasPdfB = false) shouldBe false
        canExecuteCompare(hasPdfA = false, hasPdfB = true) shouldBe false
    }

    @Test
    fun `no se puede quitar la ultima pagina`() {
        canRemovePage(2) shouldBe true
        canRemovePage(1) shouldBe false
    }

    // ── Navegacion de paginas ───────────────────────────────────────────────

    @Test
    fun `la navegacion de paginas respeta ambos extremos`() {
        canGoToPreviousPage(1) shouldBe false
        canGoToPreviousPage(2) shouldBe true
        canGoToNextPage(currentPage = 3, totalPages = 3) shouldBe false
        canGoToNextPage(currentPage = 2, totalPages = 3) shouldBe true
    }
}
