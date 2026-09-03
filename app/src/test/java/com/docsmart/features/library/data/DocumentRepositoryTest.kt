package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
    private lateinit var trashDao: FakeTrashDao
    private lateinit var repository: DocumentRepository

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_docrepo_files_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        historyDao = FakeDocumentHistoryDao()
        trashDao = FakeTrashDao()
        repository = DocumentRepository(
            context, mockk<FavoritesRepository>(relaxed = true), historyDao, trashDao,
            mockk<MediaDeletePermission>(relaxed = true), mockk<DownloadsAccessManager>(relaxed = true)
        )
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

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Deleted)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteDocument devuelve Failed si el archivo de la app no existe`() = runTest {
        val missing = File(filesDir, "no_existe.pdf")

        val deleted = repository.deleteDocument(missing.absolutePath)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Failed)
    }

    @Test
    fun `deleteDocument borra un documento de MediaStore via ContentResolver`() = runTest {
        val uriString = "content://media/external/downloads/12345"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.authority } returns "media"
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 1
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Deleted)
    }

    @Test
    fun `deleteDocument devuelve Failed si ContentResolver no pudo borrar`() = runTest {
        val uriString = "content://media/external/downloads/99999"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.authority } returns "media"
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 0
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Failed)
    }

    @Test
    fun `deleteDocument devuelve Failed si ContentResolver lanza excepcion generica`() = runTest {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.authority } returns "media"
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } throws SecurityException("no permission")
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Failed)
    }

    @Test
    fun `deleteDocument exitoso tambien borra el registro de historial`() = runTest {
        val dir = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }
        historyDao.recordOpen(DocumentHistoryEntry(file.absolutePath, 1000L))

        repository.deleteDocument(file.absolutePath)

        assertTrue(historyDao.recentDocumentIds(10).isEmpty())
    }

    // ── renameDocument (RF-VIS-06: extraído de Library/HomeViewModel para
    // reutilizarlo también desde el Visor) ────────────────────────────────

    @Test
    fun `renameDocument renombra un archivo real de la app y devuelve la nueva ruta`() = runTest {
        val favorites = mockk<FavoritesRepository>()
        coEvery { favorites.removeAlias(any()) } just Runs
        val repo = DocumentRepository(
            context, favorites, historyDao, trashDao,
            mockk<MediaDeletePermission>(relaxed = true), mockk<DownloadsAccessManager>(relaxed = true)
        )
        val dir  = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "original.pdf").apply { writeText("contenido") }

        val newId = repo.renameDocument(file.absolutePath, "nuevo.pdf")

        assertEquals(File(dir, "nuevo.pdf").absolutePath, newId)
        assertTrue(File(dir, "nuevo.pdf").exists())
        assertFalse(file.exists())
        coVerify { favorites.removeAlias(file.absolutePath) }
    }

    @Test
    fun `renameDocument de un documento de MediaStore usa alias sin tocar el archivo`() = runTest {
        val favorites = mockk<FavoritesRepository>()
        coEvery { favorites.saveAlias(any(), any()) } just Runs
        val repo = DocumentRepository(
            context, favorites, historyDao, trashDao,
            mockk<MediaDeletePermission>(relaxed = true), mockk<DownloadsAccessManager>(relaxed = true)
        )
        val uriString = "content://media/external/downloads/12345"

        val newId = repo.renameDocument(uriString, "Nuevo nombre.pdf")

        assertEquals(uriString, newId, "un documento de MediaStore conserva su id -- solo cambia el alias")
        coVerify { favorites.saveAlias(uriString, "Nuevo nombre.pdf") }
    }

    @Test
    fun `renameDocument cae a alias si el archivo de la app no se pudo mover`() = runTest {
        val favorites = mockk<FavoritesRepository>()
        coEvery { favorites.saveAlias(any(), any()) } just Runs
        val repo = DocumentRepository(
            context, favorites, historyDao, trashDao,
            mockk<MediaDeletePermission>(relaxed = true), mockk<DownloadsAccessManager>(relaxed = true)
        )
        val missing = File(filesDir, "no_existe.pdf") // File.renameTo() sobre un origen inexistente devuelve false

        val newId = repo.renameDocument(missing.absolutePath, "nuevo.pdf")

        assertEquals(missing.absolutePath, newId, "el id no cambia si el rename real falló")
        coVerify { favorites.saveAlias(missing.absolutePath, "nuevo.pdf") }
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

        override suspend fun allEntries(): List<DocumentHistoryEntry> =
            store.entries.sortedByDescending { it.value }.map { DocumentHistoryEntry(it.key, it.value) }

        override suspend fun remove(documentId: String) {
            store.remove(documentId)
        }
    }

    // ── fake de TrashDao respaldado por un mapa en memoria ────────────────────

    private class FakeTrashDao : TrashDao {
        private val store = mutableMapOf<String, TrashEntry>()

        override suspend fun insert(entry: TrashEntry) {
            store[entry.documentId] = entry
        }

        override suspend fun remove(documentId: String) {
            store.remove(documentId)
        }

        override suspend fun getAll(): List<TrashEntry> = store.values.toList()
    }
}
