package com.docsmart.features.study.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.docsmart.core.data.db.DocuSmartDatabase
import com.docsmart.features.study.domain.NoteReminderScheduler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Backlog UX #49-#52: `migrateLegacyNotesIfNeeded()` es el único camino que
 * mueve datos reales de usuarios del formato viejo (SharedPreferences+JSON,
 * `StudyNotesStorage`) al nuevo (Room) -- si esto pierde una nota o la
 * duplica, es un bug de pérdida de datos real, no cosmético.
 */
class NoteRepositoryTest {

    private lateinit var db: DocuSmartDatabase
    private lateinit var repository: NoteRepository
    private lateinit var prefsStore: MutableMap<String, Any?>
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()

        // Fake mínimo de SharedPreferences respaldado por un mapa en memoria
        // -- mockear la cadena real (getSharedPreferences → edit → putBoolean
        // → apply) sin esto exigiría re-mockear cada valor devuelto.
        prefsStore = mutableMapOf()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putBoolean(any(), any()) } answers {
            prefsStore[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.apply() } answers { }
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers {
            prefsStore[firstArg()] as? String ?: secondArg()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            prefsStore[firstArg()] as? Boolean ?: secondArg()
        }
        every { prefs.edit() } returns editor

        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns prefs

        repository = NoteRepository(context, db.noteDao(), mockk<NoteReminderScheduler>(relaxed = true))
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun putLegacyNotesJson(vararg notes: Triple<String, String, String>) {
        val array = JSONArray()
        notes.forEach { (id, title, text) ->
            array.put(JSONObject().apply {
                put("id", id)
                put("title", title)
                put("text", text)
                put("date", "24/08/2026 · 10:00")
            })
        }
        prefsStore["notes_list"] = array.toString()
    }

    @Test
    fun `migra notas legadas reales a Room, conservando id, titulo y texto`() = runTest {
        putLegacyNotesJson(Triple("1000", "Repaso", "contenido legado"))

        repository.migrateLegacyNotesIfNeeded()

        val migrated = db.noteDao().observeAll().first()
        assertEquals(1, migrated.size)
        assertEquals("1000", migrated.first().note.id)
        assertEquals("Repaso", migrated.first().note.title)
        assertEquals("contenido legado", migrated.first().note.text)
    }

    @Test
    fun `migra varias notas legadas de una sola vez`() = runTest {
        putLegacyNotesJson(
            Triple("1", "A", "texto A"),
            Triple("2", "B", "texto B")
        )

        repository.migrateLegacyNotesIfNeeded()

        assertEquals(setOf("1", "2"), db.noteDao().observeAll().first().map { it.note.id }.toSet())
    }

    @Test
    fun `no migra dos veces -- la segunda llamada es un no-op`() = runTest {
        putLegacyNotesJson(Triple("1", "A", "texto A"))

        repository.migrateLegacyNotesIfNeeded()
        // Si el usuario borra la nota migrada y la migración corriera de
        // nuevo, "reaparecería" -- el flag evita justamente eso.
        db.noteDao().deleteAll()
        repository.migrateLegacyNotesIfNeeded()

        assertTrue(db.noteDao().observeAll().first().isEmpty())
    }

    @Test
    fun `sin notas legadas no inserta nada y no falla`() = runTest {
        repository.migrateLegacyNotesIfNeeded()

        assertTrue(db.noteDao().observeAll().first().isEmpty())
    }

    @Test
    fun `createNote genera un id unico por nota`() = runTest {
        repository.createNote("Título 1", "texto 1")
        repository.createNote("Título 2", "texto 2")

        val ids = db.noteDao().observeAll().first().map { it.note.id }
        assertEquals(2, ids.toSet().size, "cada nota nueva debe tener un id distinto")
    }
}
