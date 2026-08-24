package com.docsmart.core.ui

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Cubre el bug real encontrado en Ajustes (docs/requirements/settings-premium.md):
 * "Restablecer configuración" forzaba español sin importar el idioma
 * configurado del dispositivo — mismo tipo de bug ya corregido en TTS y
 * reconocimiento de voz (Modo Estudio).
 */
class LanguageManagerTest {

    private lateinit var originalLocale: Locale
    private lateinit var manager: LanguageManager

    @BeforeEach
    fun setUp() {
        originalLocale = Locale.getDefault()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns fakeSharedPreferences()
        manager = LanguageManager(context)
    }

    @AfterEach
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `deviceDefaultLanguage devuelve el idioma del dispositivo si esta soportado`() {
        Locale.setDefault(Locale("ru"))

        assertEquals(AppLanguage.RUSSIAN, manager.deviceDefaultLanguage())
    }

    @Test
    fun `deviceDefaultLanguage devuelve espanol si el idioma del dispositivo no esta soportado`() {
        Locale.setDefault(Locale("ja")) // japonés — no está en AppLanguage

        assertEquals(AppLanguage.SPANISH, manager.deviceDefaultLanguage())
    }

    @Test
    fun `deviceDefaultLanguage reconoce ingles`() {
        Locale.setDefault(Locale("en", "US"))

        assertEquals(AppLanguage.ENGLISH, manager.deviceDefaultLanguage())
    }

    // ── helper: SharedPreferences respaldado por un mapa real ────────────────

    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, String?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            store[firstArg<String>()] ?: secondArg()
        }
        return prefs
    }
}
