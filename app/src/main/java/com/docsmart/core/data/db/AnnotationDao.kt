package com.docsmart.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)

    // Hallazgo real de la revisión general 2026-09-16: nada limpiaba ni
    // migraba las anotaciones de un documento al borrarlo definitivamente,
    // renombrarlo (cambio real de ruta), o moverlo a/desde Carpeta Segura --
    // quedaban filas huérfanas para siempre y, desde el punto de vista del
    // usuario, sus resaltados/notas "desaparecían" sin aviso.
    @Query("DELETE FROM annotations WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)

    @Query("UPDATE annotations SET documentId = :newDocumentId WHERE documentId = :oldDocumentId")
    suspend fun updateDocumentId(
        oldDocumentId: String,
        newDocumentId: String,
    )

    // Flow: la UI del Visor refleja altas/bajas sin recargar manualmente
    // (mismo criterio de observación reactiva que ya usa el resto del
    // proyecto para listas que cambian mientras la pantalla está abierta).
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY createdAt ASC")
    fun observeByDocument(documentId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY createdAt ASC")
    suspend fun getByDocument(documentId: String): List<AnnotationEntity>
}
