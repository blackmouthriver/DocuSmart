package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Cubre el bug real encontrado en Biblioteca/Home (docs/requirements/visor-biblioteca.md):
 * `removeDocument()` solo filtraba la lista en memoria, nunca borraba el
 * archivo real — al recargar (`refresh`/reabrir la app) el documento
 * "eliminado" volvía a aparecer. `deleteDocument()` reemplaza ese hueco.
 * También cubre `mergeHistoryWithDocuments()` (RF-VIS/HOME): "recientes"
 * según uso real, no fecha de modificación del archivo.
 */
class DocumentRepositoryTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var historyDao: FakeDocumentHistoryDao
    private lateinit var repository: DocumentRepository

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_docrepo_files_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        historyDao = FakeDocumentHistoryDao()
        repository = DocumentRepository(context, mockk<FavoritesRepository>(), historyDao)
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        filesDir.deleteRecursively()
    }

    @Test
    fun `deleteDocument borra un archivo generado por la app`() = runTest {
        val dir = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }

        val deleted = repository.deleteDocument(file.absolutePath)

        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteDocument devuelve false si el archivo de la app no existe`() = runTest {
        val missing = File(filesDir, "no_existe.pdf")

        val deleted = repository.deleteDocument(missing.absolutePath)

        assertFalse(deleted)
    }

    @Test
    fun `deleteDocument borra un documento de MediaStore via ContentResolver`() = runTest {
        val uriString = "content://media/external/downloads/12345"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 1
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertTrue(deleted)
    }

    @Test
    fun `deleteDocument devuelve false si ContentResolver no pudo borrar`() = runTest {
        val uriString = "content://media/external/downloads/99999"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 0
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertFalse(deleted)
    }

    @Test
    fun `deleteDocument devuelve false si ContentResolver lanza excepcion de permisos`() = runTest {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } throws SecurityException("no permission")
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertFalse(deleted)
    }

    @Test
    fun `deleteDocument exitoso tambien borra el registro de historial`() = runTest {
        val dir = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }
        historyDao.recordOpen(DocumentHistoryEntry(file.absolutePath, 1000L))

        repository.deleteDocument(file.absolutePath)

        assertTrue(historyDao.recentDocumentIds(10).isEmpty())
    }

    // ── mergeHistoryWithDocuments (RF-VIS/HOME: recientes = uso real) ─────────

    private fun doc(id: String) = DocumentUiModel(
        id = id, name = id, type = DocumentType.PDF,
        size = "1 KB", date = "24/08/2026", isFavorite = false
    )

    @Test
    fun `mergeHistoryWithDocuments prioriza el orden del historial sobre la fecha de archivo`() {
        val all = listOf(doc("a"), doc("b"), doc("c")) // orden por fecha de archivo
        val recentIds = listOf("c", "a") // "c" se abrió más recientemente que "a"

        val result = repository.mergeHistoryWithDocuments(all, recentIds, limit = 2)

        assertEquals(listOf("c", "a"), result.map { it.id })
    }

    @Test
    fun `mergeHistoryWithDocuments ignora ids del historial que ya no existen en disco`() {
        val all = listOf(doc("a"), doc("b"))
        val recentIds = listOf("borrado_hace_tiempo", "a") // "borrado..." ya no está en `all`

        val result = repository.mergeHistoryWithDocuments(all, recentIds, limit = 2)

        assertEquals(listOf("a", "b"), result.map { it.id }, "debe completar con fallback sin duplicar")
    }

    @Test
    fun `mergeHistoryWithDocuments completa con los mas recientes por archivo si el historial no alcanza`() {
        val all = listOf(doc("a"), doc("b"), doc("c"))
        val recentIds = listOf("c") // solo un documento con historial real

        val result = repository.mergeHistoryWithDocuments(all, recentIds, limit = 3)

        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun `mergeHistoryWithDocuments sin historial se comporta como antes (orden por archivo)`() {
        val all = listOf(doc("a"), doc("b"), doc("c"))

        val result = repository.mergeHistoryWithDocuments(all, recentIds = emptyList(), limit = 2)

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    // ── fake de DocumentHistoryDao respaldado por un mapa en memoria ──────────

    private class FakeDocumentHistoryDao : DocumentHistoryDao {
        private val store = mutableMapOf<String, Long>()

        override suspend fun recordOpen(entry: DocumentHistoryEntry) {
            store[entry.documentId] = entry.lastOpenedAt
        }

        override suspend fun recentDocumentIds(limit: Int): List<String> =
            store.entries.sortedByDescending { it.value }.map { it.key }.take(limit)

        override suspend fun remove(documentId: String) {
            store.remove(documentId)
        }
    }
}
