package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DocumentHistoryDao {

    @Upsert
    suspend fun recordOpen(entry: DocumentHistoryEntry)

    // limit mayor al pedido por el llamador a propósito: algunos ids del
    // historial pueden ya no existir en disco (archivo borrado/movido) y se
    // filtran después — pedir de más da margen para completar igual la lista.
    @Query("SELECT documentId FROM document_history ORDER BY lastOpenedAt DESC LIMIT :limit")
    suspend fun recentDocumentIds(limit: Int): List<String>

    @Query("DELETE FROM document_history WHERE documentId = :documentId")
    suspend fun remove(documentId: String)
}
