package com.docsmart.core.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Las migraciones de Room son SQL en texto: sin este test un error de tipeo
 * solo aparecería al actualizar la app en el dispositivo de un usuario real.
 * Se capturan las sentencias ejecutadas y se comprueba tablas, índices y el
 * rango de versiones de cada una; [DatabaseModule] se cubre con una base
 * simulada (los DAO reales ya se prueban con Room en memoria).
 */
class DatabaseMigrationsTest {
    private fun run(migration: androidx.room.migration.Migration): List<String> {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(capture(statements)) } just Runs
        migration.migrate(db)
        return statements
    }

    @Test
    fun `cada migracion cubre el salto de version esperado`() {
        assertEquals(2 to 3, MIGRATION_2_3.startVersion to MIGRATION_2_3.endVersion)
        assertEquals(3 to 4, MIGRATION_3_4.startVersion to MIGRATION_3_4.endVersion)
        assertEquals(4 to 5, MIGRATION_4_5.startVersion to MIGRATION_4_5.endVersion)
        assertEquals(5 to 6, MIGRATION_5_6.startVersion to MIGRATION_5_6.endVersion)
    }

    @Test
    fun `2 a 3 crea annotations con su indice`() {
        val sql = run(MIGRATION_2_3)
        assertEquals(2, sql.size)
        assertTrue(sql[0].contains("CREATE TABLE IF NOT EXISTS annotations"))
        assertTrue(sql[1].contains("index_annotations_documentId"))
    }

    @Test
    fun `3 a 4 crea marcadores y ultima pagina vista`() {
        val sql = run(MIGRATION_3_4)
        assertEquals(3, sql.size)
        assertTrue(sql[0].contains("page_bookmarks"))
        assertTrue(sql[1].contains("index_page_bookmarks_documentId"))
        assertTrue(sql[2].contains("last_viewed_page"))
    }

    @Test
    fun `4 a 5 crea notes y note_images con clave foranea`() {
        val sql = run(MIGRATION_4_5)
        assertEquals(4, sql.size)
        assertTrue(sql[0].contains("CREATE TABLE IF NOT EXISTS notes"))
        assertTrue(sql[2].contains("note_images"))
        assertTrue(sql[2].contains("ON DELETE CASCADE"))
    }

    @Test
    fun `5 a 6 crea agenda_events con su indice`() {
        val sql = run(MIGRATION_5_6)
        assertEquals(2, sql.size)
        assertTrue(sql[0].contains("agenda_events"))
        assertTrue(sql[1].contains("index_agenda_events_documentId"))
    }

    @Test
    fun `DatabaseModule expone cada DAO de la base`() {
        val db = mockk<DocuSmartDatabase>()
        val history = mockk<DocumentHistoryDao>()
        val trash = mockk<TrashDao>()
        val annotation = mockk<AnnotationDao>()
        val bookmark = mockk<PageBookmarkDao>()
        val lastViewed = mockk<LastViewedPageDao>()
        val note = mockk<NoteDao>()
        val agenda = mockk<AgendaEventDao>()
        every { db.documentHistoryDao() } returns history
        every { db.trashDao() } returns trash
        every { db.annotationDao() } returns annotation
        every { db.pageBookmarkDao() } returns bookmark
        every { db.lastViewedPageDao() } returns lastViewed
        every { db.noteDao() } returns note
        every { db.agendaEventDao() } returns agenda

        assertEquals(history, DatabaseModule.provideDocumentHistoryDao(db))
        assertEquals(trash, DatabaseModule.provideTrashDao(db))
        assertEquals(annotation, DatabaseModule.provideAnnotationDao(db))
        assertEquals(bookmark, DatabaseModule.providePageBookmarkDao(db))
        assertEquals(lastViewed, DatabaseModule.provideLastViewedPageDao(db))
        assertEquals(note, DatabaseModule.provideNoteDao(db))
        assertEquals(agenda, DatabaseModule.provideAgendaEventDao(db))
    }
}
