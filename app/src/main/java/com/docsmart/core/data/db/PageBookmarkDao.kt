package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PageBookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: PageBookmarkEntity)

    @Query("DELETE FROM page_bookmarks WHERE documentId = :documentId AND page = :page")
    suspend fun delete(documentId: String, page: Int)

    @Query("DELETE FROM page_bookmarks WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)

    @Query("UPDATE page_bookmarks SET documentId = :newDocumentId WHERE documentId = :oldDocumentId")
    suspend fun updateDocumentId(oldDocumentId: String, newDocumentId: String)

    // Flow: la lista de marcadores del Visor se actualiza sola al marcar/
    // desmarcar, mismo criterio que AnnotationDao.observeByDocument().
    @Query("SELECT * FROM page_bookmarks WHERE documentId = :documentId ORDER BY page ASC")
    fun observeByDocument(documentId: String): Flow<List<PageBookmarkEntity>>
}
