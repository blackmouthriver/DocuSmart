package com.docsmart.features.study.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// Fase 4 (2026-09-22): cubre el bug real encontrado en auditoría -- el motor TTS
// reporta el rango como `start until end` (onRangeStart), y usar `range.last`
// directo como límite de `String.substring` (exclusivo) le comía la última letra
// de cada palabra resaltada. También cubre los límites del texto: un rango que
// se pasa del tamaño del párrafo no debe lanzar excepción.
class SplitForHighlightTest {
    @Test
    fun `sin rango no hay nada resaltado`() {
        val parts = splitForHighlight("Hola mundo", range = null)
        parts.before shouldBe "Hola mundo"
        parts.highlighted shouldBe ""
        parts.after shouldBe ""
    }

    @Test
    fun `resalta la palabra completa, sin comerse la ultima letra`() {
        // "Hola mundo" -- "mundo" ocupa los índices 5..9 (start=5, end=10, como
        // los reporta onRangeStart con start until end).
        val parts = splitForHighlight("Hola mundo", range = 5 until 10)
        parts.before shouldBe "Hola "
        parts.highlighted shouldBe "mundo"
        parts.after shouldBe ""
    }

    @Test
    fun `resalta una palabra intermedia completa`() {
        val parts = splitForHighlight("Hola mundo cruel", range = 5 until 10)
        parts.before shouldBe "Hola "
        parts.highlighted shouldBe "mundo"
        parts.after shouldBe " cruel"
    }

    @Test
    fun `un rango que se pasa del texto se acota sin lanzar excepcion`() {
        val parts = splitForHighlight("Hola", range = 2 until 10)
        parts.before shouldBe "Ho"
        parts.highlighted shouldBe "la"
        parts.after shouldBe ""
    }

    @Test
    fun `un rango negativo se acota a cero sin lanzar excepcion`() {
        val parts = splitForHighlight("Hola", range = -3 until 2)
        parts.before shouldBe ""
        parts.highlighted shouldBe "Ho"
        parts.after shouldBe "la"
    }

    @Test
    fun `un rango vacio no resalta nada`() {
        // Rango de ancho cero: mismo caso que `5 until 5`, escrito así para que
        // detekt no lo marque como un bucle que nunca se ejecuta.
        val parts = splitForHighlight("Hola mundo", range = IntRange(5, 4))
        parts.highlighted shouldBe ""
    }

    @Test
    fun `resaltar hasta el final del parrafo no lanza excepcion`() {
        val parts = splitForHighlight("Hola", range = 0 until 4)
        parts.before shouldBe ""
        parts.highlighted shouldBe "Hola"
        parts.after shouldBe ""
    }
}
