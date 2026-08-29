package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// RF-VIS-07: registro de un documento movido a la papelera. El archivo/fila
// real (app o MediaStore) NO se toca al insertar esta entrada -- permanece
// intacto en su ubicación original hasta que se cumpla el plazo de retención
// (DocumentRepository.TRASH_RETENTION_DAYS) o el usuario lo elimine
// definitivamente. Mismo `documentId` que usan Biblioteca/Home/Favoritos.
@Entity(tableName = "trash_entries")
data class TrashEntry(
    @PrimaryKey val documentId: String,
    val deletedAt: Long
)
