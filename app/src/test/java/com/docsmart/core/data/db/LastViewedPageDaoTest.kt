package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Backlog UX #48 (recordar la última página vista): mismo estilo de prueba
 * de integración contra SQLite real que `DocumentHistoryDaoTest`.
 */
class LastViewedPageDaoTest {

    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: LastViewedPageDao

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.lastViewedPageDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getByDocument devuelve null si nunca se guardo nada`() = runTest {
        assertNull(dao.getByDocument("doc-1"))
    }

    @Test
    fun `save guarda la pagina y getByDocument la devuelve`() = runTest {
        dao.save(LastViewedPageEntity("doc-1", page = 7, updatedAt = 1000L))

        assertEquals(7, dao.getByDocument("doc-1")?.page)
    }

    @Test
    fun `save es upsert -- volver a guardar el mismo documento actualiza la pagina, no duplica`() = runTest {
        dao.save(LastViewedPageEntity("doc-1", page = 3, updatedAt = 1000L))
        dao.save(LastViewedPageEntity("doc-1", page = 9, updatedAt = 2000L))

        assertEquals(9, dao.getByDocument("doc-1")?.page)
    }

    @Test
    fun `deleteByDocument borra el registro`() = runTest {
        dao.save(LastViewedPageEntity("doc-1", page = 3, updatedAt = 1000L))

        dao.deleteByDocument("doc-1")

        assertNull(dao.getByDocument("doc-1"))
    }

    @Test
    fun `updateDocumentId migra la fila al id nuevo`() = runTest {
        dao.save(LastViewedPageEntity("doc-viejo", page = 5, updatedAt = 1000L))

        dao.updateDocumentId("doc-viejo", "doc-nuevo")

        assertNull(dao.getByDocument("doc-viejo"))
        assertEquals(5, dao.getByDocument("doc-nuevo")?.page)
    }
}
