package com.docsmart.features.study.domain

import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pedido explícito del usuario 2026-09-22: el género de cada voz se cachea
 * por voz técnica (ver KnownVoiceGenders.kt/VoiceGenderProbe.kt) para no
 * repetir la síntesis + análisis cada vez que se abre el selector -- estos
 * tests fijan el contrato de guardado/lectura de `saveGender`/`loadGenders`.
 */
class StudyVoicePreferenceTest {
    @Test
    fun `saveGender y loadGenders redondean el viaje completo`() {
        val context = fakeContextWithPrefs(fakePrefsStore())

        StudyVoicePreference.saveGender(context, "es-es-x-eef-local", isFeminine = true)
        StudyVoicePreference.saveGender(context, "es-es-x-eee-local", isFeminine = false)

        val genders = StudyVoicePreference.loadGenders(context, listOf("es-es-x-eef-local", "es-es-x-eee-local"))

        assertEquals(true, genders["es-es-x-eef-local"])
        assertEquals(false, genders["es-es-x-eee-local"])
    }

    @Test
    fun `loadGenders no incluye voces nunca guardadas`() {
        val context = fakeContextWithPrefs(fakePrefsStore())

        val genders = StudyVoicePreference.loadGenders(context, listOf("es-es-x-eef-local"))

        assertTrue(genders.isEmpty())
    }

    @Test
    fun `loadGenders solo devuelve las voces pedidas, no todo lo guardado`() {
        val context = fakeContextWithPrefs(fakePrefsStore())
        StudyVoicePreference.saveGender(context, "es-es-x-eef-local", isFeminine = true)
        StudyVoicePreference.saveGender(context, "es-es-x-eee-local", isFeminine = false)

        val genders = StudyVoicePreference.loadGenders(context, listOf("es-es-x-eef-local"))

        assertEquals(1, genders.size)
        assertFalse(genders.containsKey("es-es-x-eee-local"))
    }

    @Test
    fun `saveGender sobreescribe un valor previo de la misma voz`() {
        val context = fakeContextWithPrefs(fakePrefsStore())
        StudyVoicePreference.saveGender(context, "es-es-x-eef-local", isFeminine = false)

        StudyVoicePreference.saveGender(context, "es-es-x-eef-local", isFeminine = true)

        assertEquals(true, StudyVoicePreference.loadGenders(context, listOf("es-es-x-eef-local"))["es-es-x-eef-local"])
    }
}
