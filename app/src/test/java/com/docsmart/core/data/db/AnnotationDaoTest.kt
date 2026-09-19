package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * HU-46: mismo patrón que `TrashDaoTest`/`DocumentHistoryDaoTest` -- corre
 * contra SQLite real (`BundledSQLiteDriver`), no un fake en memoria.
 */
class AnnotationDaoTest {
    private lateinit var db: DocuSmartDatabase
    private lateinit var dao: AnnotationDao

    @BeforeEach
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(mockk<Context>(relaxed = true), DocuSmartDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .build()
        dao = db.annotationDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun highlight(
        id: String,
        documentId: String = "doc-1",
        page: Int = 1,
    ) = AnnotationEntity(
        id = id,
        documentId = documentId,
        type = AnnotationType.HIGHLIGHT,
        page = page,
        xPts = 10f,
        yPts = 20f,
        widthPts = 100f,
        heightPts = 15f,
        color = 0xFFFFEB3B.toInt(),
        text = "",
        createdAt = 1000L,
    )

    private fun note(
        id: String,
        documentId: String = "doc-1",
        page: Int = 1,
        text: String = "Recordar esto",
    ) = AnnotationEntity(
        id = id,
        documentId = documentId,
        type = AnnotationType.NOTE,
        page = page,
        xPts = 30f,
        yPts = 40f,
        widthPts = 0f,
        heightPts = 0f,
        color = 0xFFFF7043.toInt(),
        text = text,
        createdAt = 2000L,
    )

    @Test
    fun `insert agrega una anotacion nueva`() =
        runTest {
            dao.insert(highlight("a1"))

            val all = dao.getByDocument("doc-1")

            assertEquals(1, all.size)
            assertEquals(AnnotationType.HIGHLIGHT, all.first().type)
        }

    @Test
    fun `getByDocument no devuelve anotaciones de otro documento`() =
        runTest {
            dao.insert(highlight("a1", documentId = "doc-1"))
            dao.insert(highlight("a2", documentId = "doc-2"))

            val docOne = dao.getByDocument("doc-1")

            assertEquals(listOf("a1"), docOne.map { it.id })
        }

    @Test
    fun `insert conserva texto y color reales de una nota`() =
        runTest {
            dao.insert(note("n1", text = "Revisar la clausula 4"))

            val saved = dao.getByDocument("doc-1").first()

            assertEquals("Revisar la clausula 4", saved.text)
            assertEquals(0xFFFF7043.toInt(), saved.color)
            assertEquals(AnnotationType.NOTE, saved.type)
        }

    @Test
    fun `delete elimina solo la anotacion indicada`() =
        runTest {
            dao.insert(highlight("a1"))
            dao.insert(note("n1"))

            dao.delete("a1")

            val remaining = dao.getByDocument("doc-1")
            assertEquals(listOf("n1"), remaining.map { it.id })
        }

    @Test
    fun `delete de un id que no existe no falla`() =
        runTest {
            dao.delete("nunca-existio")

            assertTrue(dao.getByDocument("doc-1").isEmpty())
        }

    @Test
    fun `observeByDocument refleja un insert posterior en una suscripcion nueva`() =
        runTest {
            assertTrue(dao.observeByDocument("doc-1").first().isEmpty())

            dao.insert(highlight("a1"))

            val afterInsert = dao.observeByDocument("doc-1").first()
            assertEquals(listOf("a1"), afterInsert.map { it.id })
        }

    // Hallazgo de la revisión de correctitud HU-46: el test de arriba
    // verifica dos suscripciones NUEVAS (una antes, otra después del
    // insert) -- no prueba el patrón real que usa ViewerViewModel.
    // observeAnnotations() (`dao.observeByDocument(id).collect { ... }`,
    // UN solo colector activo de principio a fin). Con Turbine se verifica
    // que ese colector activo reciba la nueva emisión sin volver a
    // suscribirse.
    @Test
    fun `observeByDocument emite una fila nueva a un colector ya activo, sin resuscribirse`() =
        runTest {
            dao.observeByDocument("doc-1").test {
                assertTrue(awaitItem().isEmpty())

                dao.insert(highlight("a1"))

                assertEquals(listOf("a1"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insert con el mismo id reemplaza en vez de duplicar`() =
        runTest {
            dao.insert(highlight("a1"))
            dao.insert(highlight("a1").copy(color = 0xFF000000.toInt()))

            val all = dao.getByDocument("doc-1")

            assertEquals(1, all.size)
            assertEquals(0xFF000000.toInt(), all.first().color)
        }

    @Test
    fun `una anotacion recien creada no existe hasta insertarla`() =
        runTest {
            assertNull(dao.getByDocument("doc-1").find { it.id == "a1" })
        }
}
