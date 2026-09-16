package com.docsmart.features.viewer.domain.annotation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

private const val EPS = 0.01f

/**
 * HU-46: la conversión pantalla↔puntos PDF es el mecanismo geométrico que
 * decide dónde queda guardado un resaltado/nota, y dónde se vuelve a
 * dibujar al reabrir el documento -- si esta matemática está mal, la
 * anotación "se mueve" cada vez que se recalcula su posición en pantalla.
 */
class AnnotationCoordinatesTest {

    // Página de 600×800 puntos PDF, mostrada a 300px de ancho → escala 0.5.
    private val displayScale  = 0.5f
    private val pageHeightPts = 800f

    @Test
    fun `screenDragToPdfRect - arrastre de arriba-izquierda a abajo-derecha`() {
        // 300,200 (screen) → 800,400 (screen), escala 0.5
        val rect = screenDragToPdfRect(300f, 200f, 800f, 400f, displayScale, pageHeightPts)

        assertEquals(600f, rect.xPts, EPS)
        assertEquals(0f, rect.yPts, EPS) // pageHeightPts - maxScreenY/scale = 800 - 400/0.5 = 0
        assertEquals(1000f, rect.widthPts, EPS)
        assertEquals(400f, rect.heightPts, EPS)
    }

    @Test
    fun `screenDragToPdfRect da el mismo resultado sin importar el orden de los dos puntos`() {
        val forward  = screenDragToPdfRect(100f, 150f, 300f, 250f, displayScale, pageHeightPts)
        val backward = screenDragToPdfRect(300f, 250f, 100f, 150f, displayScale, pageHeightPts)

        assertEquals(forward, backward)
    }

    @Test
    fun `screenPointToPdfPoint y pdfPointToScreenPoint son inversas exactas`() {
        val originalScreenX = 240f
        val originalScreenY = 360f

        val pdfPoint = screenPointToPdfPoint(originalScreenX, originalScreenY, displayScale, pageHeightPts)
        val (backX, backY) = pdfPointToScreenPoint(pdfPoint.xPts, pdfPoint.yPts, displayScale, pageHeightPts)

        assertTrue(abs(backX - originalScreenX) < EPS)
        assertTrue(abs(backY - originalScreenY) < EPS)
    }

    @Test
    fun `screenPointToPdfPoint produce un rect de tamano cero (es un punto de anclaje)`() {
        val point = screenPointToPdfPoint(50f, 60f, displayScale, pageHeightPts)

        assertEquals(0f, point.widthPts, EPS)
        assertEquals(0f, point.heightPts, EPS)
    }

    @Test
    fun `isValidHighlightSize rechaza un rectangulo casi de tamano cero`() {
        val tiny = screenDragToPdfRect(100f, 100f, 101f, 101f, displayScale, pageHeightPts)

        assertFalse(isValidHighlightSize(tiny))
    }

    @Test
    fun `isValidHighlightSize acepta un rectangulo de tamano real`() {
        val real = screenDragToPdfRect(100f, 100f, 300f, 200f, displayScale, pageHeightPts)

        assertTrue(isValidHighlightSize(real))
    }

    @Test
    fun `isValidHighlightSize - justo por debajo del umbral se rechaza`() {
        val justBelow = PdfRectPts(
            xPts = 0f, yPts = 0f, widthPts = MIN_HIGHLIGHT_SIZE_PTS - 0.01f, heightPts = MIN_HIGHLIGHT_SIZE_PTS
        )

        assertFalse(isValidHighlightSize(justBelow))
    }

    @Test
    fun `isValidHighlightSize - justo en el umbral se acepta (inclusive)`() {
        val atThreshold = PdfRectPts(
            xPts = 0f, yPts = 0f, widthPts = MIN_HIGHLIGHT_SIZE_PTS, heightPts = MIN_HIGHLIGHT_SIZE_PTS
        )

        assertTrue(isValidHighlightSize(atThreshold))
    }

    // ── visualRectToRawPageRect (hallazgo #16, revisión general 2026-09-16) ──
    // Página cruda de 200×300 puntos (rawPageWidthPts=200, rawPageHeightPts=300)
    // en los 4 casos, para poder comparar los resultados entre rotaciones.
    // Valores esperados calculados a mano con las fórmulas derivadas de una
    // matriz de rotación clockwise estándar -- ver el comentario de
    // `visualRectToRawPageRect` en PdfRectPts.kt.

    @Test
    fun `visualRectToRawPageRect con rotacion 0 no transforma nada`() {
        val visual = PdfRectPts(xPts = 5f, yPts = 6f, widthPts = 7f, heightPts = 8f)

        val raw = visualRectToRawPageRect(visual, rotationDegrees = 0, rawPageWidthPts = 200f, rawPageHeightPts = 300f)

        assertEquals(visual.xPts, raw.x, EPS)
        assertEquals(visual.yPts, raw.y, EPS)
        assertEquals(visual.widthPts, raw.width, EPS)
        assertEquals(visual.heightPts, raw.height, EPS)
    }

    @Test
    fun `visualRectToRawPageRect con rotacion 90 intercambia ejes y ubica el rectangulo correctamente`() {
        val visual = PdfRectPts(xPts = 10f, yPts = 180f, widthPts = 20f, heightPts = 10f)

        val raw = visualRectToRawPageRect(visual, rotationDegrees = 90, rawPageWidthPts = 200f, rawPageHeightPts = 300f)

        assertEquals(10f, raw.x, EPS)
        assertEquals(10f, raw.y, EPS)
        assertEquals(10f, raw.width, EPS)  // ancho crudo = alto visual
        assertEquals(20f, raw.height, EPS) // alto crudo = ancho visual
    }

    @Test
    fun `visualRectToRawPageRect con rotacion 180 refleja ambos ejes sin intercambiarlos`() {
        val visual = PdfRectPts(xPts = 10f, yPts = 20f, widthPts = 30f, heightPts = 40f)

        val raw = visualRectToRawPageRect(
            visual, rotationDegrees = 180, rawPageWidthPts = 200f, rawPageHeightPts = 300f
        )

        assertEquals(160f, raw.x, EPS)
        assertEquals(240f, raw.y, EPS)
        assertEquals(30f, raw.width, EPS)
        assertEquals(40f, raw.height, EPS)
    }

    @Test
    fun `visualRectToRawPageRect con rotacion 270 intercambia ejes en sentido opuesto a 90`() {
        val visual = PdfRectPts(xPts = 10f, yPts = 20f, widthPts = 30f, heightPts = 40f)

        val raw = visualRectToRawPageRect(
            visual, rotationDegrees = 270, rawPageWidthPts = 200f, rawPageHeightPts = 300f
        )

        assertEquals(20f, raw.x, EPS)
        assertEquals(260f, raw.y, EPS)
        assertEquals(40f, raw.width, EPS)
        assertEquals(30f, raw.height, EPS)
    }

    @Test
    fun `visualRectToRawPageRect normaliza rotaciones negativas y mayores a 360`() {
        val visual = PdfRectPts(xPts = 10f, yPts = 180f, widthPts = 20f, heightPts = 10f)

        // -270 y 450 son ambos equivalentes a 90 grados.
        val fromNegative = visualRectToRawPageRect(
            visual, rotationDegrees = -270, rawPageWidthPts = 200f, rawPageHeightPts = 300f
        )
        val fromOver360 = visualRectToRawPageRect(
            visual, rotationDegrees = 450, rawPageWidthPts = 200f, rawPageHeightPts = 300f
        )
        val expected = visualRectToRawPageRect(
            visual, rotationDegrees = 90, rawPageWidthPts = 200f, rawPageHeightPts = 300f
        )

        assertEquals(expected, fromNegative)
        assertEquals(expected, fromOver360)
    }
}
