package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocuSmartDatabase
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
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
        // Hallazgo real de la auditoría de la capa de persistencia (Alta):
        // onIdChanged()/onPermanentlyDeleted() ahora envuelven su cuerpo en
        // `database.withTransaction {}` -- sobre una base mockeada, la
        // implementación real de esa función de extensión dependería del
        // transactionExecutor real de Room y colgaría la corutina. Se
        // mockea la función de extensión en sí (mismo criterio que
        // DocumentIdentityMaintenanceTest) para que ejecute el bloque
        // recibido directo, sin pasar por la maquinaria real de Room.
        mockkStatic("androidx.room.RoomDatabaseKt")
        val database = mockk<DocuSmartDatabase>()
        // Ver el comentario equivalente en DocumentIdentityMaintenanceTest:
        // el bloque es el SEGUNDO argumento de la llamada estática
        // subyacente (el primero es el receptor `database`).
        coEvery { database.withTransaction<Any?>(any()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        // Instancia real (no mock): así los coVerify sobre `favorites` de
        // abajo siguen viendo las mismas llamadas que antes de consolidar
        // el borrado/la migración en DocumentIdentityMaintenance -- solo se
        // mockean sus otras dependencias, que estos tests no verifican.
        val identityMaintenance =
            com.docsmart.core.data.DocumentIdentityMaintenance(
                favorites,
                mockk<com.docsmart.core.data.db.AnnotationDao>(relaxed = true),
                mockk<com.docsmart.core.data.db.PageBookmarkDao>(relaxed = true),
                mockk<com.docsmart.core.data.db.LastViewedPageDao>(relaxed = true),
                mockk<com.docsmart.core.data.db.NoteDao>(relaxed = true),
                mockk<com.docsmart.core.data.db.AgendaEventDao>(relaxed = true),
                historyDao,
                database,
            )
        documentRepository =
            DocumentRepository(
                context,
                favorites,
                historyDao,
                trashDao,
                mediaDeletePermission,
                mockk<DownloadsAccessManager>(relaxed = true),
                identityMaintenance,
            )
        repository =
            TrashRepository(
                documentRepository,
                trashDao,
                historyDao,
                mediaDeletePermission,
                identityMaintenance,
                context,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        filesDir.deleteRecursively()
    }

    @Test
    fun `moveToTrash registra el documento en la papelera sin borrar el archivo`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }

            val moved = repository.moveToTrash(file.absolutePath)

            assertTrue(moved)
            assertTrue(file.exists(), "el archivo real no debe tocarse al mover a la papelera")
            assertEquals(1, trashDao.getAll().size)
            assertEquals(file.absolutePath, trashDao.getAll().first().documentId)
        }

    @Test
    fun `moveToTrash limpia el historial de abierto recientemente`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }
            historyDao.recordOpen(DocumentHistoryEntry(file.absolutePath, 1000L))

            repository.moveToTrash(file.absolutePath)

            assertTrue(historyDao.recentDocumentIds(10).isEmpty())
        }

    @Test
    fun `restoreFromTrash saca el documento de la papelera sin tocar el archivo`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }
            repository.moveToTrash(file.absolutePath)

            val restored = repository.restoreFromTrash(file.absolutePath)

            assertTrue(restored)
            assertTrue(file.exists())
            assertTrue(trashDao.getAll().isEmpty())
        }

    @Test
    fun `deleteForever borra el archivo real y limpia la entrada de la papelera`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
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
    fun `deleteForever no limpia la entrada de la papelera si el borrado real fallo`() =
        runTest {
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
    fun `purgeExpiredTrash borra de verdad las entradas vencidas y conserva las recientes`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val old = File(dir, "viejo.pdf").apply { writeText("contenido") }
            val recent = File(dir, "reciente.pdf").apply { writeText("contenido") }
            val now = 100_000_000_000L
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
    fun `purgeExpiredTrash no borra en la primera deteccion, resiste un salto instantaneo del reloj`() =
        runTest {
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

    // ── ronda 15: moveToTrash/restoreFromTrash/deleteAllForever/purga ─────────

    @Test
    fun `moveToTrash devuelve false y no oculta nada si falla el insert`() =
        runTest {
            trashDao.failInsert = true

            val moved = repository.moveToTrash("/ruta/cualquiera.pdf")

            assertFalse(moved)
            assertTrue(trashDao.getAll().isEmpty())
        }

    @Test
    fun `moveToTrash sigue siendo exitoso si solo falla la limpieza del historial`() =
        runTest {
            // Antes: el fallo de historyDao.remove() devolvia false con el
            // documento YA en trash_entries (oculto de Biblioteca), y la UI
            // mostraba "no se pudo eliminar" con el archivo ya desaparecido.
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }
            historyDao.failRemove = true

            val moved = repository.moveToTrash(file.absolutePath)

            assertTrue(moved)
            assertEquals(listOf(file.absolutePath), trashDao.getAll().map { it.documentId })
        }

    @Test
    fun `restoreFromTrash de un archivo que ya no existe devuelve false y limpia la entrada`() =
        runTest {
            val missing = File(filesDir, "borrado_por_fuera.pdf")
            trashDao.insert(TrashEntry(missing.absolutePath, 1000L))

            val restored = repository.restoreFromTrash(missing.absolutePath)

            assertFalse(restored)
            assertTrue(trashDao.getAll().isEmpty(), "no debe quedar una entrada huerfana apuntando a la nada")
        }

    @Test
    fun `restoreFromTrash devuelve false si Room falla al quitar la entrada`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val file = File(dir, "documento.pdf").apply { writeText("contenido") }
            trashDao.insert(TrashEntry(file.absolutePath, 1000L))
            trashDao.failRemoveFor = file.absolutePath

            val restored = repository.restoreFromTrash(file.absolutePath)

            assertFalse(restored)
            assertTrue(file.exists())
        }

    @Test
    fun `restoreFromTrash de un content Uri existente cierra el cursor y restaura`() =
        runTest {
            val uriString = "content://media/external/images/media/55"
            val mockUri = mockk<Uri>()
            mockkStatic(Uri::class)
            try {
                every { Uri.parse(uriString) } returns mockUri
                val cursor = mockk<Cursor>(relaxed = true)
                every { cursor.moveToFirst() } returns true
                val resolver = mockk<ContentResolver>()
                every { resolver.query(mockUri, null, null, null, null) } returns cursor
                every { context.contentResolver } returns resolver
                trashDao.insert(TrashEntry(uriString, 1000L))

                val restored = repository.restoreFromTrash(uriString)

                assertTrue(restored)
                assertTrue(trashDao.getAll().isEmpty())
                verify { cursor.close() }
            } finally {
                unmockkStatic(Uri::class)
            }
        }

    @Test
    fun `restoreFromTrash de un content Uri sin filas devuelve false`() =
        runTest {
            val uriString = "content://media/external/images/media/56"
            val mockUri = mockk<Uri>()
            mockkStatic(Uri::class)
            try {
                every { Uri.parse(uriString) } returns mockUri
                val cursor = mockk<Cursor>(relaxed = true)
                every { cursor.moveToFirst() } returns false
                val resolver = mockk<ContentResolver>()
                every { resolver.query(mockUri, null, null, null, null) } returns cursor
                every { context.contentResolver } returns resolver
                trashDao.insert(TrashEntry(uriString, 1000L))

                val restored = repository.restoreFromTrash(uriString)

                assertFalse(restored)
                verify { cursor.close() }
            } finally {
                unmockkStatic(Uri::class)
            }
        }

    @Test
    fun `restoreFromTrash de un content Uri cuyo proveedor lanza devuelve false sin crashear`() =
        runTest {
            val uriString = "content://media/external/images/media/57"
            val mockUri = mockk<Uri>()
            mockkStatic(Uri::class)
            try {
                every { Uri.parse(uriString) } returns mockUri
                val resolver = mockk<ContentResolver>()
                every { resolver.query(mockUri, null, null, null, null) } throws SecurityException("sin permiso")
                every { context.contentResolver } returns resolver
                trashDao.insert(TrashEntry(uriString, 1000L))

                val restored = repository.restoreFromTrash(uriString)

                assertFalse(restored)
            } finally {
                unmockkStatic(Uri::class)
            }
        }

    @Test
    fun `deleteAllForever borra los archivos de la app y limpia entradas, alias y favoritos`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val first = File(dir, "uno.pdf").apply { writeText("1") }
            val second = File(dir, "dos.pdf").apply { writeText("2") }
            trashDao.insert(TrashEntry(first.absolutePath, 1000L))
            trashDao.insert(TrashEntry(second.absolutePath, 2000L))

            val outcome = repository.deleteAllForever(listOf(first.absolutePath, second.absolutePath))

            assertTrue(outcome is TrashRepository.BulkDeleteOutcome.Done)
            assertFalse(first.exists())
            assertFalse(second.exists())
            assertTrue(trashDao.getAll().isEmpty())
            coVerify { favorites.removeFavorite(first.absolutePath) }
            coVerify { favorites.removeFavorite(second.absolutePath) }
        }

    @Test
    fun `deleteAllForever conserva en la papelera lo que no se pudo borrar`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val deletable = File(dir, "borrable.pdf").apply { writeText("1") }
            val missing = File(dir, "fantasma.pdf")
            trashDao.insert(TrashEntry(deletable.absolutePath, 1000L))
            trashDao.insert(TrashEntry(missing.absolutePath, 2000L))

            repository.deleteAllForever(listOf(deletable.absolutePath, missing.absolutePath))

            assertEquals(listOf(missing.absolutePath), trashDao.getAll().map { it.documentId })
            coVerify(exactly = 0) { favorites.removeFavorite(missing.absolutePath) }
        }

    @Test
    fun `deleteAllForever con lista vacia no hace nada y devuelve Done`() =
        runTest {
            val outcome = repository.deleteAllForever(emptyList())

            assertTrue(outcome is TrashRepository.BulkDeleteOutcome.Done)
        }

    @Test
    fun `deleteAllForever borra un documento SAF individualmente via DocumentsContract`() =
        runTest {
            // Antes todo content:// iba al pedido masivo de MediaStore (que
            // rechaza URIs SAF y tumbaba el lote entero en API 30+). Nota: en
            // JVM Build.VERSION.SDK_INT == 0, asi que la rama del pedido masivo
            // en si no es alcanzable; esto cubre el borrado individual SAF.
            val safId = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fx.pdf"
            val safUri = mockk<Uri>()
            every { safUri.authority } returns "com.android.externalstorage.documents"
            mockkStatic(Uri::class)
            mockkStatic(DocumentsContract::class)
            try {
                every { Uri.parse(safId) } returns safUri
                every { context.contentResolver } returns mockk<ContentResolver>()
                every { DocumentsContract.deleteDocument(any(), safUri) } returns true
                trashDao.insert(TrashEntry(safId, 1000L))

                val outcome = repository.deleteAllForever(listOf(safId))

                assertTrue(outcome is TrashRepository.BulkDeleteOutcome.Done)
                assertTrue(trashDao.getAll().isEmpty())
                coVerify { favorites.removeFavorite(safId) }
            } finally {
                unmockkStatic(DocumentsContract::class)
                unmockkStatic(Uri::class)
            }
        }

    @Test
    fun `finalizeDeleteForever en lote limpia cada entrada y su identidad`() =
        runTest {
            trashDao.insert(TrashEntry("/a.pdf", 1L))
            trashDao.insert(TrashEntry("/b.pdf", 2L))
            trashDao.insert(TrashEntry("/c.pdf", 3L))

            repository.finalizeDeleteForever(listOf("/a.pdf", "/b.pdf"))

            assertEquals(listOf("/c.pdf"), trashDao.getAll().map { it.documentId })
            coVerify { favorites.removeFavorite("/a.pdf") }
            coVerify { favorites.removeFavorite("/b.pdf") }
            coVerify(exactly = 0) { favorites.removeFavorite("/c.pdf") }
        }

    @Test
    fun `purgeExpiredTrash se re-ancla tras un reinicio del dispositivo y termina purgando`() =
        runTest {
            // elapsedRealtime se reinicia al arrancar: si el candidato se anoto
            // con un uptime enorme, tras reiniciar nowElapsed < firstSeen y la
            // diferencia negativa nunca alcanzaba el minimo -- la entrada
            // vencida no se purgaba jamas (hasta superar el uptime viejo).
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val old = File(dir, "viejo.pdf").apply { writeText("contenido") }
            val now = 100_000_000_000L
            val retentionMillis = TrashRepository.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
            trashDao.insert(TrashEntry(old.absolutePath, now - retentionMillis - 1))

            // Primera deteccion con un uptime de ~578 dias.
            repository.purgeExpiredTrash(now, nowElapsed = 50_000_000_000L)
            // Reinicio: el reloj real vuelve a valores chicos -- re-ancla.
            repository.purgeExpiredTrash(now, nowElapsed = 1_000L)
            assertTrue(old.exists(), "el re-anclaje no purga: falta tiempo real desde el nuevo arranque")

            repository.purgeExpiredTrash(now, nowElapsed = 1_000L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE + 1)

            assertFalse(old.exists())
            assertTrue(trashDao.getAll().isEmpty())
        }

    @Test
    fun `purgeExpiredTrash sigue con las demas entradas si una falla`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val broken = File(dir, "roto.pdf").apply { writeText("1") }
            val healthy = File(dir, "sano.pdf").apply { writeText("2") }
            val now = 100_000_000_000L
            val retentionMillis = TrashRepository.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
            val expiredAt = now - retentionMillis - 1
            // `broken` va primero (el fake conserva el orden de insercion).
            trashDao.insert(TrashEntry(broken.absolutePath, expiredAt))
            trashDao.insert(TrashEntry(healthy.absolutePath, expiredAt))
            trashDao.failRemoveFor = broken.absolutePath

            repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L)
            repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE + 1)

            assertFalse(healthy.exists(), "la entrada sana debe purgarse aunque la anterior haya fallado")
            assertEquals(listOf(broken.absolutePath), trashDao.getAll().map { it.documentId })
        }

    @Test
    fun `purgeExpiredTrash no purga una entrada dentro del plazo aunque pase mucho tiempo real`() =
        runTest {
            val dir = File(filesDir, "converted").apply { mkdirs() }
            val fresh = File(dir, "fresco.pdf").apply { writeText("contenido") }
            val now = 100_000_000_000L
            trashDao.insert(TrashEntry(fresh.absolutePath, now - 1000L))

            repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L)
            repository.purgeExpiredTrash(now, nowElapsed = 1_000_000L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE * 3)

            assertTrue(fresh.exists())
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
            store.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(limit)

        override suspend fun allEntries(): List<DocumentHistoryEntry> =
            store.entries.sortedByDescending { it.value }.map { DocumentHistoryEntry(it.key, it.value) }

        var failRemove = false

        override suspend fun remove(documentId: String) {
            if (failRemove) throw IllegalStateException("fallo simulado de Room")
            store.remove(documentId)
        }

        override suspend fun updateDocumentId(
            oldDocumentId: String,
            newDocumentId: String,
        ) {
            store.remove(oldDocumentId)?.let { store[newDocumentId] = it }
        }
    }

    private class FakeTrashDao : TrashDao {
        private val store = mutableMapOf<String, TrashEntry>()
        var failInsert = false
        var failRemoveFor: String? = null

        override suspend fun insert(entry: TrashEntry) {
            if (failInsert) throw IllegalStateException("fallo simulado de Room")
            store[entry.documentId] = entry
        }

        override suspend fun remove(documentId: String) {
            if (documentId == failRemoveFor) throw IllegalStateException("fallo simulado de Room")
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
