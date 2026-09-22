package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verificado a oído real en dispositivo (2026-09-22): de 11 voces "es" de
 * Google TTS, el detector de tono acertó solo 5 -- esta tabla reemplaza esa
 * clasificación para los códigos técnicos conocidos. Estos tests fijan el
 * contrato de la tabla en sí (no hay forma de testear "suena femenina" sin
 * un oído real, pero sí hay que evitar que un typo futuro rompa un código).
 */
class KnownVoiceGendersTest {
    @Test
    fun `todas las voces verificadas quedan cubiertas`() {
        assertEquals(11, KNOWN_VOICE_GENDERS.size)
    }

    @Test
    fun `ningun codigo tecnico esta en blanco`() {
        KNOWN_VOICE_GENDERS.keys.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `cada codigo tecnico aparece una sola vez`() {
        assertEquals(KNOWN_VOICE_GENDERS.size, KNOWN_VOICE_GENDERS.keys.toSet().size)
    }
}
