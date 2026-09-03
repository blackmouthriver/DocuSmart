package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DocumentHistoryDao {

    // INSERT OR REPLACE en vez de @Upsert: para esta entidad de 2 columnas
    // es equivalente (reemplaza la fila completa por documentId), con SQL
    // generado más simple. @Upsert (insert + catch conflicto + update en dos
    // pasos) no tradujo bien la excepción de conflicto con
    // BundledSQLiteDriver en las pruebas de integración — quedó como
    // android.database.SQLException sin causa legible.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordOpen(entry: DocumentHistoryEntry)

    // limit mayor al pedido por el llamador a propósito: algunos ids del
    // historial pueden ya no existir en disco (archivo borrado/movido) y se
    // filtran después — pedir de más da margen para completar igual la lista.
    @Query("SELECT documentId FROM document_history ORDER BY lastOpenedAt DESC LIMIT :limit")
    suspend fun recentDocumentIds(limit: Int): List<String>

    // Biblioteca (fila 22 backlog UX, ampliación 2026-09-03): a diferencia de
    // recentDocumentIds() (acotado para "Recientes" en Inicio), esta trae
    // TODO el historial -- cualquier documento que el usuario haya abierto
    // alguna vez vía "Abrir con DocuSmart" o el selector de archivos queda
    // visible en Biblioteca de forma permanente, no solo mientras esté entre
    // los más recientes.
    @Query("SELECT * FROM document_history ORDER BY lastOpenedAt DESC")
    suspend fun allEntries(): List<DocumentHistoryEntry>

    @Query("DELETE FROM document_history WHERE documentId = :documentId")
    suspend fun remove(documentId: String)
}
