package com.docsmart.core.data

import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AnnotationDao
import com.docsmart.core.data.db.LastViewedPageDao
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.PageBookmarkDao
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Consolida el mantenimiento de las tablas por documento (favorito/alias,
 * anotaciones, marcadores de página, última página vista, notas vinculadas)
 * que antes vivía repetido en 11 sitios (TrashRepository, SecurityViewModel,
 * DocumentRepository.renameDocument) -- este test cubre que las tablas
 * reciben la llamada correspondiente en cada uno de los 2 escenarios, sin
 * tener que repetir esa cobertura en cada llamador.
 */
class DocumentIdentityMaintenanceTest {

    private val favoritesRepository = mockk<FavoritesRepository>()
    private val annotationDao       = mockk<AnnotationDao>()
    private val pageBookmarkDao     = mockk<PageBookmarkDao>()
    private val lastViewedPageDao   = mockk<LastViewedPageDao>()
    private val noteDao             = mockk<NoteDao>()
    private val agendaEventDao      = mockk<AgendaEventDao>()
    private val maintenance = DocumentIdentityMaintenance(
        favoritesRepository, annotationDao, pageBookmarkDao, lastViewedPageDao, noteDao, agendaEventDao
    )

    @Test
    fun `onIdChanged migra favorito, anotaciones, marcadores, ultima pagina vista, notas y agenda`() = runTest {
        coEvery { favoritesRepository.migrateId(any(), any()) } just Runs
        coEvery { annotationDao.updateDocumentId(any(), any()) } just Runs
        coEvery { pageBookmarkDao.updateDocumentId(any(), any()) } just Runs
        coEvery { lastViewedPageDao.updateDocumentId(any(), any()) } just Runs
        coEvery { noteDao.updateDocumentId(any(), any()) } just Runs
        coEvery { agendaEventDao.updateDocumentId(any(), any()) } just Runs

        maintenance.onIdChanged("viejo", "nuevo")

        coVerify { favoritesRepository.migrateId("viejo", "nuevo") }
        coVerify { annotationDao.updateDocumentId("viejo", "nuevo") }
        coVerify { pageBookmarkDao.updateDocumentId("viejo", "nuevo") }
        coVerify { lastViewedPageDao.updateDocumentId("viejo", "nuevo") }
        coVerify { noteDao.updateDocumentId("viejo", "nuevo") }
        coVerify { agendaEventDao.updateDocumentId("viejo", "nuevo") }
    }

    @Test
    fun `onPermanentlyDeleted limpia todas las tablas y desvincula notas y agenda`() = runTest {
        coEvery { favoritesRepository.removeAlias(any()) } just Runs
        coEvery { favoritesRepository.removeFavorite(any()) } just Runs
        coEvery { annotationDao.deleteByDocument(any()) } just Runs
        coEvery { pageBookmarkDao.deleteByDocument(any()) } just Runs
        coEvery { lastViewedPageDao.deleteByDocument(any()) } just Runs
        coEvery { noteDao.unlinkDocument(any()) } just Runs
        coEvery { agendaEventDao.unlinkDocument(any()) } just Runs

        maintenance.onPermanentlyDeleted("doc-1")

        coVerify { favoritesRepository.removeAlias("doc-1") }
        coVerify { favoritesRepository.removeFavorite("doc-1") }
        coVerify { annotationDao.deleteByDocument("doc-1") }
        coVerify { pageBookmarkDao.deleteByDocument("doc-1") }
        coVerify { lastViewedPageDao.deleteByDocument("doc-1") }
        // Backlog UX #50/HU-65, AC2/AC6: las notas y eventos se DESVINCULAN,
        // no se borran.
        coVerify { noteDao.unlinkDocument("doc-1") }
        coVerify { agendaEventDao.unlinkDocument("doc-1") }
    }
}
