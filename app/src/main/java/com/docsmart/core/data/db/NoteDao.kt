package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// TooManyFunctions de detekt: un DAO de Room agrupa naturalmente muchas
// operaciones pequeñas e independientes -- dividirlo en varias interfaces
// no reduciría la complejidad real, solo la escondería detrás de más
// archivos.
@Suppress("TooManyFunctions")
@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    // Hallazgo real de la auditoría general 2026-09-17 (B22, descubierto
    // por el propio test nuevo de updateNote): @Insert(REPLACE) sobre un id
    // que YA existe hace que SQLite borre la fila vieja e inserte una
    // nueva -- como note_images tiene FK a notes.id con
    // onDelete=CASCADE, ese borrado interno arrastraba TODAS las imágenes
    // de la nota (fila de la base, no el archivo en disco) aunque nunca se
    // hubiera pedido borrar ninguna. @Update genera un UPDATE real, sin
    // pasar por ese camino.
    @Update
    suspend fun update(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: NoteImageEntity)

    // No hace falta borrar note_images a mano -- ForeignKey(onDelete=CASCADE)
    // limpia esas filas sola. El archivo real en disco de cada imagen se
    // borra aparte (Room no toca el sistema de archivos), ver
    // NoteRepository.deleteNote().
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteWithImages>>

    @Transaction
    @Query("SELECT * FROM notes WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun observeByDocument(documentId: String): Flow<List<NoteWithImages>>

    // Backlog UX #50 (indicador en el Visor): más liviano que observar la
    // lista completa de notas cuando solo hace falta saber "¿hay alguna?".
    @Query("SELECT COUNT(*) FROM notes WHERE documentId = :documentId")
    fun observeLinkedCount(documentId: String): Flow<Int>

    @Query("UPDATE notes SET documentId = :newDocumentId WHERE documentId = :oldDocumentId")
    suspend fun updateDocumentId(oldDocumentId: String, newDocumentId: String)

    // Backlog UX #50, AC2: borrar el documento vinculado no borra la nota --
    // solo desvincula (documentId a null), el contenido escrito no se pierde.
    @Query("UPDATE notes SET documentId = null WHERE documentId = :documentId")
    suspend fun unlinkDocument(documentId: String)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    // B22 (auditoría general 2026-09-17): borra una sola imagen adjunta al
    // editar una nota ya guardada -- el archivo real en disco se borra
    // aparte (mismo criterio que delete()/NoteRepository.deleteNote()).
    @Query("DELETE FROM note_images WHERE id = :id")
    suspend fun deleteImage(id: Long)

    // Backlog UX #52: recupera las notas con recordatorio pendiente para
    // reprogramarlas tras un reinicio del dispositivo (AlarmManager pierde
    // todas sus alarmas al apagarse) -- ver BootRescheduleReceiver.
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL")
    suspend fun getAllWithReminder(): List<NoteEntity>
}
