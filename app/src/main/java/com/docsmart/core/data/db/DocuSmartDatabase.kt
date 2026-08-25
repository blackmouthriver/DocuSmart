package com.docsmart.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema = false: primera tabla, sin historial de migraciones que
// verificar todavía. Si se agregan más entidades/migraciones reales, vale la
// pena activar exportSchema + guardar los JSON en app/schemas para poder
// testear migraciones.
@Database(
    entities = [DocumentHistoryEntry::class],
    version = 1,
    exportSchema = false
)
abstract class DocuSmartDatabase : RoomDatabase() {
    abstract fun documentHistoryDao(): DocumentHistoryDao
}
