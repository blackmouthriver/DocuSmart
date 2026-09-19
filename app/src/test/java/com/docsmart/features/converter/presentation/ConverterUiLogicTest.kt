package com.docsmart.features.converter.presentation

import com.docsmart.features.converter.domain.model.ConversionType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConverterUiLogicTest {
    // ── Contador de limite diario ───────────────────────────────────────────

    @Test
    fun `conversionLimitProgress es la fraccion usada`() {
        conversionLimitProgress(1, 4) shouldBe 0.25f
        conversionLimitProgress(2, 4) shouldBe 0.5f
    }

    @Test
    fun `conversionLimitProgress se acota a 1 al pasar el limite`() {
        conversionLimitProgress(4, 4) shouldBe 1f
        conversionLimitProgress(9, 4) shouldBe 1f
    }

    @Test
    fun `conversionLimitProgress con limite cero cuenta como agotado sin dividir por cero`() {
        conversionLimitProgress(1, 0) shouldBe 1f
    }

    @Test
    fun `conversionLimitLevel distingue los tres estados`() {
        conversionLimitLevel(0f) shouldBe LimitLevel.OK
        conversionLimitLevel(0.59f) shouldBe LimitLevel.OK
        conversionLimitLevel(0.6f) shouldBe LimitLevel.WARNING
        conversionLimitLevel(0.99f) shouldBe LimitLevel.WARNING
        conversionLimitLevel(1f) shouldBe LimitLevel.REACHED
    }

    @Test
    fun `con el limite gratuito de 3 conversiones el color cambia en la segunda y la tercera`() {
        conversionLimitLevel(conversionLimitProgress(1, 3)) shouldBe LimitLevel.OK
        conversionLimitLevel(conversionLimitProgress(2, 3)) shouldBe LimitLevel.WARNING
        conversionLimitLevel(conversionLimitProgress(3, 3)) shouldBe LimitLevel.REACHED
    }

    // ── Tipo inicial de navegacion ──────────────────────────────────────────

    @Test
    fun `resolveInitialType resuelve un tipo valido cuando no hay uno elegido`() {
        resolveInitialType("PDF_TO_TXT", null) shouldBe ConversionType.PDF_TO_TXT
    }

    @Test
    fun `resolveInitialType no pisa una eleccion manual`() {
        resolveInitialType("PDF_TO_TXT", ConversionType.IMAGE_TO_PDF) shouldBe null
    }

    @Test
    fun `resolveInitialType ignora nulos y nombres que no existen`() {
        resolveInitialType(null, null) shouldBe null
        resolveInitialType("NO_EXISTE", null) shouldBe null
        resolveInitialType("pdf_to_txt", null) shouldBe null
    }

    // ── Categorias y MIME ───────────────────────────────────────────────────

    @Test
    fun `cada ConversionType cae en una categoria conocida`() {
        val categorias = setOf("Imagen", "PDF", "Word", "Excel", "PowerPoint")

        ConversionType.entries.forEach { type ->
            (type.getCategoryForUi() in categorias) shouldBe true
        }
    }

    @Test
    fun `la categoria coincide con el formato de origen de cada tipo`() {
        // fromFormat es "Imagen"/"PDF"/"Word"/"Excel"/"PowerPoint" para todos los tipos.
        ConversionType.entries.forEach { type ->
            type.getCategoryForUi() shouldBe type.fromFormat
        }
    }

    @Test
    fun `getCategoryForUi de ejemplos concretos`() {
        ConversionType.IMAGE_TO_PDF.getCategoryForUi() shouldBe "Imagen"
        ConversionType.PDF_TO_IMAGE.getCategoryForUi() shouldBe "PDF"
        ConversionType.WORD_TO_HTML.getCategoryForUi() shouldBe "Word"
        ConversionType.EXCEL_TO_CSV.getCategoryForUi() shouldBe "Excel"
        ConversionType.PPT_TO_TXT.getCategoryForUi() shouldBe "PowerPoint"
    }

    @Test
    fun `los tipos con origen imagen piden image y los de origen PDF piden application pdf`() {
        ConversionType.entries.filter { it.fromFormat == "Imagen" }.forEach {
            getMimeForType(it) shouldBe "image/*"
        }
        ConversionType.entries.filter { it.fromFormat == "PDF" }.forEach {
            getMimeForType(it) shouldBe "application/pdf"
        }
    }

    // Hallazgo #25 (latente): "application/msword" filtraba fuera los .docx reales.
    @Test
    fun `Word Excel y PowerPoint piden cualquier MIME para no filtrar formatos modernos`() {
        ConversionType.entries
            .filter { it.fromFormat in setOf("Word", "Excel", "PowerPoint") }
            .forEach { getMimeForType(it) shouldBe "*/*" }
    }
}
