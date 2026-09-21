package com.docsmart.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado única de DocuSmart (Fase 0 del plan de mejora de
 * diseño, 2026-09-20). Todo margen, padding o separación nuevo debe salir de
 * acá en vez de escribir un número suelto -- así las pantallas no vuelven a
 * divergir entre sí (el mismo problema que ya tuvo el margen de los banners).
 *
 * Los valores existentes en pantallas antiguas no se tocan de golpe: se
 * migran de a una al rediseñarlas (Fase 2).
 */
object DocuSmartSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Margen horizontal estándar de pantalla (igual al del banner). */
    val screenHorizontal = lg

    /** Alto mínimo táctil recomendado (Material y WCAG). */
    val minTouchTarget = 48.dp
}

/** Elevaciones permitidas; las superficies tonales reemplazan a las sombras. */
object DocuSmartElevation {
    val none = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val overlay = 8.dp
}
