package com.docsmart.features.converter.domain.usecase

import java.io.ByteArrayOutputStream

/**
 * Codificador de GIF de un solo cuadro, sin dependencias de Android (Android no trae codificador
 * GIF). Reduce la imagen a 256 colores con corte por mediana sobre un histograma de 15 bits y
 * comprime con LZW de GIF. Los pixeles con transparencia (alfa < 128) usan el indice 255 como
 * color transparente.
 */
internal object GifEncoder {
    private const val MAX_DIMENSION = 65_535
    private const val TRANSPARENT_INDEX = 255
    private const val MIN_CODE_SIZE = 8
    private const val CLEAR_CODE = 1 shl MIN_CODE_SIZE
    private const val END_CODE = CLEAR_CODE + 1
    private const val FIRST_FREE_CODE = CLEAR_CODE + 2
    private const val MAX_CODE_SIZE = 12
    private const val TABLE_LIMIT = 1 shl MAX_CODE_SIZE
    private const val BLOCK_SIZE = 255

    fun encode(
        width: Int,
        height: Int,
        argb: IntArray,
    ): ByteArray {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) { "tamano fuera de 1..65535" }
        require(argb.size == width * height) { "la cantidad de pixeles no coincide con el tamano" }
        val hasTransparency = argb.any { (it ushr 24) < GifPalette.ALPHA_LIMIT }
        val palette = GifPalette.build(argb, if (hasTransparency) TRANSPARENT_INDEX else GifPalette.PALETTE_SIZE)
        val indices = ByteArray(argb.size)
        for (i in argb.indices) {
            val pixel = argb[i]
            val transparent = (pixel ushr 24) < GifPalette.ALPHA_LIMIT
            indices[i] = (if (transparent) TRANSPARENT_INDEX else palette.indexOfBin[GifPalette.binOf(pixel)]).toByte()
        }
        return writeGif(width, height, palette.colors, hasTransparency, lzw(indices))
    }

    /** LZW de GIF con codigos de 9 a 12 bits y reinicio de la tabla al llenarse. */
    private fun lzw(indices: ByteArray): ByteArray {
        val writer = BitWriter()
        val table = HashMap<Int, Int>(GifPalette.BIN_COUNT)
        var codeSize = MIN_CODE_SIZE + 1
        var next = FIRST_FREE_CODE
        writer.write(CLEAR_CODE, codeSize)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val symbol = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or symbol
            val known = table[key]
            if (known != null) {
                prefix = known
                continue
            }
            writer.write(prefix, codeSize)
            table[key] = next++
            if (next > (1 shl codeSize) && codeSize < MAX_CODE_SIZE) codeSize++
            if (next == TABLE_LIMIT) {
                writer.write(CLEAR_CODE, codeSize)
                table.clear()
                next = FIRST_FREE_CODE
                codeSize = MIN_CODE_SIZE + 1
            }
            prefix = symbol
        }
        writer.write(prefix, codeSize)
        // El decodificador agrega una entrada tras cada codigo, asi que puede subir el tamano un codigo antes.
        val endSize = if (next == (1 shl codeSize) && codeSize < MAX_CODE_SIZE) codeSize + 1 else codeSize
        writer.write(END_CODE, endSize)
        return writer.finish()
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var accumulator = 0
        private var bits = 0

        fun write(
            code: Int,
            size: Int,
        ) {
            accumulator = accumulator or (code shl bits)
            bits += size
            while (bits >= 8) {
                out.write(accumulator and 0xFF)
                accumulator = accumulator ushr 8
                bits -= 8
            }
        }

        fun finish(): ByteArray {
            if (bits > 0) out.write(accumulator and 0xFF)
            return out.toByteArray()
        }
    }

    private fun writeGif(
        width: Int,
        height: Int,
        colors: IntArray,
        hasTransparency: Boolean,
        compressed: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(compressed.size + 1024)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        out.writeShortLe(width)
        out.writeShortLe(height)
        out.write(0xF7) // tabla global de 256 colores, 8 bits por canal
        out.write(0) // color de fondo
        out.write(0) // proporcion de pixel
        for (color in colors) {
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
        if (hasTransparency) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, TRANSPARENT_INDEX.toByte(), 0x00))
        }
        out.write(0x2C)
        out.writeShortLe(0)
        out.writeShortLe(0)
        out.writeShortLe(width)
        out.writeShortLe(height)
        out.write(0) // sin tabla local, sin entrelazado
        out.write(MIN_CODE_SIZE)
        var offset = 0
        while (offset < compressed.size) {
            val length = minOf(BLOCK_SIZE, compressed.size - offset)
            out.write(length)
            out.write(compressed, offset, length)
            offset += length
        }
        out.write(0)
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
}
