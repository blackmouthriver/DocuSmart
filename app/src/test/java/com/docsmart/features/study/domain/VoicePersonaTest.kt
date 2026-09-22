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

    // Pedido explícito del usuario 2026-09-22 ("las voces femeninas tienen
    // nombres y personajes masculinos... discrimina"): con el género real
    // conocido, cada voz solo puede caer en un personaje de SU género.
    @Test
    fun `una voz femenina siempre recibe un personaje femenino`() {
        val names = listOf("voz-a", "voz-b", "voz-c")
        val genders = mapOf("voz-a" to true, "voz-b" to true, "voz-c" to true)

        val personas = personasForVoices(names, genders)

        personas.values.forEach { assertTrue(it.isFeminine) }
    }

    @Test
    fun `una voz masculina siempre recibe un personaje masculino`() {
        val names = listOf("voz-a", "voz-b", "voz-c")
        val genders = mapOf("voz-a" to false, "voz-b" to false, "voz-c" to false)

        val personas = personasForVoices(names, genders)

        personas.values.forEach { assertTrue(!it.isFeminine) }
    }

    @Test
    fun `voces mezcladas no cruzan de genero ni entre si`() {
        val names = listOf("f1", "f2", "m1", "m2", "m3")
        val genders = mapOf("f1" to true, "f2" to true, "m1" to false, "m2" to false, "m3" to false)

        val personas = personasForVoices(names, genders)

        assertTrue(personas.getValue("f1").isFeminine)
        assertTrue(personas.getValue("f2").isFeminine)
        assertTrue(!personas.getValue("m1").isFeminine)
        assertTrue(!personas.getValue("m2").isFeminine)
        assertTrue(!personas.getValue("m3").isFeminine)
        // Con solo 5 personajes por género (10 curados / 2), ninguno se
        // repite todavía dentro de cada grupo.
        assertEquals(2, setOf(personas.getValue("f1"), personas.getValue("f2")).size)
        assertEquals(3, setOf(personas.getValue("m1"), personas.getValue("m2"), personas.getValue("m3")).size)
    }

    @Test
    fun `una voz sin genero detectado todavia recibe igual un personaje`() {
        val names = listOf("desconocida")

        val personas = personasForVoices(names, isFeminineByVoice = emptyMap())

        assertEquals(1, personas.size)
        assertTrue(personas.getValue("desconocida").name.isNotBlank())
    }

    @Test
    fun `sin informacion de genero se comporta igual que antes (por posicion)`() {
        val names = listOf("es-es-x-eef-local", "es-us-x-sfb-local", "en-us-x-tpf-local")

        val withoutGenders = personasForVoices(names)
        val withEmptyGenders = personasForVoices(names, emptyMap())

        assertEquals(withoutGenders, withEmptyGenders)
    }
}
