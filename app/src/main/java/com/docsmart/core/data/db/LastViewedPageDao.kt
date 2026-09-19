package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LastViewedPageDao {
    // INSERT OR REPLACE -- mismo criterio que DocumentHistoryDao.recordOpen()
    // para esta entidad de 3 columnas con documentId como clave primaria.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: LastViewedPageEntity)

    @Query("SELECT * FROM last_viewed_page WHERE documentId = :documentId")
    suspend fun getByDocument(documentId: String): LastViewedPageEntity?

    @Query("DELETE FROM last_viewed_page WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)

    @Query("UPDATE last_viewed_page SET documentId = :newDocumentId WHERE documentId = :oldDocumentId")
    suspend fun updateDocumentId(
        oldDocumentId: String,
        newDocumentId: String,
    )
}
