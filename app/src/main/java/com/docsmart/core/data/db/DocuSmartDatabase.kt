package com.docsmart.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema = false: sin historial de migraciones que verificar todavía.
// Si se agregan más entidades/migraciones reales, vale la pena activar
// exportSchema + guardar los JSON en app/schemas para poder testear
// migraciones. version=2 agrega trash_entries (RF-VIS-07) -- sin migración
// real, ver fallbackToDestructiveMigration() en DatabaseModule: la única
// tabla previa (document_history) es solo un caché de "abierto
// recientemente", se regenera sola sin pérdida de datos del usuario.
@Database(
    entities = [DocumentHistoryEntry::class, TrashEntry::class],
    version = 2,
    exportSchema = false
)
abstract class DocuSmartDatabase : RoomDatabase() {
    abstract fun documentHistoryDao(): DocumentHistoryDao
    abstract fun trashDao(): TrashDao
}
