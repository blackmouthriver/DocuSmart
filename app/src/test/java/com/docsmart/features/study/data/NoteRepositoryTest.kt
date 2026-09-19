package com.docsmart.features.study.data

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.docsmart.core.data.db.DocuSmartDatabase
import com.docsmart.features.study.domain.NoteReminderScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

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
    private lateinit var reminderScheduler: NoteReminderScheduler
    private lateinit var filesDir: File
    private lateinit var resolver: ContentResolver

    @BeforeEach
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
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

        filesDir = Files.createTempDirectory("docsmart_note_repo_").toFile()
        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.filesDir } returns filesDir

        resolver = mockk()
        every { context.contentResolver } returns resolver

        reminderScheduler = mockk(relaxed = true)
        repository = NoteRepository(context, db.noteDao(), reminderScheduler)
    }

    @AfterEach
    fun tearDown() {
        db.close()
        filesDir.deleteRecursively()
    }

    private fun stubImageUri(bytes: ByteArray): Uri {
        val uri = mockk<Uri>()
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun putLegacyNotesJson(vararg notes: Triple<String, String, String>) {
        val array = JSONArray()
        notes.forEach { (id, title, text) ->
            array.put(
                JSONObject().apply {
                    put("id", id)
                    put("title", title)
                    put("text", text)
                    put("date", "24/08/2026 · 10:00")
                },
            )
        }
        prefsStore["notes_list"] = array.toString()
    }

    @Test
    fun `migra notas legadas reales a Room, conservando id, titulo y texto`() =
        runTest {
            putLegacyNotesJson(Triple("1000", "Repaso", "contenido legado"))

            repository.migrateLegacyNotesIfNeeded()

            val migrated = db.noteDao().observeAll().first()
            assertEquals(1, migrated.size)
            assertEquals("1000", migrated.first().note.id)
            assertEquals("Repaso", migrated.first().note.title)
            assertEquals("contenido legado", migrated.first().note.text)
        }

    @Test
    fun `migra varias notas legadas de una sola vez`() =
        runTest {
            putLegacyNotesJson(
                Triple("1", "A", "texto A"),
                Triple("2", "B", "texto B"),
            )

            repository.migrateLegacyNotesIfNeeded()

            assertEquals(
                setOf("1", "2"),
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .map { it.note.id }
                    .toSet(),
            )
        }

    @Test
    fun `no migra dos veces -- la segunda llamada es un no-op`() =
        runTest {
            putLegacyNotesJson(Triple("1", "A", "texto A"))

            repository.migrateLegacyNotesIfNeeded()
            // Si el usuario borra la nota migrada y la migración corriera de
            // nuevo, "reaparecería" -- el flag evita justamente eso.
            db.noteDao().deleteAll()
            repository.migrateLegacyNotesIfNeeded()

            assertTrue(
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `sin notas legadas no inserta nada y no falla`() =
        runTest {
            repository.migrateLegacyNotesIfNeeded()

            assertTrue(
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `createNote genera un id unico por nota`() =
        runTest {
            repository.createNote("Título 1", "texto 1")
            repository.createNote("Título 2", "texto 2")

            val ids =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .map { it.note.id }
            assertEquals(2, ids.toSet().size, "cada nota nueva debe tener un id distinto")
        }

    // ── updateNote() -- B22, auditoría general 2026-09-17 ────────────────────

    @Test
    fun `updateNote actualiza titulo, texto y recordatorio`() =
        runTest {
            repository.createNote("Original", "texto original")
            val note =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .note

            repository.updateNote(
                noteId = note.id,
                title = "Editado",
                text = "texto editado",
                reminderAt = 5_000L,
                keptImages = emptyList(),
                removedImages = emptyList(),
                newImageUris = emptyList(),
            )

            val updated =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .note
            assertEquals("Editado", updated.title)
            assertEquals("texto editado", updated.text)
            assertEquals(5_000L, updated.reminderAt)
        }

    @Test
    fun `updateNote conserva las imagenes mantenidas, borra del disco las removidas y agrega las nuevas`() =
        runTest {
            val uriA = stubImageUri(byteArrayOf(1))
            val uriB = stubImageUri(byteArrayOf(2))
            repository.createNote("Con imagenes", "texto", imageUris = listOf(uriA, uriB))
            val original =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
            // Se mantiene la posición MÁS ALTA (no la 0) para que la imagen
            // nueva (posición kept.max + 1) nunca reutilice la ruta del
            // archivo justo borrado -- comportamiento real y correcto (position
            // + noteId arman el nombre de archivo, reutilizar una posición
            // vacía es válido), pero mezclaría las dos aserciones de este test
            // si coincidieran.
            val (keep, remove) = original.images.partition { it.filePath.endsWith("_1.jpg") }
            assertTrue(File(remove.single().filePath).exists(), "la imagen a remover debe existir antes de editar")

            val uriC = stubImageUri(byteArrayOf(3))
            repository.updateNote(
                noteId = original.note.id,
                title = original.note.title,
                text = original.note.text,
                reminderAt = null,
                keptImages = keep,
                removedImages = remove,
                newImageUris = listOf(uriC),
            )

            val result =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
            assertFalse(File(remove.single().filePath).exists(), "el archivo de la imagen removida debe borrarse")
            assertEquals(2, result.images.size, "1 conservada + 1 nueva")
            assertTrue(result.images.any { it.filePath == keep.single().filePath }, "la conservada sigue igual")
            assertTrue(
                result.images.none { it.filePath == remove.single().filePath },
                "la removida no debe quedar en la base",
            )
        }

    @Test
    fun `updateNote cancela el recordatorio viejo y programa el nuevo cuando cambia`() =
        runTest {
            repository.createNote("Con recordatorio", "texto", reminderAt = 1_000L)
            val note =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .note

            repository.updateNote(
                noteId = note.id,
                title = note.title,
                text = note.text,
                reminderAt = 2_000L,
                keptImages = emptyList(),
                removedImages = emptyList(),
                newImageUris = emptyList(),
            )

            verify(exactly = 1) { reminderScheduler.cancel(note.id) }
            verify(exactly = 1) { reminderScheduler.schedule(note.id, note.title, 2_000L) }
        }

    @Test
    fun `updateNote no toca el scheduler si nunca hubo recordatorio ni se agrega uno`() =
        runTest {
            repository.createNote("Sin recordatorio", "texto")
            val note =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .note

            repository.updateNote(
                noteId = note.id,
                title = note.title,
                text = "texto editado",
                reminderAt = null,
                keptImages = emptyList(),
                removedImages = emptyList(),
                newImageUris = emptyList(),
            )

            verify(exactly = 0) { reminderScheduler.cancel(any()) }
            verify(exactly = 0) { reminderScheduler.schedule(any(), any(), any()) }
        }

    // Hallazgo real encontrado al corregir updateNote() (mismo bug, mismo
    // método `noteDao.insert()` con REPLACE): vincular un documento a una
    // nota que ya tenía imágenes adjuntas las borraba de la base por el
    // CASCADE de SQLite, aunque linkDocument() nunca pidió tocar ninguna
    // imagen.
    @Test
    fun `linkDocument no borra las imagenes ya adjuntas a la nota`() =
        runTest {
            val uri = stubImageUri(byteArrayOf(1))
            repository.createNote("Con imagen", "texto", imageUris = listOf(uri))
            val note =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .note
            assertEquals(
                1,
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
                    .images.size,
            )

            repository.linkDocument(note.id, "documento-123")

            val result =
                db
                    .noteDao()
                    .observeAll()
                    .first()
                    .first()
            assertEquals("documento-123", result.note.documentId)
            assertEquals(1, result.images.size, "las imágenes ya adjuntas no deben desaparecer al vincular")
        }
}
