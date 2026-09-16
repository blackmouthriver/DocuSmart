package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Backlog UX #47 (marcadores de página): mismo estilo de prueba de
 * integración contra SQLite real que `DocumentHistoryDaoTest` -- confirma
 * el SQL generado por Room (clave primaria compuesta, upsert real, orden
 * real), no solo la firma del DAO.
 */
class PageBookmarkDaoTest {

    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: PageBookmarkDao

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.pageBookmarkDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert agrega un marcador nuevo`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 3, createdAt = 1000L))

        assertEquals(listOf(3), dao.observeByDocument("doc-1").first().map { it.page })
    }

    @Test
    fun `insert de la misma pagina dos veces no duplica (clave primaria compuesta)`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 3, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-1", page = 3, createdAt = 2000L))

        assertEquals(1, dao.observeByDocument("doc-1").first().size)
    }

    @Test
    fun `observeByDocument ordena las paginas ascendente`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 5, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-1", page = 1, createdAt = 2000L))
        dao.insert(PageBookmarkEntity("doc-1", page = 3, createdAt = 3000L))

        assertEquals(listOf(1, 3, 5), dao.observeByDocument("doc-1").first().map { it.page })
    }

    @Test
    fun `observeByDocument no mezcla marcadores de otro documento`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 1, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-2", page = 2, createdAt = 1000L))

        assertEquals(listOf(1), dao.observeByDocument("doc-1").first().map { it.page })
    }

    @Test
    fun `delete quita solo la pagina indicada`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 1, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-1", page = 2, createdAt = 1000L))

        dao.delete("doc-1", page = 1)

        assertEquals(listOf(2), dao.observeByDocument("doc-1").first().map { it.page })
    }

    @Test
    fun `deleteByDocument borra todos los marcadores del documento`() = runTest {
        dao.insert(PageBookmarkEntity("doc-1", page = 1, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-1", page = 2, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-2", page = 1, createdAt = 1000L))

        dao.deleteByDocument("doc-1")

        assertTrue(dao.observeByDocument("doc-1").first().isEmpty())
        assertEquals(1, dao.observeByDocument("doc-2").first().size)
    }

    @Test
    fun `updateDocumentId migra los marcadores al id nuevo`() = runTest {
        dao.insert(PageBookmarkEntity("doc-viejo", page = 1, createdAt = 1000L))
        dao.insert(PageBookmarkEntity("doc-viejo", page = 4, createdAt = 2000L))

        dao.updateDocumentId("doc-viejo", "doc-nuevo")

        assertTrue(dao.observeByDocument("doc-viejo").first().isEmpty())
        assertEquals(listOf(1, 4), dao.observeByDocument("doc-nuevo").first().map { it.page })
    }
}
