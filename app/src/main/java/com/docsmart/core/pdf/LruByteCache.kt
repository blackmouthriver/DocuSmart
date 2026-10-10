package com.docsmart.core.pdf

/**
 * Caché LRU acotada por BYTES (no por cantidad de elementos): las páginas de un PDF pesan de 1 a 14 MB
 * según el formato y el ancho de pantalla, así que contar elementos no limita la memoria.
 *
 * Al superar [maxBytes] se descartan los menos usados, pero **siempre se conserva al menos el último
 * elemento agregado** aunque por sí solo supere el presupuesto (una página gigante no debe quedar
 * fuera de la caché y volver a renderizarse en cada recomposición).
 *
 * No recicla ni cierra nada: quien tenga todavía una referencia (p. ej. una página que se está
 * dibujando) puede seguir usándola; el recurso se libera cuando nadie más lo referencia.
 */
internal class LruByteCache<K : Any, V : Any>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long,
) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true) // orden de acceso: el primero es el menos usado
    private var bytes = 0L

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        entries.put(key, value)?.let { bytes -= sizeOf(it) }
        bytes += sizeOf(value)
        trim()
    }

    /** Quita todas las entradas cuya clave cumpla [predicate] (p. ej. la misma página a otro ancho). */
    @Synchronized
    fun removeWhere(predicate: (K) -> Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate(entry.key)) {
                bytes -= sizeOf(entry.value)
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        bytes = 0L
    }

    @get:Synchronized
    val sizeBytes: Long get() = bytes

    @get:Synchronized
    val count: Int get() = entries.size

    private fun trim() {
        val iterator = entries.entries.iterator()
        while (bytes > maxBytes && entries.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            bytes -= sizeOf(eldest.value)
            iterator.remove()
        }
    }
}
