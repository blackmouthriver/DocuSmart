package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrashDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntry)

    @Query("DELETE FROM trash_entries WHERE documentId = :documentId")
    suspend fun remove(documentId: String)

    @Query("SELECT * FROM trash_entries")
    suspend fun getAll(): List<TrashEntry>
}
