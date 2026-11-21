package com.docsmart.features.converter.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Valida [GifEncoder] contra un decodificador independiente escrito segun la especificacion de GIF
 * (el decodificador sube el tamano del codigo cuando su tabla llega a 2^tamano), no contra el propio
 * codificador. Asi se comprueba el LZW de verdad, incluido el reinicio de la tabla.
 */
class GifEncoderTest {
    private class Decoded(
        val width: Int,
        val height: Int,
        val rgb: IntArray,
        val transparent: BooleanArray,
        val transparentIndex: Int?,
    )

    private fun expand(value5: Int) = (value5 shl 3) or (value5 shr 2)

    /** Color cuyos canales son exactos tras la reduccion a 5 bits, para poder comparar sin tolerancia. */
    private fun exactColor(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (expand(r) shl 16) or (expand(g) shl 8) or expand(b)

    private fun decode(gif: ByteArray): Decoded {
        assertEquals("GIF89a", String(gif, 0, 6, Charsets.US_ASCII))
        val width = (gif[6].toInt() and 0xFF) or ((gif[7].toInt() and 0xFF) shl 8)
        val height = (gif[8].toInt() and 0xFF) or ((gif[9].toInt() and 0xFF) shl 8)
        assertEquals(0xF7, gif[10].toInt() and 0xFF)
        var pos = 13
        val palette =
            IntArray(256) { i ->
                val o = pos + i * 3
                val r = gif[o].toInt() and 0xFF
                val g = gif[o + 1].toInt() and 0xFF
                val b = gif[o + 2].toInt() and 0xFF
                (r shl 16) or (g shl 8) or b
            }
        pos += 768
        var transparentIndex: Int? = null
        if ((gif[pos].toInt() and 0xFF) == 0x21) {
            assertEquals(0xF9, gif[pos + 1].toInt() and 0xFF)
            if ((gif[pos + 3].toInt() and 1) == 1) transparentIndex = gif[pos + 6].toInt() and 0xFF
            pos += 8
        }
        assertEquals(0x2C, gif[pos].toInt() and 0xFF)
        pos += 10
        val minCode = gif[pos++].toInt() and 0xFF
        val data = ByteArrayOutputStream()
        while (true) {
            val len = gif[pos++].toInt() and 0xFF
            if (len == 0) break
            data.write(gif, pos, len)
            pos += len
        }
        assertEquals(0x3B, gif[pos].toInt() and 0xFF)
        assertEquals(gif.size - 1, pos)
        val indices = lzwDecode(data.toByteArray(), minCode)
        assertEquals(width * height, indices.size, "pixeles decodificados")
        return Decoded(
            width,
            height,
            IntArray(indices.size) { palette[indices[it]] },
            BooleanArray(indices.size) { indices[it] == transparentIndex },
            transparentIndex,
        )
    }

    private fun readCode(
        data: ByteArray,
        bitPos: Int,
        size: Int,
    ): Int {
        var code = 0
        for (bit in 0 until size) {
            val byteIndex = (bitPos + bit) shr 3
            assertTrue(byteIndex < data.size, "se acabaron los datos antes del codigo de fin")
            if (((data[byteIndex].toInt() shr ((bitPos + bit) and 7)) and 1) == 1) code = code or (1 shl bit)
        }
        return code
    }

    /** Entrada de la tabla para [code]; agrega a la tabla la entrada nueva salvo tras un reinicio. */
    private fun nextEntry(
        table: ArrayList<IntArray>,
        code: Int,
        previous: Int,
    ): IntArray {
        if (previous == -1) return table[code]
        val entry = if (code < table.size) table[code] else table[previous] + table[previous][0]
        table.add(table[previous] + entry[0])
        return entry
    }

    private fun lzwDecode(
        data: ByteArray,
        minCode: Int,
    ): IntArray {
        val clear = 1 shl minCode
        val end = clear + 1
        val table = ArrayList<IntArray>()

        fun reset() {
            table.clear()
            for (i in 0 until clear) table.add(intArrayOf(i))
            table.add(IntArray(0))
            table.add(IntArray(0))
        }
        reset()
        val out = ArrayList<Int>()
        var size = minCode + 1
        var previous = -1
        var bitPos = 0
        var finished = false
        while (!finished) {
            val code = readCode(data, bitPos, size)
            bitPos += size
            when (code) {
                clear -> {
                    reset()
                    size = minCode + 1
                    previous = -1
                }
                end -> finished = true
                else -> {
                    out.addAll(nextEntry(table, code, previous).toList())
                    previous = code
                    if (table.size == (1 shl size) && size < 12) size++
                }
            }
        }
        return out.toIntArray()
    }

    @Test
    fun `un pixel solido se codifica y decodifica igual`() {
        val color = exactColor(31, 0, 10)
        val gif = GifEncoder.encode(1, 1, intArrayOf(color))
        val decoded = decode(gif)
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
        assertEquals(color and 0xFFFFFF, decoded.rgb[0])
    }

    @Test
    fun `una imagen con pocos colores se recupera exacta`() {
        val palette =
            List(40) {
                exactColor(Random(it).nextInt(32), Random(it + 100).nextInt(32), Random(it + 200).nextInt(32))
            }
        val random = Random(7)
        val pixels = IntArray(97 * 53) { palette[random.nextInt(palette.size)] }
        val decoded = decode(GifEncoder.encode(97, 53, pixels))
        assertEquals(97, decoded.width)
        assertEquals(53, decoded.height)
        for (i in pixels.indices) assertEquals(pixels[i] and 0xFFFFFF, decoded.rgb[i], "pixel $i")
    }

    @Test
    fun `cualquier largo de imagen cierra bien el flujo LZW`() {
        // Barre largos de 1 a 5000 pixeles: cubre los bordes donde el codigo de fin sube de tamano.
        val palette = List(6) { exactColor(it * 5, 31 - it * 3, it) }
        val random = Random(11)
        for (length in 1..5000 step 7) {
            val pixels = IntArray(length) { palette[random.nextInt(palette.size)] }
            val decoded = decode(GifEncoder.encode(length, 1, pixels))
            for (i in pixels.indices) assertEquals(pixels[i] and 0xFFFFFF, decoded.rgb[i], "largo $length pixel $i")
        }
        for (length in listOf(254, 255, 256, 257, 258, 510, 511, 512, 513, 1022, 1023, 1024, 2046, 2047, 2048, 2049)) {
            val pixels = IntArray(length) { palette[it % palette.size] }
            val decoded = decode(GifEncoder.encode(length, 1, pixels))
            for (i in pixels.indices) assertEquals(pixels[i] and 0xFFFFFF, decoded.rgb[i], "largo $length pixel $i")
        }
    }

    @Test
    fun `una imagen grande y ruidosa reinicia la tabla y se recupera`() {
        val random = Random(3)
        val palette = List(200) { exactColor(random.nextInt(32), random.nextInt(32), random.nextInt(32)) }
        val pixels = IntArray(300 * 300) { palette[random.nextInt(palette.size)] }
        val decoded = decode(GifEncoder.encode(300, 300, pixels))
        for (i in pixels.indices) assertEquals(pixels[i] and 0xFFFFFF, decoded.rgb[i], "pixel $i")
    }

    @Test
    fun `una foto con miles de colores se reduce a 256 con poco error`() {
        val random = Random(5)
        val pixels =
            IntArray(120 * 120) {
                val x = it % 120
                val y = it / 120
                (0xFF shl 24) or ((x * 2 + random.nextInt(8)).coerceAtMost(255) shl 16) or
                    ((y * 2).coerceAtMost(255) shl 8) or ((x + y + random.nextInt(8)).coerceAtMost(255))
            }
        val decoded = decode(GifEncoder.encode(120, 120, pixels))
        assertTrue(decoded.rgb.toSet().size <= 256)
        var worst = 0
        for (i in pixels.indices) {
            for (shift in intArrayOf(16, 8, 0)) {
                val expected = (pixels[i] shr shift) and 0xFF
                val actual = (decoded.rgb[i] shr shift) and 0xFF
                worst = maxOf(worst, kotlin.math.abs(expected - actual))
            }
        }
        assertTrue(worst <= 40, "error maximo por canal: $worst")
    }

    @Test
    fun `los pixeles transparentes usan el indice transparente`() {
        val opaque = exactColor(20, 5, 5)
        val pixels = IntArray(10 * 10) { if (it % 3 == 0) 0x00000000 else opaque }
        val decoded = decode(GifEncoder.encode(10, 10, pixels))
        assertEquals(255, decoded.transparentIndex)
        for (i in pixels.indices) {
            if (i % 3 == 0) {
                assertTrue(decoded.transparent[i], "pixel $i deberia ser transparente")
            } else {
                assertEquals(opaque and 0xFFFFFF, decoded.rgb[i])
            }
        }
    }

    @Test
    fun `una imagen sin transparencia no declara indice transparente`() {
        val decoded = decode(GifEncoder.encode(3, 3, IntArray(9) { exactColor(it, it, it) }))
        assertEquals(null, decoded.transparentIndex)
    }
}
