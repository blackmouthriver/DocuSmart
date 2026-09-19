package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AgendaEventEntity)

    @Query("DELETE FROM agenda_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM agenda_events ORDER BY dateTimeMillis ASC")
    fun observeAll(): Flow<List<AgendaEventEntity>>

    @Query("SELECT * FROM agenda_events WHERE id = :id")
    suspend fun getById(id: String): AgendaEventEntity?

    // Todos los eventos con recordatorio activo -- usado por
    // BootRescheduleReceiver para volver a programar las alarmas exactas
    // tras un reinicio del dispositivo (AlarmManager las pierde al apagarse).
    @Query("SELECT * FROM agenda_events WHERE reminderMinutesBefore IS NOT NULL")
    suspend fun getAllWithReminder(): List<AgendaEventEntity>

    @Query("UPDATE agenda_events SET documentId = :newDocumentId WHERE documentId = :oldDocumentId")
    suspend fun updateDocumentId(
        oldDocumentId: String,
        newDocumentId: String,
    )

    // Borrar el documento vinculado no debe borrar el evento -- mismo
    // criterio ya usado para Notas (backlog UX #50, AC2), solo se limpia
    // el vínculo.
    @Query("UPDATE agenda_events SET documentId = null WHERE documentId = :documentId")
    suspend fun unlinkDocument(documentId: String)
}
