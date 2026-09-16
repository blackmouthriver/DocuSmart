package com.docsmart.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Nota de Modo Estudio (backlog UX #49-#52). Reemplaza `SavedNote`/
 * `StudyNotesStorage` (SharedPreferences con toda la lista como un único
 * JSON) -- ese formato no admite `documentId` indexado, borrado en cascada
 * de imágenes adjuntas, ni un id estable para `WorkManager` (recordatorios).
 * `createdAt` reemplaza el `dateTime` ya formateado (String) de la entidad
 * vieja -- se formatea recién al mostrar, mismo criterio que el resto del
 * proyecto (`AnnotationEntity`, `PageBookmarkEntity`).
 *
 * `documentId` (#50): mismo id que Biblioteca/Home/Favoritos/Anotaciones
 * (Uri o ruta absoluta) -- null si la nota no está vinculada a ningún
 * documento. `reminderAt` (#52): epoch millis del recordatorio programado,
 * null si no hay ninguno.
 */
@Entity(tableName = "notes", indices = [Index("documentId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    val documentId: String? = null,
    val reminderAt: Long? = null
)

/**
 * Imagen/recorte adjunto a una nota (#49) -- copiada a almacenamiento
 * privado de la app (`filesDir/note_images/`, mismo criterio que
 * `SecurityManager`), nunca depende del original en la galería del
 * usuario. `onDelete = CASCADE`: borrar la nota borra sus filas de imagen
 * automáticamente (el archivo real en disco se borra aparte, Room solo
 * limpia la fila -- ver `NoteRepository.deleteNote()`).
 */
@Entity(
    tableName = "note_images",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val filePath: String,
    val position: Int
)

data class NoteWithImages(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val images: List<NoteImageEntity>
)
