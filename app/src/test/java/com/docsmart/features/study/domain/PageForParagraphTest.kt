package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * "Retomar lectura" (2026-09-08): `pageForParagraph` traduce el índice de
 * párrafo que va leyendo la voz a un número de página del PDF, usando los
 * límites acumulados por página que arma `extractPdfText` -- ej. página 1
 * con 3 párrafos y página 2 con 2 párrafos más da `pageBoundaries = [3, 5]`.
 */
class PageForParagraphTest {
    @Test
    fun `parrafo dentro de la primera pagina`() {
        assertEquals(1, pageForParagraph(0, listOf(3, 5, 8)))
        assertEquals(1, pageForParagraph(2, listOf(3, 5, 8)))
    }

    @Test
    fun `parrafo justo en el limite pertenece a la pagina siguiente`() {
        assertEquals(2, pageForParagraph(3, listOf(3, 5, 8)))
    }

    @Test
    fun `parrafo en la ultima pagina`() {
        assertEquals(3, pageForParagraph(7, listOf(3, 5, 8)))
    }

    @Test
    fun `parrafo mas alla del ultimo limite se queda en la ultima pagina`() {
        assertEquals(3, pageForParagraph(50, listOf(3, 5, 8)))
    }

    @Test
    fun `sin limites de pagina siempre devuelve 1`() {
        assertEquals(1, pageForParagraph(0, emptyList()))
        assertEquals(1, pageForParagraph(99, emptyList()))
    }
}
