package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import kotlinx.coroutines.CancellationException
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
        repository =
            DocumentRepository(
                context,
                mockk<FavoritesRepository>(relaxed = true),
                historyDao,
                trashDao,
                mockk<MediaDeletePermission>(relaxed = true),
                mockk<DownloadsAccessManager>(relaxed = true),
                mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true),
            )
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        filesDir.deleteRecursively()
    }

    @Test
    fun `deleteDocument borra un archivo generado por la app`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }

            val deleted = repository.deleteDocument(file.absolutePath)

            assertTrue(deleted is DocumentRepository.DeleteOutcome.Deleted)
            assertFalse(file.exists())
        }

    @Test
    fun `deleteDocument devuelve Failed si el archivo de la app no existe`() =
        runTest {
            val missing = File(filesDir, "no_existe.pdf")

            val deleted = repository.deleteDocument(missing.absolutePath)

            assertTrue(deleted is DocumentRepository.DeleteOutcome.Failed)
        }

    @Test
    fun `deleteDocument borra un documento de MediaStore via ContentResolver`() =
        runTest {
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
    fun `deleteDocument devuelve Failed si ContentResolver no pudo borrar`() =
        runTest {
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
    fun `deleteDocument devuelve Failed si ContentResolver lanza excepcion generica`() =
        runTest {
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
    fun `deleteDocument exitoso tambien borra el registro de historial`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }
            historyDao.recordOpen(DocumentHistoryEntry(file.absolutePath, 1000L))

            repository.deleteDocument(file.absolutePath)

            assertTrue(historyDao.recentDocumentIds(10).isEmpty())
        }

    // ── renameDocument (RF-VIS-06: extraído de Library/HomeViewModel para
    // reutilizarlo también desde el Visor) ────────────────────────────────

    @Test
    fun `renameDocument renombra un archivo real de la app y devuelve la nueva ruta`() =
        runTest {
            val favorites = mockk<FavoritesRepository>()
            coEvery { favorites.removeAlias(any()) } just Runs
            val identityMaintenance = mockk<com.docsmart.core.data.DocumentIdentityMaintenance>()
            coEvery { identityMaintenance.onIdChanged(any(), any()) } just Runs
            val repo =
                DocumentRepository(
                    context,
                    favorites,
                    historyDao,
                    trashDao,
                    mockk<MediaDeletePermission>(relaxed = true),
                    mockk<DownloadsAccessManager>(relaxed = true),
                    identityMaintenance,
                )
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "original.pdf").apply { writeText("contenido") }

            val newId = repo.renameDocument(file.absolutePath, "nuevo.pdf")

            assertEquals(File(dir, "nuevo.pdf").absolutePath, newId)
            assertTrue(File(dir, "nuevo.pdf").exists())
            assertFalse(file.exists())
            coVerify { favorites.removeAlias(file.absolutePath) }
            // Hallazgo #50 (revisión general 2026-09-16): un rename físico
            // también debe migrar favorito/anotaciones/marcadores/última página
            // (DocumentIdentityMaintenance) al nuevo id (la ruta absoluta del
            // archivo renombrado).
            coVerify { identityMaintenance.onIdChanged(file.absolutePath, File(dir, "nuevo.pdf").absolutePath) }
        }

    @Test
    fun `renameDocument de un documento de MediaStore usa alias sin tocar el archivo`() =
        runTest {
            val favorites = mockk<FavoritesRepository>()
            coEvery { favorites.saveAlias(any(), any()) } just Runs
            val repo =
                DocumentRepository(
                    context,
                    favorites,
                    historyDao,
                    trashDao,
                    mockk<MediaDeletePermission>(relaxed = true),
                    mockk<DownloadsAccessManager>(relaxed = true),
                    mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true),
                )
            val uriString = "content://media/external/downloads/12345"

            val newId = repo.renameDocument(uriString, "Nuevo nombre.pdf")

            assertEquals(uriString, newId, "un documento de MediaStore conserva su id -- solo cambia el alias")
            coVerify { favorites.saveAlias(uriString, "Nuevo nombre.pdf") }
        }

    @Test
    fun `renameDocument cae a alias si el archivo de la app no se pudo mover`() =
        runTest {
            val favorites = mockk<FavoritesRepository>()
            coEvery { favorites.saveAlias(any(), any()) } just Runs
            val repo =
                DocumentRepository(
                    context,
                    favorites,
                    historyDao,
                    trashDao,
                    mockk<MediaDeletePermission>(relaxed = true),
                    mockk<DownloadsAccessManager>(relaxed = true),
                    mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true),
                )
            val missing = File(filesDir, "no_existe.pdf") // File.renameTo() sobre un origen inexistente devuelve false

            val newId = repo.renameDocument(missing.absolutePath, "nuevo.pdf")

            assertEquals(missing.absolutePath, newId, "el id no cambia si el rename real falló")
            coVerify { favorites.saveAlias(missing.absolutePath, "nuevo.pdf") }
        }

    // ── mergeHistoryWithDocuments (RF-VIS/HOME: recientes = uso real) ─────────

    private fun doc(id: String) =
        DocumentUiModel(
            id = id,
            name = id,
            type = DocumentType.PDF,
            size = "1 KB",
            date = "24/08/2026",
            isFavorite = false,
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

    // ── helpers de construccion ───────────────────────────────────────────────

    private fun repoWith(
        favorites: FavoritesRepository,
        identity: com.docsmart.core.data.DocumentIdentityMaintenance =
            mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true),
    ) = DocumentRepository(
        context,
        favorites,
        historyDao,
        trashDao,
        mockk<MediaDeletePermission>(relaxed = true),
        mockk<DownloadsAccessManager>(relaxed = true),
        identity,
    )

    // ── renameDocument: casos borde (ronda 15) ────────────────────────────────

    @Test
    fun `renameDocument no pisa un archivo existente con el nombre elegido`() =
        runTest {
            // File.renameTo() reemplaza en silencio el destino en Linux/Android:
            // antes, renombrar "a.pdf" a "b.pdf" destruia el otro "b.pdf".
            val favorites = mockk<FavoritesRepository>(relaxed = true)
            val identity = mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true)
            val repo = repoWith(favorites, identity)
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val source = File(dir, "a.pdf").apply { writeText("contenido A") }
            val other = File(dir, "b.pdf").apply { writeText("contenido B") }

            val newId = repo.renameDocument(source.absolutePath, "b.pdf")

            assertEquals("contenido B", other.readText(), "el otro documento no debe sobrescribirse")
            assertTrue(source.exists(), "el origen sigue en su lugar")
            assertEquals(source.absolutePath, newId, "el id no cambia: solo se guardo un alias")
            coVerify { favorites.saveAlias(source.absolutePath, "b.pdf") }
            coVerify(exactly = 0) { identity.onIdChanged(any(), any()) }
        }

    @Test
    fun `renameDocument al mismo nombre no migra ids y limpia el alias`() =
        runTest {
            val favorites = mockk<FavoritesRepository>(relaxed = true)
            val identity = mockk<com.docsmart.core.data.DocumentIdentityMaintenance>(relaxed = true)
            val repo = repoWith(favorites, identity)
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "igual.pdf").apply { writeText("contenido") }

            val newId = repo.renameDocument(file.absolutePath, "igual.pdf")

            assertEquals(file.absolutePath, newId)
            assertTrue(file.exists())
            coVerify { favorites.removeAlias(file.absolutePath) }
            coVerify(exactly = 0) { identity.onIdChanged(any(), any()) }
            coVerify(exactly = 0) { favorites.saveAlias(any(), any()) }
        }

    @Test
    fun `renameDocument con nombre en blanco conserva el nombre real del archivo`() =
        runTest {
            val favorites = mockk<FavoritesRepository>(relaxed = true)
            val repo = repoWith(favorites)
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "real.pdf").apply { writeText("contenido") }

            val newId = repo.renameDocument(file.absolutePath, "   ")

            assertEquals(file.absolutePath, newId)
            assertTrue(file.exists())
        }

    @Test
    fun `renameDocument neutraliza path traversal y deja el archivo en su carpeta`() =
        runTest {
            val repo = repoWith(mockk<FavoritesRepository>(relaxed = true))
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "original.pdf").apply { writeText("contenido") }

            val newId = repo.renameDocument(file.absolutePath, "../../evil.pdf")

            assertEquals(File(dir, "evil.pdf").absolutePath, newId)
            assertTrue(File(dir, "evil.pdf").exists())
            assertFalse(File(filesDir, "evil.pdf").exists())
        }

    @Test
    fun `renameDocument devuelve la ruta nueva aunque falle la migracion de ids`() =
        runTest {
            // El archivo YA se movio: devolver el id viejo dejaba al llamador
            // apuntando a una ruta inexistente y guardaba un alias huerfano.
            val favorites = mockk<FavoritesRepository>(relaxed = true)
            val identity = mockk<com.docsmart.core.data.DocumentIdentityMaintenance>()
            coEvery { identity.onIdChanged(any(), any()) } throws IllegalStateException("room caido")
            val repo = repoWith(favorites, identity)
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "original.pdf").apply { writeText("contenido") }

            val newId = repo.renameDocument(file.absolutePath, "nuevo.pdf")

            assertEquals(File(dir, "nuevo.pdf").absolutePath, newId)
            assertTrue(File(dir, "nuevo.pdf").exists())
            coVerify(exactly = 0) { favorites.saveAlias(any(), any()) }
        }

    @Test
    fun `renameDocument propaga la cancelacion en vez de tragarla`() =
        runTest {
            val favorites = mockk<FavoritesRepository>()
            coEvery { favorites.saveAlias(any(), any()) } throws CancellationException("cancelado")
            val repo = repoWith(favorites)

            val result = runCatching { repo.renameDocument("content://media/external/downloads/1", "x.pdf") }

            assertTrue(result.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `renameDocument reintenta el alias en el catch si el primer guardado lanza`() =
        runTest {
            val favorites = mockk<FavoritesRepository>()
            // Primer intento lanza, el del catch (segundo) funciona.
            coEvery { favorites.saveAlias(any(), any()) } throws IllegalStateException("una vez") andThen Unit
            val repo = repoWith(favorites)
            val uriString = "content://media/external/downloads/1"

            val newId = repo.renameDocument(uriString, "x.pdf")

            assertEquals(uriString, newId)
            coVerify(exactly = 2) { favorites.saveAlias(uriString, "x.pdf") }
        }

    // ── deleteDocument sobre documentos SAF (carpeta vinculada) ───────────────

    private fun stubSafUri(uriString: String): Uri {
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.authority } returns "com.android.externalstorage.documents"
        every { context.contentResolver } returns mockk<ContentResolver>()
        return mockUri
    }

    @Test
    fun `deleteDocument de un documento SAF usa DocumentsContract y limpia el historial`() =
        runTest {
            val uriString = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fa.pdf"
            val mockUri = stubSafUri(uriString)
            mockkStatic(DocumentsContract::class)
            try {
                every { DocumentsContract.deleteDocument(any(), mockUri) } returns true
                historyDao.recordOpen(DocumentHistoryEntry(uriString, 1000L))

                val outcome = repository.deleteDocument(uriString)

                assertTrue(outcome is DocumentRepository.DeleteOutcome.Deleted)
                assertTrue(historyDao.allEntries().isEmpty())
            } finally {
                unmockkStatic(DocumentsContract::class)
            }
        }

    @Test
    fun `deleteDocument SAF devuelve Failed y conserva el historial si no se pudo borrar`() =
        runTest {
            val uriString = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fb.pdf"
            val mockUri = stubSafUri(uriString)
            mockkStatic(DocumentsContract::class)
            try {
                every { DocumentsContract.deleteDocument(any(), mockUri) } returns false
                historyDao.recordOpen(DocumentHistoryEntry(uriString, 1000L))

                val outcome = repository.deleteDocument(uriString)

                assertTrue(outcome is DocumentRepository.DeleteOutcome.Failed)
                assertEquals(1, historyDao.allEntries().size)
            } finally {
                unmockkStatic(DocumentsContract::class)
            }
        }

    @Test
    fun `deleteDocument SAF devuelve Failed si DocumentsContract lanza una excepcion`() =
        runTest {
            val uriString = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fc.pdf"
            val mockUri = stubSafUri(uriString)
            mockkStatic(DocumentsContract::class)
            try {
                every { DocumentsContract.deleteDocument(any(), mockUri) } throws SecurityException("sin permiso")

                val outcome = repository.deleteDocument(uriString)

                assertTrue(outcome is DocumentRepository.DeleteOutcome.Failed)
            } finally {
                unmockkStatic(DocumentsContract::class)
            }
        }

    @Test
    fun `deleteDocument SAF propaga la cancelacion`() =
        runTest {
            val uriString = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fd.pdf"
            val mockUri = stubSafUri(uriString)
            mockkStatic(DocumentsContract::class)
            try {
                every { DocumentsContract.deleteDocument(any(), mockUri) } throws CancellationException("cancelado")

                val result = runCatching { repository.deleteDocument(uriString) }

                assertTrue(result.exceptionOrNull() is CancellationException)
            } finally {
                unmockkStatic(DocumentsContract::class)
            }
        }

    @Test
    fun `deleteDocument de MediaStore propaga la cancelacion en vez de devolver Failed`() =
        runTest {
            val uriString = "content://media/external/images/7"
            val mockUri = mockk<Uri>()
            every { Uri.parse(uriString) } returns mockUri
            every { mockUri.authority } returns "media"
            val resolver = mockk<ContentResolver>()
            every { resolver.delete(mockUri, null, null) } throws CancellationException("cancelado")
            every { context.contentResolver } returns resolver

            val result = runCatching { repository.deleteDocument(uriString) }

            assertTrue(result.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `deleteDocument fallido de un archivo no toca el historial`() =
        runTest {
            val missing = File(filesDir, "fantasma.pdf")
            historyDao.recordOpen(DocumentHistoryEntry(missing.absolutePath, 1000L))

            val outcome = repository.deleteDocument(missing.absolutePath)

            assertTrue(outcome is DocumentRepository.DeleteOutcome.Failed)
            assertEquals(1, historyDao.allEntries().size)
        }

    // ── dedupeAndSortByRecency (orden real por timestamp, ronda 15) ───────────

    private fun dated(
        id: String,
        millis: Long,
        date: String = "01/01/2026",
        name: String = id,
    ) = DatedDocument(doc(id).copy(date = date, name = name), millis)

    @Test
    fun `dedupeAndSortByRecency ordena por timestamp real y no por el texto de la fecha`() {
        // Como texto, "15/01/2026" > "14/09/2026" (compara el dia primero):
        // el orden viejo ponia enero ANTES que septiembre.
        val january = dated("enero", millis = 1_768_435_200_000L, date = "15/01/2026")
        val september = dated("septiembre", millis = 1_789_344_000_000L, date = "14/09/2026")

        val result = dedupeAndSortByRecency(listOf(january, september))

        assertEquals(listOf("septiembre", "enero"), result.map { it.id })
    }

    @Test
    fun `dedupeAndSortByRecency conserva la primera aparicion de un id repetido`() {
        val fromMediaStore = dated("mismo", millis = 100L, name = "MediaStore")
        val fromHistory = dated("mismo", millis = 999L, name = "Historial")

        val result = dedupeAndSortByRecency(listOf(fromMediaStore, fromHistory))

        assertEquals(1, result.size)
        assertEquals("MediaStore", result.single().name)
    }

    @Test
    fun `dedupeAndSortByRecency es estable con timestamps iguales`() {
        val result = dedupeAndSortByRecency(listOf(dated("a", 5L), dated("b", 5L), dated("c", 5L)))

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }

    @Test
    fun `dedupeAndSortByRecency con lista vacia devuelve vacia`() {
        assertTrue(dedupeAndSortByRecency(emptyList()).isEmpty())
    }

    @Test
    fun `mergeHistoryWithDocuments con limite cero no devuelve nada`() {
        val result = repository.mergeHistoryWithDocuments(listOf(doc("a")), listOf("a"), limit = 0)

        assertTrue(result.isEmpty())
    }

    // `loadAllDocuments()`/`loadRecentlyOpened()`/`loadDocumentsFrom*()` NO se
    // cubren con tests directos: `loadImagesFromMediaStore()` toca
    // `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, cuyo inicializador
    // estatico llama a metodos del framework que en el android.jar de tests
    // (sin Robolectric) lanzan -- ExceptionInInitializerError, un Error que ni
    // siquiera atrapa el `catch (Exception)`. Lo puro (orden/dedupe/merge) ya
    // esta extraido y cubierto arriba.

    // ── fake de DocumentHistoryDao respaldado por un mapa en memoria ──────────

    private class FakeDocumentHistoryDao : DocumentHistoryDao {
        private val store = mutableMapOf<String, Long>()

        override suspend fun recordOpen(entry: DocumentHistoryEntry) {
            store[entry.documentId] = entry.lastOpenedAt
        }

        override suspend fun recentDocumentIds(limit: Int): List<String> =
            store.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(limit)

        override suspend fun allEntries(): List<DocumentHistoryEntry> =
            store.entries.sortedByDescending { it.value }.map { DocumentHistoryEntry(it.key, it.value) }

        override suspend fun remove(documentId: String) {
            store.remove(documentId)
        }

        override suspend fun updateDocumentId(
            oldDocumentId: String,
            newDocumentId: String,
        ) {
            store.remove(oldDocumentId)?.let { store[newDocumentId] = it }
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
