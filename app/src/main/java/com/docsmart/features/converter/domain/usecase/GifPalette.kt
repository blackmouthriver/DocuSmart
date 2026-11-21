package com.docsmart.features.converter.domain.usecase

/**
 * Reduccion de color para [GifEncoder]: histograma de 15 bits y corte por mediana hasta 256 colores.
 * Sin dependencias de Android.
 */
internal object GifPalette {
    const val ALPHA_LIMIT = 128
    const val BIN_COUNT = 1 shl 15
    const val PALETTE_SIZE = 256

    class Palette(
        /** RGB empaquetado (0xRRGGBB) de cada indice usado. */
        val colors: IntArray,
        /** Indice de paleta de cada uno de los 32 768 colores de 15 bits. */
        val indexOfBin: IntArray,
    )

    private class Box(
        val bins: IntArray,
        val pixels: Long,
    )

    fun binOf(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
    }

    private fun channel(
        bin: Int,
        shift: Int,
    ): Int = (bin shr shift) and 0x1F

    private fun expand(value5: Int): Int = (value5 shl 3) or (value5 shr 2)

    fun build(
        argb: IntArray,
        maxColors: Int,
    ): Palette {
        val histogram = IntArray(BIN_COUNT)
        for (pixel in argb) if ((pixel ushr 24) >= ALPHA_LIMIT) histogram[binOf(pixel)]++
        val used = (0 until BIN_COUNT).filter { histogram[it] > 0 }.toIntArray()
        val boxes = if (used.isEmpty()) emptyList() else medianCut(used, histogram, maxColors)
        val colors = IntArray(PALETTE_SIZE)
        val indexOfBin = IntArray(BIN_COUNT)
        boxes.forEachIndexed { index, box ->
            colors[index] = averageColor(box, histogram)
            box.bins.forEach { indexOfBin[it] = index }
        }
        return Palette(colors, indexOfBin)
    }

    private fun medianCut(
        used: IntArray,
        histogram: IntArray,
        maxColors: Int,
    ): List<Box> {
        val boxes = mutableListOf(Box(used, used.sumOf { histogram[it].toLong() }))
        while (boxes.size < maxColors) {
            val target = boxes.filter { it.bins.size > 1 }.maxByOrNull { it.pixels } ?: break
            boxes.remove(target)
            boxes.addAll(split(target, histogram))
        }
        return boxes
    }

    private fun split(
        box: Box,
        histogram: IntArray,
    ): List<Box> {
        val shift = widestChannelShift(box.bins)
        val sorted = box.bins.sortedBy { channel(it, shift) }
        var accumulated = 0L
        var cut = 1
        for (i in sorted.indices) {
            accumulated += histogram[sorted[i]]
            if (accumulated * 2 >= box.pixels) {
                cut = (i + 1).coerceIn(1, sorted.size - 1)
                break
            }
        }
        val first = sorted.subList(0, cut).toIntArray()
        val second = sorted.subList(cut, sorted.size).toIntArray()
        return listOf(
            Box(first, first.sumOf { histogram[it].toLong() }),
            Box(second, second.sumOf { histogram[it].toLong() }),
        )
    }

    /** Desplazamiento (10 = rojo, 5 = verde, 0 = azul) del canal con mayor rango dentro de la caja. */
    private fun widestChannelShift(bins: IntArray): Int {
        var best = 0
        var bestRange = -1
        for (shift in intArrayOf(10, 5, 0)) {
            var low = Int.MAX_VALUE
            var high = Int.MIN_VALUE
            for (bin in bins) {
                val value = channel(bin, shift)
                if (value < low) low = value
                if (value > high) high = value
            }
            if (high - low > bestRange) {
                bestRange = high - low
                best = shift
            }
        }
        return best
    }

    private fun averageColor(
        box: Box,
        histogram: IntArray,
    ): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (bin in box.bins) {
            val weight = histogram[bin].toLong()
            r += expand(channel(bin, 10)) * weight
            g += expand(channel(bin, 5)) * weight
            b += expand(channel(bin, 0)) * weight
        }
        val total = box.pixels.coerceAtLeast(1)
        return ((r / total).toInt() shl 16) or ((g / total).toInt() shl 8) or (b / total).toInt()
    }
}
