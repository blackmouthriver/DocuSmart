package com.docsmart.core.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LruByteCacheTest {
    // Cada valor "pesa" lo que dice su longitud: se prueba el presupuesto sin Bitmaps reales.
    private fun cache(maxBytes: Long) = LruByteCache<Int, String>(maxBytes) { it.length.toLong() }

    private fun bytes(n: Int) = "x".repeat(n)

    @Test
    fun `guarda y devuelve lo agregado dentro del presupuesto`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.put(2, bytes(40))

        assertNotNull(c.get(1))
        assertNotNull(c.get(2))
        assertEquals(80L, c.sizeBytes)
        assertEquals(2, c.count)
    }

    @Test
    fun `al pasar el presupuesto descarta el menos usado`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.put(2, bytes(40))
        c.put(3, bytes(40)) // 120 > 100: sale la 1

        assertNull(c.get(1))
        assertNotNull(c.get(2))
        assertNotNull(c.get(3))
        assertEquals(80L, c.sizeBytes)
    }

    @Test
    fun `leer una entrada la vuelve la mas reciente`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.put(2, bytes(40))
        c.get(1) // ahora la menos usada es la 2
        c.put(3, bytes(40))

        assertNotNull(c.get(1))
        assertNull(c.get(2))
        assertNotNull(c.get(3))
    }

    @Test
    fun `un elemento mas grande que todo el presupuesto se conserva igual`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.put(2, bytes(500)) // por sí solo supera el presupuesto

        assertNull(c.get(1))
        assertNotNull(c.get(2))
        assertEquals(1, c.count)
    }

    @Test
    fun `reemplazar una clave no duplica los bytes`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.put(1, bytes(10))

        assertEquals(10L, c.sizeBytes)
        assertEquals(1, c.count)
    }

    @Test
    fun `removeWhere descuenta los bytes de lo que quita`() {
        val c = cache(1_000)
        c.put(1, bytes(10))
        c.put(2, bytes(20))
        c.put(3, bytes(30))

        c.removeWhere { it % 2 == 1 }

        assertNull(c.get(1))
        assertNotNull(c.get(2))
        assertNull(c.get(3))
        assertEquals(20L, c.sizeBytes)
    }

    @Test
    fun `clear deja la cache vacia`() {
        val c = cache(100)
        c.put(1, bytes(40))
        c.clear()

        assertEquals(0L, c.sizeBytes)
        assertEquals(0, c.count)
        assertNull(c.get(1))
    }

    @Test
    fun `nunca supera el presupuesto si todos los elementos caben individualmente`() {
        val c = cache(100)
        repeat(500) { c.put(it, bytes(30)) }

        assert(c.sizeBytes <= 100) { "bytes=${c.sizeBytes}" }
        assertEquals(3, c.count)
    }
}
