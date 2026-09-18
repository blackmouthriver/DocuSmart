package com.docsmart.features.library.data

import android.content.Context
import android.content.SharedPreferences
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
        // Ver P1 (auditoría 2026-09-17/18, décima ronda): purgeExpiredTrash()
        // ahora persiste candidatos de vencimiento en SharedPreferences.
        every { context.getSharedPreferences(any(), any()) } returns fakeSharedPreferences()
        historyDao = FakeDocumentHistoryDao()
        trashDao = FakeTrashDao()
        favorites = mockk()
        coEvery { favorites.removeAlias(any()) } just Runs
        // Hallazgo #50 (revisión general 2026-09-16): borrar definitivamente
        // (individual, en lote o por vencimiento) también debe limpiar el
        // flag de favorito, no solo el alias.
        coEvery { favorites.removeFavorite(any()) } just Runs
        val mediaDeletePermission = mockk<MediaDeletePermission>(relaxed = true)
        // Instancia real (no mock): así los coVerify sobre `favorites` de
        // abajo siguen viendo las mismas llamadas que antes de consolidar
        // el borrado/la migración en DocumentIdentityMaintenance -- solo se
        // mockean sus otras dependencias, que estos tests no verifican.
        val identityMaintenance = com.docsmart.core.data.DocumentIdentityMaintenance(
            favorites,
            mockk<com.docsmart.core.data.db.AnnotationDao>(relaxed = true),
            mockk<com.docsmart.core.data.db.PageBookmarkDao>(relaxed = true),
            mockk<com.docsmart.core.data.db.LastViewedPageDao>(relaxed = true),
            mockk<com.docsmart.core.data.db.NoteDao>(relaxed = true),
            mockk<com.docsmart.core.data.db.AgendaEventDao>(relaxed = true),
            historyDao
        )
        documentRepository = DocumentRepository(
            context, favorites, historyDao, trashDao, mediaDeletePermission,
            mockk<DownloadsAccessManager>(relaxed = true), identityMaintenance
        )
        repository = TrashRepository(
            documentRepository, trashDao, historyDao, mediaDeletePermission, identityMaintenance, context
        )
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
        // Hallazgo #50 (revisión general 2026-09-16): un borrado definitivo
        // también debe limpiar el flag de favorito, no solo el alias.
        coVerify { favorites.removeFavorite(file.absolutePath) }
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

        // Hallazgo P1 (auditoría 2026-09-17/18, décima ronda): la primera
        // vez que una entrada se ve vencida solo queda anotada como
        // candidata, todavía no se borra -- hace falta una segunda purga
        // con suficiente tiempo REAL (elapsedRealtime) transcurrido. Se
        // simulan las 2 purgas con `nowElapsed` explícito para no depender
        // de mockear el reloj real del sistema.
        repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L)
        assertTrue(old.exists(), "no debe borrar en la primera detección")

        repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE + 1)

        assertFalse(old.exists(), "la entrada vencida debe borrarse de verdad tras confirmar con tiempo real")
        assertTrue(recent.exists(), "la entrada reciente no debe tocarse")
        assertEquals(listOf(recent.absolutePath), trashDao.getAll().map { it.documentId })
        // Hallazgo #50 (revisión general 2026-09-16): purgeExpiredTrash no
        // limpiaba ni el alias ni el favorito de las entradas vencidas.
        coVerify { favorites.removeAlias(old.absolutePath) }
        coVerify { favorites.removeFavorite(old.absolutePath) }
        coVerify(exactly = 0) { favorites.removeFavorite(recent.absolutePath) }
    }

    @Test
    fun `purgeExpiredTrash no borra en la primera deteccion, resiste un salto instantaneo del reloj`() = runTest {
        // Escenario real del hallazgo P1: el usuario adelanta la fecha del
        // sistema 31+ días y abre Papelera -- sin este fix, esto borraba
        // todo de una sola vez, sin ningún aviso.
        val dir = File(filesDir, "converted").apply { mkdirs() }
        val old = File(dir, "viejo.pdf").apply { writeText("contenido") }
        val now = 100_000_000_000L
        val retentionMillis = TrashRepository.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
        trashDao.insert(TrashEntry(old.absolutePath, now - retentionMillis - 1))

        repository.purgeExpiredTrash(now, nowElapsed = 5_000L)
        // Reabrir Papelera de inmediato (mismo instante real) no alcanza.
        repository.purgeExpiredTrash(now, nowElapsed = 5_500L)

        assertTrue(old.exists(), "un salto instantáneo del reloj no debe alcanzar para purgar")
        assertEquals(1, trashDao.getAll().size)
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

        override suspend fun allEntries(): List<DocumentHistoryEntry> =
            store.entries.sortedByDescending { it.value }.map { DocumentHistoryEntry(it.key, it.value) }

        override suspend fun remove(documentId: String) {
            store.remove(documentId)
        }

        override suspend fun updateDocumentId(oldDocumentId: String, newDocumentId: String) {
            store.remove(oldDocumentId)?.let { store[newDocumentId] = it }
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

    // Respaldado por un mapa real -- mismo patrón ya usado en
    // DailyLimitManagerTest para SharedPreferences con getLong/putLong.
    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Long>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putLong(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Long>()
            editor
        }
        every { editor.remove(any()) } answers {
            store.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getLong(any(), any()) } answers {
            store[firstArg<String>()] ?: secondArg()
        }
        return prefs
    }
}
