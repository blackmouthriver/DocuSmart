package com.docsmart.core.data

import androidx.room.withTransaction
import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AnnotationDao
import com.docsmart.core.data.db.DocuSmartDatabase
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.LastViewedPageDao
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.PageBookmarkDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolida el mantenimiento de las tablas "por documento" (favorito/alias,
 * anotaciones, marcadores de página, última página vista, notas vinculadas
 * de Modo Estudio, eventos de Agenda) que deben migrar juntas cuando el id
 * de un documento cambia (renombrar, mover a/desde Carpeta Segura,
 * restaurar) o limpiarse juntas cuando un documento se borra de forma
 * definitiva.
 *
 * Extraída al agregar la 3ra y 4ta tabla de este tipo (backlog UX #47/#48,
 * marcadores de página + última página vista): antes de esto, el mismo
 * bloque de 2-3 líneas (`favoritesRepository.migrateId()` +
 * `annotationDao.updateDocumentId()`, o `removeAlias()` + `removeFavorite()`
 * + `deleteByDocument()`) estaba repetido en 11 sitios distintos
 * (`TrashRepository` ×6, `SecurityViewModel` ×4, `DocumentRepository.
 * renameDocument()` ×1) -- agregar 2 tablas más ahí habría dejado 22
 * llamadas nuevas dispersas en vez de una sola.
 */
@Singleton
class DocumentIdentityMaintenance
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
        private val annotationDao: AnnotationDao,
        private val pageBookmarkDao: PageBookmarkDao,
        private val lastViewedPageDao: LastViewedPageDao,
        private val noteDao: NoteDao,
        private val agendaEventDao: AgendaEventDao,
        private val documentHistoryDao: DocumentHistoryDao,
        // Hallazgo real de la auditoría de la capa de persistencia (Alta): las
        // escrituras de abajo tocan 6-7 tablas distintas sin ninguna garantía
        // de atomicidad -- un proceso muerto a mitad de camino dejaba la
        // identidad del documento parcialmente migrada entre tablas. Se envuelve
        // el cuerpo completo de cada método en `database.withTransaction {}`
        // para que sea todo o nada.
        private val database: DocuSmartDatabase,
    ) {
        suspend fun onIdChanged(
            oldId: String,
            newId: String,
        ) {
            database.withTransaction {
                favoritesRepository.migrateId(oldId, newId)
                annotationDao.updateDocumentId(oldId, newId)
                pageBookmarkDao.updateDocumentId(oldId, newId)
                lastViewedPageDao.updateDocumentId(oldId, newId)
                noteDao.updateDocumentId(oldId, newId)
                agendaEventDao.updateDocumentId(oldId, newId)
                // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
                // Media -- B1): sin esto, la fila de "Recientes" quedaba
                // apuntando al id viejo (huérfana), y el documento perdía su
                // posición real en Home (caía al fallback por fecha de archivo
                // en vez de por fecha de apertura real).
                documentHistoryDao.updateDocumentId(oldId, newId)
            }
        }

        suspend fun onPermanentlyDeleted(documentId: String) {
            database.withTransaction {
                favoritesRepository.removeAlias(documentId)
                favoritesRepository.removeFavorite(documentId)
                annotationDao.deleteByDocument(documentId)
                pageBookmarkDao.deleteByDocument(documentId)
                lastViewedPageDao.deleteByDocument(documentId)
                // Backlog UX #50, AC2: borrar el documento vinculado NO debe
                // borrar la nota -- a diferencia de las demás tablas de arriba,
                // acá solo se limpia el vínculo (documentId a null), el
                // contenido escrito queda intacto.
                noteDao.unlinkDocument(documentId)
                // HU-65, AC6: mismo criterio que las notas -- borrar el
                // documento vinculado desvincula el evento de Agenda, no lo
                // borra.
                agendaEventDao.unlinkDocument(documentId)
            }
        }
    }
