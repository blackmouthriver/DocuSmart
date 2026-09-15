package com.docsmart.features.viewer.domain.annotation

/**
 * HU-46: conversión pura (sin Compose/Android) entre puntos de pantalla y
 * puntos PDF (origen inferior-izquierda) -- mismo sistema de coordenadas
 * que `PdfMatchRect`/`pdfSearchHighlights`, extraída a una función testeable
 * en vez de vivir solo dentro del `pointerInput` de `PdfViewerContent`.
 */
data class PdfRectPts(
    val xPts     : Float,
    val yPts     : Float,
    val widthPts : Float,
    val heightPts: Float
)

// Mínimo real para no persistir un resaltado "accidental" de un toque mal
// interpretado como arrastre de 1-2px.
const val MIN_HIGHLIGHT_SIZE_PTS = 4f

/**
 * Convierte un arrastre en píxeles de pantalla (coordenadas locales del
 * bitmap de la página, cualquier orden entre los dos puntos) al rectángulo
 * equivalente en puntos PDF. Inverso exacto de la fórmula de dibujo ya
 * usada por `pageHighlights` en `PdfViewerContent`.
 */
fun screenDragToPdfRect(
    screenX1: Float, screenY1: Float,
    screenX2: Float, screenY2: Float,
    displayScale : Float,
    pageHeightPts: Float
): PdfRectPts {
    val minX = minOf(screenX1, screenX2)
    val maxX = maxOf(screenX1, screenX2)
    val minY = minOf(screenY1, screenY2)
    val maxY = maxOf(screenY1, screenY2)
    return PdfRectPts(
        xPts      = minX / displayScale,
        yPts      = pageHeightPts - (maxY / displayScale),
        widthPts  = (maxX - minX) / displayScale,
        heightPts = (maxY - minY) / displayScale
    )
}

/** Convierte un punto de pantalla (anclaje de una nota) a puntos PDF. */
fun screenPointToPdfPoint(
    screenX: Float, screenY: Float,
    displayScale : Float,
    pageHeightPts: Float
): PdfRectPts = PdfRectPts(
    xPts      = screenX / displayScale,
    yPts      = pageHeightPts - (screenY / displayScale),
    widthPts  = 0f,
    heightPts = 0f
)

/** Inverso: convierte un punto en puntos PDF a coordenadas de pantalla (px). */
fun pdfPointToScreenPoint(
    xPts: Float, yPts: Float,
    displayScale : Float,
    pageHeightPts: Float
): Pair<Float, Float> {
    val screenX = xPts * displayScale
    val screenY = (pageHeightPts - yPts) * displayScale
    return screenX to screenY
}

fun isValidHighlightSize(rect: PdfRectPts): Boolean =
    rect.widthPts >= MIN_HIGHLIGHT_SIZE_PTS && rect.heightPts >= MIN_HIGHLIGHT_SIZE_PTS
