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
}
