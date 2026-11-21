package com.docsmart.features.converter.domain.usecase

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IcoEncoderTest {
    private fun u8(
        bytes: ByteArray,
        at: Int,
    ) = bytes[at].toInt() and 0xFF

    private fun u16(
        bytes: ByteArray,
        at: Int,
    ) = u8(bytes, at) or (u8(bytes, at + 1) shl 8)

    private fun u32(
        bytes: ByteArray,
        at: Int,
    ) = u16(bytes, at) or (u16(bytes, at + 2) shl 16)

    @Test
    fun `los tamanos son los de un favicon completo`() {
        assertEquals(listOf(16, 32, 48, 64, 128, 256), IcoEncoder.SIZES)
    }

    @Test
    fun `el encabezado y las entradas apuntan a cada PNG`() {
        val pngs = listOf(16 to byteArrayOf(1, 2, 3), 32 to byteArrayOf(4, 5, 6, 7, 8), 256 to byteArrayOf(9))
        val ico = IcoEncoder.encode(pngs.map { IcoEncoder.Entry(it.first, it.second) })

        assertEquals(0, u16(ico, 0))
        assertEquals(1, u16(ico, 2)) // tipo icono
        assertEquals(3, u16(ico, 4))
        var expectedOffset = 6 + 16 * 3
        pngs.forEachIndexed { i, (size, png) ->
            val entry = 6 + 16 * i
            val dimension = if (size == 256) 0 else size
            assertEquals(dimension, u8(ico, entry), "ancho de la entrada $i")
            assertEquals(dimension, u8(ico, entry + 1), "alto de la entrada $i")
            assertEquals(1, u16(ico, entry + 4))
            assertEquals(32, u16(ico, entry + 6))
            assertEquals(png.size, u32(ico, entry + 8))
            assertEquals(expectedOffset, u32(ico, entry + 12))
            assertArrayEquals(png, ico.copyOfRange(expectedOffset, expectedOffset + png.size))
            expectedOffset += png.size
        }
        assertEquals(expectedOffset, ico.size)
    }

    @Test
    fun `rechaza una lista vacia y tamanos fuera de rango`() {
        assertThrows(IllegalArgumentException::class.java) { IcoEncoder.encode(emptyList()) }
        for (size in listOf(512, 0)) {
            val entry = IcoEncoder.Entry(size, byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { IcoEncoder.encode(listOf(entry)) }
        }
    }
}
