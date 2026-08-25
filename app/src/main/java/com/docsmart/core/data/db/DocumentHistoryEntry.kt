package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registra cuándo el usuario abrió realmente un documento — a diferencia de
 * la fecha de modificación del archivo (usada como respaldo en
 * DocumentRepository), esto refleja uso real, no metadata del archivo.
 * documentId es el mismo id que usan Biblioteca/Home/Favoritos (Uri o ruta
 * absoluta como String).
 */
@Entity(tableName = "document_history")
data class DocumentHistoryEntry(
    @PrimaryKey val documentId: String,
    val lastOpenedAt: Long
)
