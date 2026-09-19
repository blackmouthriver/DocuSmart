package com.docsmart.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// exportSchema = false: sin historial de migraciones que verificar todavía.
// version=6 agrega `agenda_events` (HU-65, backlog UX 2026-09-16) vía
// MIGRATION_5_6, mismo criterio que los saltos anteriores: dropAllTables=true
// en DatabaseModule borraría datos reales de usuarios si no hay una ruta de
// migración real registrada para cada versión nueva.
@Database(
    entities = [
        DocumentHistoryEntry::class, TrashEntry::class, AnnotationEntity::class,
        PageBookmarkEntity::class, LastViewedPageEntity::class,
        NoteEntity::class, NoteImageEntity::class, AgendaEventEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(AnnotationTypeConverter::class)
abstract class DocuSmartDatabase : RoomDatabase() {
    abstract fun documentHistoryDao(): DocumentHistoryDao

    abstract fun trashDao(): TrashDao

    abstract fun annotationDao(): AnnotationDao

    abstract fun pageBookmarkDao(): PageBookmarkDao

    abstract fun lastViewedPageDao(): LastViewedPageDao

    abstract fun noteDao(): NoteDao

    abstract fun agendaEventDao(): AgendaEventDao
}

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS annotations (
                    id TEXT NOT NULL PRIMARY KEY,
                    documentId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    page INTEGER NOT NULL,
                    xPts REAL NOT NULL,
                    yPts REAL NOT NULL,
                    widthPts REAL NOT NULL,
                    heightPts REAL NOT NULL,
                    color INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_documentId ON annotations(documentId)")
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS page_bookmarks (
                    documentId TEXT NOT NULL,
                    page INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(documentId, page)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_page_bookmarks_documentId ON page_bookmarks(documentId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS last_viewed_page (
                    documentId TEXT NOT NULL PRIMARY KEY,
                    page INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    text TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    documentId TEXT,
                    reminderAt INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_documentId ON notes(documentId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_images (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    noteId TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_note_images_noteId ON note_images(noteId)")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agenda_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    dateTimeMillis INTEGER NOT NULL,
                    documentId TEXT,
                    reminderMinutesBefore INTEGER,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_agenda_events_documentId ON agenda_events(documentId)")
        }
    }
