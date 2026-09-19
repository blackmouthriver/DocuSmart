package com.docsmart.features.study.data

import android.content.Context
import android.net.Uri
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.features.study.domain.NoteReminderScheduler
import com.docsmart.features.study.domain.StudyNotesStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val NOTE_IMAGES_DIR = "note_images"

private const val LEGACY_PREFS_NAME = "study_notes"
private const val KEY_MIGRATED_TO_ROOM = "migrated_to_room"

/**
 * Backlog UX #49-#52: reemplaza `StudyNotesStorage` (SharedPreferences con
 * toda la lista de notas como un único JSON) por Room, agregando imágenes
 * adjuntas, vínculo a documento y recordatorio. `migrateLegacyNotesIfNeeded()`
 * mueve los datos reales de usuarios ya guardados con el mecanismo viejo --
 * se deja el JSON legado intacto en SharedPreferences (no se borra) como
 * respaldo silencioso, en vez de arriesgar perder notas si la migración
 * fallara a mitad de camino.
 */
@Singleton
class NoteRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val noteDao: NoteDao,
        private val noteReminderScheduler: NoteReminderScheduler,
    ) {
        // dd/MM/yyyy · HH:mm -- numérico, sin nombres de mes/día, así que
        // parsearlo no depende del idioma activo en el momento de la migración.
        private val legacyDateFormatter = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())

        // Hallazgo real de la auditoría de la capa de persistencia (Media):
        // updateNote()/linkDocument() hacían lectura-modificación-escritura
        // (getById + update(copy(...))) sin ningún lock -- dos escrituras casi
        // simultáneas sobre la MISMA nota podían pisarse en silencio ("lost
        // update"). Mismo mecanismo ya usado en AgendaRepository (ronda 12):
        // Mutex por id en un ConcurrentHashMap, con `computeIfAbsent` (NO
        // `getOrPut` de Kotlin, que no es atómico sobre ConcurrentHashMap).
        private val noteMutexes = ConcurrentHashMap<String, Mutex>()

        private suspend fun <T> withNoteLock(
            noteId: String,
            block: suspend () -> T,
        ): T = noteMutexes.computeIfAbsent(noteId) { Mutex() }.withLock { block() }

        @Suppress("TooGenericExceptionCaught")
        suspend fun migrateLegacyNotesIfNeeded() =
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_MIGRATED_TO_ROOM, false)) return@withContext
                try {
                    StudyNotesStorage.loadNotes(context).forEach { legacy ->
                        val createdAt =
                            parseLegacyDate(legacy.dateTime)
                                ?: legacy.id.toLongOrNull()
                                ?: System.currentTimeMillis()
                        val migrated =
                            NoteEntity(
                                id = legacy.id,
                                title = legacy.title,
                                text = legacy.text,
                                createdAt = createdAt,
                            )
                        // REPLACE: si la migración se interrumpió a mitad de camino
                        // (app cerrada) y se reintenta, reinsertar las mismas notas
                        // con el mismo id es un no-op, no genera duplicados.
                        noteDao.insert(migrated)
                    }
                    prefs.edit().putBoolean(KEY_MIGRATED_TO_ROOM, true).apply()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error migrando notas legadas a Room")
                }
            }

        // Un formato de fecha inesperado (dato legado corrupto/manual) no debe
        // frenar la migración -- se resuelve con los 2 fallbacks del llamador
        // (id legado, o la hora actual), no hace falta loguear cada fallo de
        // parseo individual.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun parseLegacyDate(dateTime: String): Long? =
            try {
                legacyDateFormatter.parse(dateTime)?.time
            } catch (e: Exception) {
                null
            }

        fun observeAll(): Flow<List<NoteWithImages>> = noteDao.observeAll()

        fun observeByDocument(documentId: String): Flow<List<NoteWithImages>> = noteDao.observeByDocument(documentId)

        fun observeLinkedCount(documentId: String): Flow<Int> = noteDao.observeLinkedCount(documentId)

        // Backlog UX #49: `imageUris` son URIs del selector del sistema o del
        // escáner de ML Kit (temporales, viven en el cache del proveedor) --
        // se copian a `filesDir/note_images/` antes de guardar la fila para que
        // la nota no dependa de un archivo ajeno que puede desaparecer.
        // Backlog UX #52: `reminderAt` (epoch millis, null = sin recordatorio)
        // programa la alarma recién con el id real ya generado -- no hace falta
        // un segundo paso de "actualizar" como en linkDocument().
        suspend fun createNote(
            title: String,
            text: String,
            documentId: String? = null,
            imageUris: List<Uri> = emptyList(),
            reminderAt: Long? = null,
        ): Unit =
            withContext(Dispatchers.IO) {
                val noteId = UUID.randomUUID().toString()
                noteDao.insert(
                    NoteEntity(
                        id = noteId,
                        title = title,
                        text = text,
                        createdAt = System.currentTimeMillis(),
                        documentId = documentId,
                        reminderAt = reminderAt,
                    ),
                )
                imageUris.forEachIndexed { position, uri ->
                    val filePath = copyImageToNoteStorage(noteId, position, uri) ?: return@forEachIndexed
                    noteDao.insertImage(NoteImageEntity(noteId = noteId, filePath = filePath, position = position))
                }
                if (reminderAt != null) noteReminderScheduler.schedule(noteId, title, reminderAt)
            }

        // Una imagen que falla al copiar (proveedor externo caído, formato raro)
        // no debe frenar la creación de la nota entera ni de las demás imágenes
        // -- se salta esa sola, el resto sigue su curso normal.
        @Suppress("TooGenericExceptionCaught")
        private fun copyImageToNoteStorage(
            noteId: String,
            position: Int,
            uri: Uri,
        ): String? {
            return try {
                val dir = File(context.filesDir, NOTE_IMAGES_DIR).apply { mkdirs() }
                val outFile = File(dir, "${noteId}_$position.jpg")
                val input = context.contentResolver.openInputStream(uri) ?: return null
                input.use { stream -> outFile.outputStream().use { output -> stream.copyTo(output) } }
                outFile.absolutePath
            } catch (e: Exception) {
                Timber.e(e, "Error copiando imagen adjunta a la nota $noteId")
                null
            }
        }

        // B22 (auditoría general 2026-09-17): no existía forma de editar una
        // nota ya guardada -- asimetría frente a Agenda, que sí permite editar
        // un evento. `keptImages`/`removedImages` vienen ya separadas por el
        // llamador (que tiene el `NoteWithImages` original completo desde la
        // lista) en vez de que este método tenga que recalcular la diferencia
        // -- evita una consulta extra a la base y dos fuentes de verdad sobre
        // "qué imágenes tenía la nota antes de editar".
        suspend fun updateNote(
            noteId: String,
            title: String,
            text: String,
            reminderAt: Long?,
            keptImages: List<NoteImageEntity>,
            removedImages: List<NoteImageEntity>,
            newImageUris: List<Uri>,
        ): Unit =
            withContext(Dispatchers.IO) {
                withNoteLock(noteId) {
                    val existing = noteDao.getById(noteId) ?: return@withNoteLock
                    noteDao.update(existing.copy(title = title, text = text, reminderAt = reminderAt))

                    removedImages.forEach { image ->
                        File(image.filePath).delete()
                        noteDao.deleteImage(image.id)
                    }
                    val nextPosition = (keptImages.maxOfOrNull { it.position } ?: -1) + 1
                    newImageUris.forEachIndexed { offset, uri ->
                        val position = nextPosition + offset
                        val filePath = copyImageToNoteStorage(noteId, position, uri) ?: return@forEachIndexed
                        noteDao.insertImage(NoteImageEntity(noteId = noteId, filePath = filePath, position = position))
                    }

                    // Mismo criterio que createNote(): reprograma/cancela el
                    // recordatorio real en vez de dejar la alarma vieja viva
                    // apuntando a un título desactualizado, o una nueva sin
                    // cancelar la anterior.
                    if (existing.reminderAt != null) noteReminderScheduler.cancel(noteId)
                    if (reminderAt != null) noteReminderScheduler.schedule(noteId, title, reminderAt)
                }
            }

        // Hallazgo real de la auditoría general 2026-09-17 (B22, descubierto de
        // paso al arreglar updateNote()): usaba noteDao.insert() (REPLACE) igual
        // que el bug ya corregido en updateNote() -- vincular/desvincular un
        // documento a una nota que YA tenía imágenes adjuntas borraba esas filas
        // de note_images por el CASCADE de SQLite (el archivo en disco quedaba
        // huérfano, la nota se veía sin sus imágenes al volver a abrirla).
        suspend fun linkDocument(
            noteId: String,
            documentId: String?,
        ) = withContext(Dispatchers.IO) {
            withNoteLock(noteId) {
                val note = noteDao.getById(noteId) ?: return@withNoteLock
                noteDao.update(note.copy(documentId = documentId))
            }
        }

        // Borra las copias de imagen en disco antes de la fila -- Room solo
        // limpia note_images vía CASCADE, no toca el sistema de archivos
        // (backlog UX #49, AC2: "no deja archivos huérfanos"). Backlog UX #52:
        // cancela también el recordatorio pendiente -- sin esto, la alarma ya
        // programada seguiría sonando y abriría una nota que ya no existe.
        // Revisión adversarial de correctitud (ronda 13, Media): a diferencia
        // de updateNote()/linkDocument(), deleteNote()/deleteAll() no tomaban
        // withNoteLock -- un autoguardado (updateNote: getById -> copy ->
        // insertImage) intercalado con un delete sin lock podía violar el
        // ForeignKey(onDelete=CASCADE) de note_images sobre una fila notes ya
        // borrada (SQLiteConstraintException real, no solo teórica). Mismo
        // criterio que AgendaRepository.deleteEvent(), que sí toma el lock.
        suspend fun deleteNote(note: NoteWithImages) =
            withContext(Dispatchers.IO) {
                withNoteLock(note.note.id) {
                    note.images.forEach { File(it.filePath).delete() }
                    if (note.note.reminderAt != null) noteReminderScheduler.cancel(note.note.id)
                    noteDao.delete(note.note.id)
                }
            }

        suspend fun deleteAll(notes: List<NoteWithImages>) =
            withContext(Dispatchers.IO) {
                notes.forEach { note ->
                    withNoteLock(note.note.id) {
                        note.images.forEach { File(it.filePath).delete() }
                        if (note.note.reminderAt != null) noteReminderScheduler.cancel(note.note.id)
                    }
                }
                noteDao.deleteAll()
            }
    }
