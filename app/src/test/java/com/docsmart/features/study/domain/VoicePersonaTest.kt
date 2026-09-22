package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HU-64: la misma voz técnica del dispositivo debe mostrar siempre el
 * mismo personaje (nombre + color) entre sesiones -- estos tests fijan
 * ese contrato de determinismo, que es lo único que puede romperse en
 * silencio si alguien reordena o reemplaza la lista curada.
 */
class VoicePersonaTest {
    @Test
    fun `la misma voz tecnica siempre devuelve el mismo personaje`() {
        val first = personaForVoice("es-es-x-eef-local")
        val second = personaForVoice("es-es-x-eef-local")

        assertEquals(first, second)
    }

    @Test
    fun `voces tecnicas distintas pueden devolver personajes distintos`() {
        val names =
            listOf(
                "es-es-x-eef-local",
                "es-es-x-eed-local",
                "es-us-x-sfb-local",
                "en-us-x-tpf-local",
                "en-us-x-tpd-local",
                "pt-br-x-ptd-local",
            )
        val personas = names.map { personaForVoice(it) }.toSet()

        assertTrue(personas.size > 1)
    }

    @Test
    fun `nunca devuelve un nombre vacio sin importar la voz tecnica`() {
        val edgeCases = listOf("", "a", "x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x")

        edgeCases.forEach { name ->
            assertTrue(personaForVoice(name).name.isNotBlank())
        }
    }

    // Pedido explícito del usuario 2026-09-22 ("hay varios personajes que
    // repiten nombre... la idea es que cada uno sea diferente"): `personaForVoice`
    // sola (hash % 10) podía repetir personaje con solo 6-10 voces instaladas
    // (paradoja del cumpleaños) -- `personasForVoices` no debe repetir mientras
    // la cantidad de voces no supere la de personajes curados.
    @Test
    fun `personasForVoices no repite personaje mientras no se superen los personajes curados`() {
        val names = (1..10).map { "es-es-x-voz-$it-local" }

        val personas = personasForVoices(names)

        assertEquals(10, personas.size)
        assertEquals(10, personas.values.map { it.name }.toSet().size)
    }

    @Test
    fun `personasForVoices es deterministico para el mismo conjunto de voces`() {
        val names = listOf("es-es-x-eef-local", "es-us-x-sfb-local", "en-us-x-tpf-local")

        val first = personasForVoices(names)
        // Mismo conjunto, orden de llegada distinto (como podría reportarlo el
        // motor TTS entre dos arranques) -- el resultado no debe cambiar.
        val second = personasForVoices(names.reversed())

        assertEquals(first, second)
    }

    @Test
    fun `personasForVoices ignora duplicados en la lista de entrada`() {
        val names = listOf("es-es-x-eef-local", "es-es-x-eef-local", "es-us-x-sfb-local")

        val personas = personasForVoices(names)

        assertEquals(2, personas.size)
    }

    @Test
    fun `personasForVoices con una lista vacia no lanza excepcion`() {
        assertEquals(0, personasForVoices(emptyList()).size)
    }
}
