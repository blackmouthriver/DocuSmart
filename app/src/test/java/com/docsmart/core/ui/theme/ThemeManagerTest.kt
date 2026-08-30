package com.docsmart.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        val manager = ThemeManager(contextWith(fakePrefsStore()))

        assertEquals(AppTheme.SYSTEM, manager.currentTheme.value)
        assertEquals(AccentColor.BLUE, manager.accentColor.value)
    }

    @Test
    fun `setAccentColor persiste y no afecta el tema guardado`() {
        val manager = ThemeManager(contextWith(fakePrefsStore()))

        manager.setTheme(AppTheme.DARK)
        manager.setAccentColor(AccentColor.PURPLE)

        assertEquals(AppTheme.DARK, manager.currentTheme.value)
        assertEquals(AccentColor.PURPLE, manager.accentColor.value)
    }

    @Test
    fun `un acento guardado se recupera en una instancia nueva`() {
        val store = fakePrefsStore()
        ThemeManager(contextWith(store)).setAccentColor(AccentColor.TEAL)

        val reloaded = ThemeManager(contextWith(store))

        assertEquals(AccentColor.TEAL, reloaded.accentColor.value)
    }

    @Test
    fun `un valor guardado invalido cae al Azul por defecto`() {
        val store = fakePrefsStore()
        store["accent_color"] = "NO_EXISTE"

        val manager = ThemeManager(contextWith(store))

        assertEquals(AccentColor.BLUE, manager.accentColor.value)
    }

    // ── helpers: SharedPreferences respaldado por un mapa real ─────────────

    private fun fakePrefsStore(): MutableMap<String, String> = mutableMapOf()

    private fun contextWith(store: MutableMap<String, String>): Context {
        val editor = mockk<SharedPreferences.Editor>()
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { editor.putString(capture(keySlot), capture(valueSlot)) } answers {
            store[keySlot.captured] = valueSlot.captured
            editor
        }
        every { editor.apply() } answers { }

        val keyArg = slot<String>()
        val defaultArg = slot<String>()
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(capture(keyArg), capture(defaultArg)) } answers {
            store[keyArg.captured] ?: defaultArg.captured
        }
        every { prefs.edit() } returns editor

        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }
}
