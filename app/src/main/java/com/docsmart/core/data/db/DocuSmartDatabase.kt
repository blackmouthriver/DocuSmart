package com.docsmart.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// exportSchema = false: sin historial de migraciones que verificar todavía.
// version=3 agrega la tabla `annotations` (HU-46) vía MIGRATION_2_3 --
// a diferencia del salto anterior (version=2, trash_entries), acá SÍ hace
// falta una migración explícita en vez de fallbackToDestructiveMigration:
// `trash_entries` ya contiene datos reales de usuarios en producción
// (documentos movidos a la papelera) y dropAllTables=true en
// DatabaseModule borraría esa tabla completa en cuanto alguien actualice a
// esta versión si no hay una ruta de migración real registrada.
@Database(
    entities = [DocumentHistoryEntry::class, TrashEntry::class, AnnotationEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(AnnotationTypeConverter::class)
abstract class DocuSmartDatabase : RoomDatabase() {
    abstract fun documentHistoryDao(): DocumentHistoryDao
    abstract fun trashDao(): TrashDao
    abstract fun annotationDao(): AnnotationDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
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
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_documentId ON annotations(documentId)")
    }
}
