package com.docsmart.core.ui.theme

import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * RF-SET-07: color de acento persistido junto al tema (claro/oscuro/
 * sistema), independiente uno del otro. Mismo patrón de SharedPreferences
 * fake respaldado por un mapa real ya usado en StudyNotesStorageTest.
 */
class ThemeManagerTest {

    @Test
    fun `sin nada guardado, el tema es Sistema y el acento es Azul por defecto`() {
        val manager = ThemeManager(fakeContextWithPrefs(fakePrefsStore()))

        assertEquals(AppTheme.SYSTEM, manager.currentTheme.value)
        assertEquals(AccentColor.BLUE, manager.accentColor.value)
    }

    @Test
    fun `setAccentColor persiste y no afecta el tema guardado`() {
        val manager = ThemeManager(fakeContextWithPrefs(fakePrefsStore()))

        manager.setTheme(AppTheme.DARK)
        manager.setAccentColor(AccentColor.PURPLE)

        assertEquals(AppTheme.DARK, manager.currentTheme.value)
        assertEquals(AccentColor.PURPLE, manager.accentColor.value)
    }

    @Test
    fun `un acento guardado se recupera en una instancia nueva`() {
        val store = fakePrefsStore()
        ThemeManager(fakeContextWithPrefs(store)).setAccentColor(AccentColor.TEAL)

        val reloaded = ThemeManager(fakeContextWithPrefs(store))

        assertEquals(AccentColor.TEAL, reloaded.accentColor.value)
    }

    @Test
    fun `un valor guardado invalido cae al Azul por defecto`() {
        val store = fakePrefsStore()
        store["accent_color"] = "NO_EXISTE"

        val manager = ThemeManager(fakeContextWithPrefs(store))

        assertEquals(AccentColor.BLUE, manager.accentColor.value)
    }
}
