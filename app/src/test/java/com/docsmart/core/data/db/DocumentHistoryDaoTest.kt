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
 * Primera prueba de integración del proyecto: corre contra SQLite real
 * (BundledSQLiteDriver) en vez de un fake en memoria, verificando el DAO
 * generado por Room (queries SQL reales, upsert real, orden real) — algo
 * que un fake no puede confirmar por sí solo. Sin Robolectric ni emulador,
 * siguiendo la recomendación oficial de Google para pruebas de Room en JVM.
 */
class DocumentHistoryDaoTest {

    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: DocumentHistoryDao

    @BeforeEach
    fun setUp() {
        // Context no se usa realmente: la base es en memoria y
        // BundledSQLiteDriver maneja el I/O real, sin tocar el framework de
        // Android — el artefacto Android de Room igual exige el parámetro.
        db = Room.inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.documentHistoryDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recordOpen inserta un registro nuevo`() = runTest {
        dao.recordOpen(DocumentHistoryEntry("doc-1", 1000L))

        assertEquals(listOf("doc-1"), dao.recentDocumentIds(10))
    }

    @Test
    fun `recordOpen es upsert - abrir el mismo documento otra vez actualiza la fecha, no duplica`() = runTest {
        dao.recordOpen(DocumentHistoryEntry("doc-1", 1000L))
        dao.recordOpen(DocumentHistoryEntry("doc-2", 2000L))
        dao.recordOpen(DocumentHistoryEntry("doc-1", 3000L)) // reabrir doc-1, más reciente que doc-2

        val ids = dao.recentDocumentIds(10)

        assertEquals(listOf("doc-1", "doc-2"), ids, "doc-1 debe quedar primero tras reabrirse, sin duplicarse")
    }

    @Test
    fun `recentDocumentIds ordena del mas reciente al mas antiguo`() = runTest {
        dao.recordOpen(DocumentHistoryEntry("viejo", 1000L))
        dao.recordOpen(DocumentHistoryEntry("nuevo", 3000L))
        dao.recordOpen(DocumentHistoryEntry("medio", 2000L))

        assertEquals(listOf("nuevo", "medio", "viejo"), dao.recentDocumentIds(10))
    }

    @Test
    fun `recentDocumentIds respeta el limite pedido`() = runTest {
        (1..5).forEach { i -> dao.recordOpen(DocumentHistoryEntry("doc-$i", i.toLong())) }

        val ids = dao.recentDocumentIds(2)

        assertEquals(2, ids.size)
        assertEquals(listOf("doc-5", "doc-4"), ids)
    }

    @Test
    fun `remove elimina el registro y ya no aparece en recentDocumentIds`() = runTest {
        dao.recordOpen(DocumentHistoryEntry("doc-1", 1000L))
        dao.recordOpen(DocumentHistoryEntry("doc-2", 2000L))

        dao.remove("doc-1")

        assertEquals(listOf("doc-2"), dao.recentDocumentIds(10))
    }

    @Test
    fun `remove de un id que no existe no falla`() = runTest {
        dao.remove("nunca-existio")

        assertTrue(dao.recentDocumentIds(10).isEmpty())
    }
}
