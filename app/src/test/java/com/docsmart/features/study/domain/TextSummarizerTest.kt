package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Resumen local (2026-09-08): `summarize()` no depende de Context ni de
 * ningún framework de Android -- lógica pura, fácil de probar con texto real
 * a mano.
 */
class TextSummarizerTest {

    @Test
    fun `documento mas corto que el resumen pedido devuelve todo tal cual`() {
        val paragraphs = listOf(
            "Esta es la primera oración del documento. Esta es la segunda oración."
        )

        val result = TextSummarizer.summarize(paragraphs, maxSentences = 15)

        assertEquals(2, result.size)
        assertEquals("Esta es la primera oración del documento.", result[0])
        assertEquals("Esta es la segunda oración.", result[1])
    }

    @Test
    fun `documento vacio no genera resumen`() {
        assertEquals(emptyList<String>(), TextSummarizer.summarize(emptyList()))
        assertEquals(emptyList<String>(), TextSummarizer.summarize(listOf("")))
    }

    @Test
    fun `elige las oraciones con palabras mas repetidas y las devuelve en orden original`() {
        // "gato"/"perro" se repiten varias veces (relevantes); las demás
        // oraciones son "relleno" con palabras que no se repiten.
        val paragraphs = listOf(
            "El gato duerme en el sofá todas las tardes soleadas de verano.",
            "El clima estuvo templado ayer por la mañana en la ciudad.",
            "El perro persigue al gato por el jardín trasero de la casa.",
            "Compré manzanas y peras en el mercado local del barrio.",
            "El gato y el perro son mejores amigos desde hace varios años.",
            "La bicicleta nueva tiene un manubrio ajustable de color rojo."
        )

        val result = TextSummarizer.summarize(paragraphs, maxSentences = 2)

        assertEquals(2, result.size)
        // Las 2 oraciones elegidas deben ser sobre gato/perro (las más
        // repetidas), no sobre clima/manzanas/bicicleta.
        result.forEach { sentence ->
            assertTrue(sentence.contains("gato") || sentence.contains("perro"))
        }
        // Deben mantener el orden en que aparecen en el documento original.
        val firstIndex = paragraphs.indexOfFirst { it == result[0] }
        val secondIndex = paragraphs.indexOfFirst { it == result[1] }
        assertTrue(firstIndex < secondIndex)
    }

    @Test
    fun `oraciones muy cortas se descartan como ruido`() {
        val paragraphs = listOf(
            "Sí. No. Ok. Esta es una oración real con suficiente longitud como para contar."
        )

        val result = TextSummarizer.summarize(paragraphs, maxSentences = 15)

        assertEquals(1, result.size)
        assertTrue(result[0].contains("oración real"))
    }

    @Test
    fun `documento largo no supera el maximo de oraciones pedido`() {
        val paragraphs = (1..50).map { i ->
            "Esta es la oración número $i sobre un tema general de prueba para el resumen."
        }

        val result = TextSummarizer.summarize(paragraphs, maxSentences = 10)

        assertTrue(result.size <= 10)
        assertTrue(result.isNotEmpty())
    }
}
