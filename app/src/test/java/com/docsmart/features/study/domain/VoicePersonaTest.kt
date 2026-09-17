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
        val names = listOf(
            "es-es-x-eef-local", "es-es-x-eed-local", "es-us-x-sfb-local",
            "en-us-x-tpf-local", "en-us-x-tpd-local", "pt-br-x-ptd-local"
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
}
