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
 * Backlog UX #49-#52: mismo estilo de prueba de integración contra SQLite
 * real que `DocumentHistoryDaoTest`/`PageBookmarkDaoTest` -- confirma el SQL
 * generado por Room (`@Relation`, `ForeignKey(onDelete=CASCADE)`, upsert
 * real), no solo la firma del DAO.
 */
class NoteDaoTest {
    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: NoteDao

    @BeforeEach
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .build()
        dao = db.noteDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun note(
        id: String,
        title: String = "Título",
        documentId: String? = null,
        reminderAt: Long? = null,
    ) = NoteEntity(
        id = id,
        title = title,
        text = "Contenido de $id",
        createdAt = id.hashCode().toLong(),
        documentId = documentId,
        reminderAt = reminderAt,
    )

    @Test
    fun `insert agrega una nota nueva`() =
        runTest {
            dao.insert(note("n1"))

            assertEquals(listOf("n1"), dao.observeAll().first().map { it.note.id })
        }

    @Test
    fun `observeAll ordena de mas reciente a mas antiguo`() =
        runTest {
            dao.insert(NoteEntity("viejo", "t", "x", createdAt = 1000L))
            dao.insert(NoteEntity("nuevo", "t", "x", createdAt = 3000L))
            dao.insert(NoteEntity("medio", "t", "x", createdAt = 2000L))

            assertEquals(listOf("nuevo", "medio", "viejo"), dao.observeAll().first().map { it.note.id })
        }

    @Test
    fun `insertImage asocia la imagen a la nota via el Relation`() =
        runTest {
            dao.insert(note("n1"))
            dao.insertImage(NoteImageEntity(noteId = "n1", filePath = "/a.jpg", position = 0))
            dao.insertImage(NoteImageEntity(noteId = "n1", filePath = "/b.jpg", position = 1))

            val withImages = dao.observeAll().first().first()
            assertEquals(2, withImages.images.size)
            assertEquals(listOf("/a.jpg", "/b.jpg"), withImages.images.sortedBy { it.position }.map { it.filePath })
        }

    @Test
    fun `delete de una nota borra tambien sus filas de imagen via CASCADE`() =
        runTest {
            dao.insert(note("n1"))
            dao.insertImage(NoteImageEntity(noteId = "n1", filePath = "/a.jpg", position = 0))

            dao.delete("n1")

            assertTrue(dao.observeAll().first().isEmpty())
            // Si CASCADE no funcionara, esta fila de note_images seguiría
            // huérfana -- lo confirmamos indirectamente insertando otra nota
            // con el mismo id y verificando que no arrastra imágenes viejas.
            dao.insert(note("n1"))
            assertTrue(
                dao
                    .observeAll()
                    .first()
                    .first()
                    .images
                    .isEmpty(),
            )
        }

    @Test
    fun `deleteAll borra todas las notas`() =
        runTest {
            dao.insert(note("n1"))
            dao.insert(note("n2"))

            dao.deleteAll()

            assertTrue(dao.observeAll().first().isEmpty())
        }

    @Test
    fun `observeByDocument solo devuelve las notas vinculadas a ese documento`() =
        runTest {
            dao.insert(note("n1", documentId = "doc-1"))
            dao.insert(note("n2", documentId = "doc-2"))
            dao.insert(note("n3", documentId = "doc-1"))

            val linked = dao.observeByDocument("doc-1").first().map { it.note.id }

            assertEquals(setOf("n1", "n3"), linked.toSet())
        }

    @Test
    fun `observeLinkedCount cuenta las notas vinculadas a un documento`() =
        runTest {
            dao.insert(note("n1", documentId = "doc-1"))
            dao.insert(note("n2", documentId = "doc-1"))
            dao.insert(note("n3", documentId = null))

            assertEquals(2, dao.observeLinkedCount("doc-1").first())
        }

    @Test
    fun `updateDocumentId migra el vinculo al id nuevo`() =
        runTest {
            dao.insert(note("n1", documentId = "doc-viejo"))

            dao.updateDocumentId("doc-viejo", "doc-nuevo")

            assertEquals("doc-nuevo", dao.getById("n1")?.documentId)
        }

    @Test
    fun `unlinkDocument desvincula sin borrar la nota (AC2)`() =
        runTest {
            dao.insert(note("n1", documentId = "doc-1"))

            dao.unlinkDocument("doc-1")

            val stillThere = dao.getById("n1")
            assertEquals("n1", stillThere?.id)
            assertNull(stillThere?.documentId)
        }
}
