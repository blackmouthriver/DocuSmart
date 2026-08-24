package com.docsmart.features.study.domain

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cubre 2 bugs reales encontrados en Modo Estudio (docs/requirements/study.md):
 * el serializador manual de notas reemplazaba comillas dobles por comillas
 * simples en cada guardado (corrupción de datos), y aplicaba un `.reversed()`
 * de más al cargar que invertía el orden real de las notas guardadas.
 */
class StudyNotesStorageTest {

    @Test
    fun `guardar y cargar preserva comillas dobles sin corromper el texto`() {
        val store = fakePrefsStore()
        val context = contextWith(store)
        val note = SavedNote(
            id = "1", title = "Cita", text = """Dijo "hola" y se fue""", dateTime = "24/08/2026 · 10:00"
        )

        StudyNotesStorage.saveNotes(context, listOf(note))
        val loaded = StudyNotesStorage.loadNotes(context)

        assertEquals("""Dijo "hola" y se fue""", loaded.first().text)
    }

    @Test
    fun `guardar y cargar preserva saltos de linea reales`() {
        val store = fakePrefsStore()
        val context = contextWith(store)
        val note = SavedNote(
            id = "1", title = "Lista", text = "Primero\nSegundo\nTercero", dateTime = "24/08/2026 · 10:00"
        )

        StudyNotesStorage.saveNotes(context, listOf(note))
        val loaded = StudyNotesStorage.loadNotes(context)

        assertEquals("Primero\nSegundo\nTercero", loaded.first().text)
    }

    @Test
    fun `el orden guardado (mas nuevo primero) se preserva al recargar`() {
        val store = fakePrefsStore()
        val context = contextWith(store)
        val newest = SavedNote(id = "2", title = "B", text = "nota nueva", dateTime = "24/08/2026 · 11:00")
        val oldest = SavedNote(id = "1", title = "A", text = "nota vieja", dateTime = "24/08/2026 · 10:00")

        // El llamador siempre antepone la nota nueva, como hace NotesTab: listOf(newNote) + savedNotes
        StudyNotesStorage.saveNotes(context, listOf(newest, oldest))
        val loaded = StudyNotesStorage.loadNotes(context)

        assertEquals(listOf("2", "1"), loaded.map { it.id })
    }

    @Test
    fun `cargar sin notas guardadas devuelve lista vacia`() {
        val store = fakePrefsStore()
        val context = contextWith(store)

        assertTrue(StudyNotesStorage.loadNotes(context).isEmpty())
    }

    @Test
    fun `json corrupto en preferencias devuelve lista vacia en vez de fallar`() {
        val store = fakePrefsStore()
        store["notes_list"] = "esto no es json"
        val context = contextWith(store)

        assertTrue(StudyNotesStorage.loadNotes(context).isEmpty())
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
