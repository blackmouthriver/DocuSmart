package com.docsmart.core.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LazyPageBitmapSizeTest {
    @Test
    fun `una A4 al ancho de un telefono conserva la proporcion y no se reduce`() {
        val size = lazyPageBitmapSize(595, 842, 1038)

        assertEquals(1038, size.width)
        assertEquals(1469, size.height) // 1038 * 842 / 595
    }

    @Test
    fun `una pagina pequena se renderiza al ancho pedido, no a 2x sus puntos`() {
        // El libro de prueba: 252x331 pt. A 2x serían 504 px (se vería borrosa en una pantalla de 1038 px).
        val size = lazyPageBitmapSize(252, 331, 1038)

        assertEquals(1038, size.width)
        assertEquals(1363, size.height)
    }

    @Test
    fun `una pagina gigante se reduce al tope de pixeles conservando la proporcion`() {
        val size = lazyPageBitmapSize(3118, 3118, 4000)

        assertTrue(size.width.toLong() * size.height <= VIEWER_PAGE_MAX_PIXELS)
        assertEquals(size.width, size.height) // era cuadrada
    }

    @Test
    fun `la pagina del crash de Crashlytics queda bajo el limite de 100 MB de Android`() {
        val size = lazyPageBitmapSize(3118, 3118, 6236)

        assertTrue(size.width.toLong() * size.height * 4 < 100L * 1024 * 1024)
    }

    @Test
    fun `dimensiones invalidas no producen un bitmap de tamano cero`() {
        val size = lazyPageBitmapSize(0, -5, 0)

        assertTrue(size.width >= 1)
        assertTrue(size.height >= 1)
    }

    @Test
    fun `una pagina muy alta y angosta mantiene su proporcion`() {
        val size = lazyPageBitmapSize(100, 1000, 500)

        assertEquals(500, size.width)
        assertEquals(5000, size.height)
    }
}
