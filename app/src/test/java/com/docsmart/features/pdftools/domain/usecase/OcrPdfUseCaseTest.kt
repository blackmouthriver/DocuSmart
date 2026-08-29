package com.docsmart.features.pdftools.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `OcrPdfUseCase` en sí depende de `android.graphics.pdf.PdfRenderer` y de
 * ML Kit (Text Recognition), ambos solo disponibles en runtime Android
 * real -- no se puede ejercitar en un test unitario JVM puro (mismo límite
 * ya documentado para `CompressPdfUseCase`, ver docs/requirements/pdf-tools.md
 * §7). Lo que sí es JVM puro y merece cobertura real es la conversión de
 * coordenadas de un bounding box de OCR a puntos de PDF, y el cálculo de
 * escalado horizontal -- ambas son funciones internas sin dependencias de
 * Android/iText, extraídas específicamente para poder testearlas.
 */
class OcrPdfUseCaseTest {

    @Test
    fun `mapOcrBoxToPdf convierte un box en la esquina superior izquierda del bitmap`() {
        // Página de 100x200 puntos, renderizada a escala 2x (bitmap de 200x400 px).
        // Un box en (0,0)-(20,10) px queda en la esquina superior-izquierda visual.
        val placement = mapOcrBoxToPdf(
            text = "Hola",
            box = OcrBoxPx(left = 0, top = 0, right = 20, bottom = 10),
            geometry = PdfPageGeometry(x = 0f, y = 0f, height = 200f, renderScale = 2f)
        )

        // x = 0/2 = 0
        assertEquals(0f, placement.x, 0.001f)
        // yBaseline = 0 + 200 - 10/2 = 195 (cerca del techo de la página, como se espera)
        assertEquals(195f, placement.yBaseline, 0.001f)
        assertEquals(10f, placement.widthPts, 0.001f)
        assertEquals(5f, placement.heightPts, 0.001f)
    }

    @Test
    fun `mapOcrBoxToPdf convierte un box cerca del pie de la pagina`() {
        // Bitmap de 200x400 px (escala 2x de una página de 100x200 pts).
        // Box en (10,390)-(50,400) px -- pegado al borde inferior del bitmap.
        val placement = mapOcrBoxToPdf(
            text = "Pie",
            box = OcrBoxPx(left = 10, top = 390, right = 50, bottom = 400),
            geometry = PdfPageGeometry(x = 0f, y = 0f, height = 200f, renderScale = 2f)
        )

        assertEquals(5f, placement.x, 0.001f)
        // yBaseline = 200 - 400/2 = 0 (base de la página, como se espera)
        assertEquals(0f, placement.yBaseline, 0.001f)
        assertEquals(20f, placement.widthPts, 0.001f)
        assertEquals(5f, placement.heightPts, 0.001f)
    }

    @Test
    fun `mapOcrBoxToPdf respeta el origen de pagina cuando x e y no son cero`() {
        val placement = mapOcrBoxToPdf(
            text = "Offset",
            box = OcrBoxPx(left = 0, top = 0, right = 10, bottom = 10),
            geometry = PdfPageGeometry(x = 50f, y = 30f, height = 100f, renderScale = 1f)
        )

        assertEquals(50f, placement.x, 0.001f)
        assertEquals(30f + 100f - 10f, placement.yBaseline, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent devuelve 100 si el ancho natural coincide con el objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 40f, targetWidthPts = 40f)
        assertEquals(100f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent comprime el texto si el ancho natural es mayor al objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 80f, targetWidthPts = 40f)
        assertEquals(50f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent estira el texto si el ancho natural es menor al objetivo`() {
        val percent = horizontalScalingPercent(naturalWidthPts = 20f, targetWidthPts = 40f)
        assertEquals(200f, percent, 0.001f)
    }

    @Test
    fun `horizontalScalingPercent devuelve 100 sin dividir por cero con anchos invalidos`() {
        assertEquals(100f, horizontalScalingPercent(0f, 40f), 0.001f)
        assertEquals(100f, horizontalScalingPercent(40f, 0f), 0.001f)
        assertEquals(100f, horizontalScalingPercent(-5f, 40f), 0.001f)
    }

    @Test
    fun `horizontalScalingPercent se limita al rango 1 a 500 por ciento`() {
        assertEquals(500f, horizontalScalingPercent(naturalWidthPts = 1f, targetWidthPts = 1000f), 0.001f)
        assertEquals(1f, horizontalScalingPercent(naturalWidthPts = 1000f, targetWidthPts = 1f), 0.001f)
    }
}
