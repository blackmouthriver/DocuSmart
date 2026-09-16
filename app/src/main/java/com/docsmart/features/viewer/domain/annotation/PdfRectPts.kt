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

/** Rectángulo en el sistema de coordenadas CRUDO del MediaBox (sin rotar). */
data class RawPageRect(
    val x     : Float,
    val y     : Float,
    val width : Float,
    val height: Float
)

// Hallazgo real de la revisión general 2026-09-16 (cuarta pasada, #16):
// xPts/yPts/widthPts/heightPts de una anotación están en el sistema de
// coordenadas VISUAL de la página (el que ve el usuario en pantalla, ya con
// la rotación /Rotate aplicada -- así lo definen screenDragToPdfRect/
// screenPointToPdfPoint arriba, contra pageHeightPts de PdfRenderer.Page,
// que Android ya devuelve intercambiado para 90°/270°). Pero
// FlattenAnnotationsPdfUseCase dibuja con PdfCanvas directo sobre el content
// stream crudo de iText7, que usa el MediaBox SIN rotar. Para una página con
// /Rotate 90/180/270 el resaltado quedaba desplazado en el PDF compartido
// aunque se viera bien en pantalla. Esta función deshace la rotación clockwise
// que un visor le aplica al contenido crudo al mostrarlo, derivada con una
// matriz de rotación estándar (x'=x·cosθ+y·senθ, y'=-x·senθ+y·cosθ para una
// rotación clockwise en un sistema con Y hacia arriba) + la traslación que
// centra la caja rotada en el origen -- verificada a mano para las 4
// rotaciones válidas en AnnotationCoordinatesTest.
fun visualRectToRawPageRect(
    visual: PdfRectPts,
    rotationDegrees: Int,
    rawPageWidthPts : Float,
    rawPageHeightPts: Float
): RawPageRect {
    val vx = visual.xPts
    val vy = visual.yPts
    val vw = visual.widthPts
    val vh = visual.heightPts
    return when (normalizeRotation(rotationDegrees)) {
        90 -> RawPageRect(
            x = rawPageWidthPts - (vy + vh), y = vx,
            width = vh, height = vw
        )
        180 -> RawPageRect(
            x = rawPageWidthPts - (vx + vw), y = rawPageHeightPts - (vy + vh),
            width = vw, height = vh
        )
        270 -> RawPageRect(
            x = vy, y = rawPageHeightPts - (vx + vw),
            width = vh, height = vw
        )
        else -> RawPageRect(x = vx, y = vy, width = vw, height = vh)
    }
}

private fun normalizeRotation(rotationDegrees: Int): Int {
    val normalized = rotationDegrees % 360
    return if (normalized < 0) normalized + 360 else normalized
}
