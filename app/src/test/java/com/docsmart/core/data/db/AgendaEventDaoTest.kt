package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * HU-65 (Agenda): integración real de SQLite vía `Room.inMemoryDatabaseBuilder`
 * -- confirma el SQL generado (orden real, upsert por REPLACE, migración/
 * desvínculo de documentId), mismo estilo que `PageBookmarkDaoTest`/
 * `NoteDaoTest`.
 */
class AgendaEventDaoTest {
    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: AgendaEventDao

    @BeforeEach
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .build()
        dao = db.agendaEventDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun event(
        id: String,
        dateTimeMillis: Long,
        documentId: String? = null,
        reminderMinutesBefore: Int? = null,
    ) = AgendaEventEntity(
        id = id,
        title = "Evento $id",
        description = null,
        dateTimeMillis = dateTimeMillis,
        documentId = documentId,
        reminderMinutesBefore = reminderMinutesBefore,
        createdAt = 1_000L,
    )

    @Test
    fun `insert agrega un evento nuevo`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 5_000L))

            assertEquals(1, dao.observeAll().first().size)
        }

    @Test
    fun `observeAll ordena por fecha ascendente`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 3_000L))
            dao.insert(event("e2", dateTimeMillis = 1_000L))
            dao.insert(event("e3", dateTimeMillis = 2_000L))

            assertEquals(listOf("e2", "e3", "e1"), dao.observeAll().first().map { it.id })
        }

    @Test
    fun `insert con el mismo id reemplaza el evento (edicion)`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 1_000L))
            dao.insert(event("e1", dateTimeMillis = 1_000L).copy(title = "Editado"))

            val all = dao.observeAll().first()
            assertEquals(1, all.size)
            assertEquals("Editado", all.first().title)
        }

    @Test
    fun `delete quita solo el evento indicado`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 1_000L))
            dao.insert(event("e2", dateTimeMillis = 2_000L))

            dao.delete("e1")

            assertEquals(listOf("e2"), dao.observeAll().first().map { it.id })
        }

    @Test
    fun `getById devuelve el evento o null si no existe`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 1_000L))

            assertEquals("e1", dao.getById("e1")?.id)
            assertNull(dao.getById("no-existe"))
        }

    @Test
    fun `getAllWithReminder excluye los eventos sin recordatorio`() =
        runTest {
            dao.insert(event("sin-recordatorio", dateTimeMillis = 1_000L, reminderMinutesBefore = null))
            dao.insert(event("con-recordatorio", dateTimeMillis = 2_000L, reminderMinutesBefore = 60))

            val withReminder = dao.getAllWithReminder()

            assertEquals(listOf("con-recordatorio"), withReminder.map { it.id })
        }

    @Test
    fun `updateDocumentId migra los eventos vinculados al id nuevo`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 1_000L, documentId = "doc-viejo"))
            dao.insert(event("e2", dateTimeMillis = 2_000L, documentId = "otro-doc"))

            dao.updateDocumentId("doc-viejo", "doc-nuevo")

            assertEquals("doc-nuevo", dao.getById("e1")?.documentId)
            assertEquals("otro-doc", dao.getById("e2")?.documentId)
        }

    @Test
    fun `unlinkDocument desvincula sin borrar el evento`() =
        runTest {
            dao.insert(event("e1", dateTimeMillis = 1_000L, documentId = "doc-1"))

            dao.unlinkDocument("doc-1")

            val reloaded = dao.getById("e1")
            assertTrue(reloaded != null && reloaded.documentId == null)
        }
}
