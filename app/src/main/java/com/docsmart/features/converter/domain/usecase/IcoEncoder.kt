package com.docsmart.features.converter.domain.usecase

/**
 * Empaqueta imagenes PNG ya codificadas en un archivo `.ico` (formato de Windows con entradas PNG,
 * valido desde Windows Vista y en todos los navegadores). Sin dependencias de Android para poder
 * probarlo en JVM.
 */
internal object IcoEncoder {
    /** Tamanos de un favicon completo, los mismos que la web (`ICO_SIZES`). */
    val SIZES: List<Int> = listOf(16, 32, 48, 64, 128, 256)

    private const val HEADER_BYTES = 6
    private const val ENTRY_BYTES = 16
    private const val TYPE_ICON = 1
    private const val BITS_PER_PIXEL = 32
    private const val MAX_BYTE_DIMENSION = 256

    class Entry(
        val size: Int,
        val png: ByteArray,
    )

    fun encode(entries: List<Entry>): ByteArray {
        require(entries.isNotEmpty()) { "un ICO necesita al menos una imagen" }
        require(entries.all { it.size in 1..MAX_BYTE_DIMENSION }) { "tamano fuera de 1..256" }
        val dataStart = HEADER_BYTES + ENTRY_BYTES * entries.size
        val out = java.io.ByteArrayOutputStream(dataStart + entries.sumOf { it.png.size })
        out.writeShortLe(0) // reservado
        out.writeShortLe(TYPE_ICON)
        out.writeShortLe(entries.size)
        var offset = dataStart
        for (entry in entries) {
            val dimension = if (entry.size == MAX_BYTE_DIMENSION) 0 else entry.size // 0 significa 256
            out.write(dimension) // ancho
            out.write(dimension) // alto
            out.write(0) // colores de paleta (0 = sin paleta)
            out.write(0) // reservado
            out.writeShortLe(1) // planos de color
            out.writeShortLe(BITS_PER_PIXEL)
            out.writeIntLe(entry.png.size)
            out.writeIntLe(offset)
            offset += entry.png.size
        }
        entries.forEach { out.write(it.png) }
        return out.toByteArray()
    }

    private fun java.io.ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun java.io.ByteArrayOutputStream.writeIntLe(value: Int) {
        writeShortLe(value and 0xFFFF)
        writeShortLe((value ushr 16) and 0xFFFF)
    }
}
