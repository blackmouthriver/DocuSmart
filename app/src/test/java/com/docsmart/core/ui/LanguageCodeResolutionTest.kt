package com.docsmart.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ronda 16: MainActivity.attachBaseContext() caia siempre en "es" sin idioma
 * guardado, mientras LanguageManager mostraba el del dispositivo.
 */
class LanguageCodeResolutionTest {
    @Test
    fun `el idioma guardado gana sobre el del dispositivo`() {
        assertEquals("de", resolveLanguageCode(saved = "de", deviceLanguage = "en"))
    }

    @Test
    fun `sin idioma guardado se usa el del dispositivo si esta soportado`() {
        assertEquals("en", resolveLanguageCode(saved = null, deviceLanguage = "en"))
        assertEquals("eu", resolveLanguageCode(saved = null, deviceLanguage = "eu"))
    }

    @Test
    fun `sin guardado y con dispositivo no soportado cae en espanol`() {
        assertEquals("es", resolveLanguageCode(saved = null, deviceLanguage = "ar"))
        assertEquals("es", resolveLanguageCode(saved = null, deviceLanguage = null))
    }

    @Test
    fun `un valor guardado corrupto se trata como ausente`() {
        assertEquals("fr", resolveLanguageCode(saved = "xx", deviceLanguage = "fr"))
        assertEquals("es", resolveLanguageCode(saved = "", deviceLanguage = "zz"))
    }

    @Test
    fun `cada idioma soportado se resuelve a si mismo`() {
        AppLanguage.entries.forEach {
            assertEquals(it.code, resolveLanguageCode(saved = it.code, deviceLanguage = null))
        }
    }
}
