package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * RF-VIS-07: mismo patrón que `DocumentHistoryDaoTest` -- corre contra
 * SQLite real (`BundledSQLiteDriver`), no un fake en memoria, verificando
 * el DAO que Room genera de verdad.
 */
class TrashDaoTest {

    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: TrashDao

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.trashDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert agrega una entrada nueva`() = runTest {
        dao.insert(TrashEntry("doc-1", 1000L))

        assertEquals(listOf("doc-1"), dao.getAll().map { it.documentId })
    }

    @Test
    fun `insert es upsert - volver a eliminar el mismo documento actualiza la fecha, no duplica`() = runTest {
        dao.insert(TrashEntry("doc-1", 1000L))
        dao.insert(TrashEntry("doc-1", 2000L))

        val all = dao.getAll()

        assertEquals(1, all.size, "no debe duplicar la fila para el mismo documentId")
        assertEquals(2000L, all.first().deletedAt)
    }

    @Test
    fun `remove elimina la entrada y ya no aparece en getAll`() = runTest {
        dao.insert(TrashEntry("doc-1", 1000L))
        dao.insert(TrashEntry("doc-2", 2000L))

        dao.remove("doc-1")

        assertEquals(listOf("doc-2"), dao.getAll().map { it.documentId })
    }

    @Test
    fun `remove de un id que no existe no falla`() = runTest {
        dao.remove("nunca-existio")

        assertTrue(dao.getAll().isEmpty())
    }
}
