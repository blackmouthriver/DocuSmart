package com.docsmart.features.library.data

import android.content.Context
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
 * RF-VIS-07: "eliminar" desde Biblioteca/Home/Visor mueve a la papelera en
 * vez de borrar de inmediato -- el archivo/fila real permanece intacto
 * hasta que se restaura, se elimina definitivamente, o vence
 * `TRASH_RETENTION_DAYS` (purga automática).
 */
class TrashRepositoryTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var historyDao: FakeDocumentHistoryDao
    private lateinit var trashDao: FakeTrashDao
    private lateinit var favorites: FavoritesRepository
    private lateinit var documentRepository: DocumentRepository
    private lateinit var repository: TrashRepository

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_trashrepo_files_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        historyDao = FakeDocumentHistoryDao()
        trashDao = FakeTrashDao()
        favorites = mockk()
        coEvery { favorites.removeAlias(any()) } just Runs
        val mediaDeletePermission = mockk<MediaDeletePermission>(relaxed = true)
        documentRepository = DocumentRepository(
            context, favorites, historyDao, trashDao, mediaDeletePermission,
            mockk<DownloadsAccessManager>(relaxed = true)
        )
        repository = TrashRepository(documentRepository, trashDao, historyDao, favorites, mediaDeletePermission)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `moveToTrash registra el documento en la papelera sin borrar el archivo`() = runTest {
        val dir  = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }

        val moved = repository.moveToTrash(file.absolutePath)

        assertTrue(moved)
        assertTrue(file.exists(), "el archivo real no debe tocarse al mover a la papelera")
        assertEquals(1, trashDao.getAll().size)
        assertEquals(file.absolutePath, trashDao.getAll().first().documentId)
    }

    @Test
    fun `moveToTrash limpia el historial de abierto recientemente`() = runTest {
        val dir  = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }
        historyDao.recordOpen(DocumentHistoryEntry(file.absolutePath, 1000L))

        repository.moveToTrash(file.absolutePath)

        assertTrue(historyDao.recentDocumentIds(10).isEmpty())
    }

    @Test
    fun `restoreFromTrash saca el documento de la papelera sin tocar el archivo`() = runTest {
        val dir  = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }
        repository.moveToTrash(file.absolutePath)

        val restored = repository.restoreFromTrash(file.absolutePath)

        assertTrue(restored)
        assertTrue(file.exists())
        assertTrue(trashDao.getAll().isEmpty())
    }

    @Test
    fun `deleteForever borra el archivo real y limpia la entrada de la papelera`() = runTest {
        val dir  = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }
        repository.moveToTrash(file.absolutePath)

        val deleted = repository.deleteForever(file.absolutePath)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Deleted)
        assertFalse(file.exists())
        assertTrue(trashDao.getAll().isEmpty())
    }

    @Test
    fun `deleteForever no limpia la entrada de la papelera si el borrado real fallo`() = runTest {
        // Bug real corregido (2026-08-30): antes se llamaba a trashDao.remove()
        // sin importar el resultado del borrado -- el archivo "resucitaba" en
        // Biblioteca/Recientes aunque no se hubiera podido borrar de verdad.
        val missing = File(filesDir, "no_existe.pdf")
        trashDao.insert(TrashEntry(missing.absolutePath, 1000L))

        val deleted = repository.deleteForever(missing.absolutePath)

        assertTrue(deleted is DocumentRepository.DeleteOutcome.Failed)
        assertEquals(1, trashDao.getAll().size, "la entrada debe seguir en la papelera para reintentar")
    }

    @Test
    fun `isTrashEntryExpired es falso antes del plazo de retencion y verdadero al cumplirse`() {
        val deletedAt = 1_000_000L
        val retentionMillis = TrashRepository.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000

        assertFalse(TrashRepository.isTrashEntryExpired(deletedAt, now = deletedAt + retentionMillis - 1))
        assertTrue(TrashRepository.isTrashEntryExpired(deletedAt, now = deletedAt + retentionMillis))
        assertTrue(TrashRepository.isTrashEntryExpired(deletedAt, now = deletedAt + retentionMillis + 1))
    }

    @Test
    fun `purgeExpiredTrash borra de verdad las entradas vencidas y conserva las recientes`() = runTest {
        val dir     = File(filesDir, "converted").apply { mkdirs() }
        val old     = File(dir, "viejo.pdf").apply { writeText("contenido") }
        val recent  = File(dir, "reciente.pdf").apply { writeText("contenido") }
        val now     = 100_000_000_000L
        val retentionMillis = TrashRepository.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
        trashDao.insert(TrashEntry(old.absolutePath, now - retentionMillis - 1))
        trashDao.insert(TrashEntry(recent.absolutePath, now - 1000L))

        repository.purgeExpiredTrash(now)

        assertFalse(old.exists(), "la entrada vencida debe borrarse de verdad")
        assertTrue(recent.exists(), "la entrada reciente no debe tocarse")
        assertEquals(listOf(recent.absolutePath), trashDao.getAll().map { it.documentId })
    }

    // `loadTrashedDocuments()` no está cubierto por un test directo: depende
    // de `DocumentRepository.loadAllDocumentsRaw()`, que llama a
    // `loadImagesFromMediaStore()` (ContentResolver real), mismo límite ya
    // documentado para `CompressPdfUseCase` -- requeriría mockear todo el
    // pipeline de MediaStore para un beneficio marginal, dado que la mecánica
    // de la papelera en sí (moveToTrash/restoreFromTrash/deleteForever/purga)
    // ya está cubierta arriba sin tocar ese pipeline.

    // ── fakes respaldados por un mapa en memoria (mismo patrón que
    // DocumentRepositoryTest) ─────────────────────────────────────────────

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
