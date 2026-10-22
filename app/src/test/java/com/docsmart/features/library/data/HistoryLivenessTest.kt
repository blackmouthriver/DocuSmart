package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.database.Cursor
import android.net.Uri
import com.docsmart.core.data.db.DocumentHistoryDao
import io.mockk.coVerify
import io.mockk.every
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
import java.io.FileNotFoundException

/**
 * Backlog #25 (1.1.6): una fila del historial cuyo documento ya no existe se
 * limpia, pero ante cualquier duda (Drive sin red, proveedor ausente, permiso
 * persistente que aun existe) no se borra nada.
 */
class HistoryLivenessTest {
    private val id = "content://com.android.providers.media.documents/document/document%3A1000159683"
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var dao: DocumentHistoryDao
    private lateinit var uri: Uri

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        uri = mockk()
        every { Uri.parse(id) } returns uri
        resolver = mockk(relaxed = true)
        context = mockk()
        every { context.contentResolver } returns resolver
        every { resolver.persistedUriPermissions } returns emptyList()
        dao = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun grant(
        read: Boolean = true,
        write: Boolean = false,
    ): UriPermission {
        val permissionUri = mockk<Uri>()
        every { permissionUri.toString() } returns id
        val permission = mockk<UriPermission>()
        every { permission.uri } returns permissionUri
        every { permission.isReadPermission } returns read
        every { permission.isWritePermission } returns write
        every { resolver.persistedUriPermissions } returns listOf(permission)
        return permission
    }

    private fun queryReturns(cursor: Cursor?) {
        every { resolver.query(uri, any(), null, null, null) } returns cursor
    }

    private fun cursorWithRow(hasRow: Boolean): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns hasRow
        return cursor
    }

    // ── classify (logica pura) ───────────────────────────────────────────────

    @Test
    fun `un documento con fila esta vivo`() {
        assertEquals(HistoryProbe.ALIVE, HistoryLiveness.classify(HistoryQueryOutcome.Row, hasPersistedGrant = true))
    }

    @Test
    fun `FileNotFoundException es GONE con o sin permiso`() {
        val outcome = HistoryQueryOutcome.Failed(FileNotFoundException())
        assertEquals(HistoryProbe.GONE, HistoryLiveness.classify(outcome, hasPersistedGrant = true))
        assertEquals(HistoryProbe.GONE, HistoryLiveness.classify(outcome, hasPersistedGrant = false))
    }

    @Test
    fun `SecurityException sin permiso persistente es GONE y con permiso persistente es UNKNOWN`() {
        val outcome = HistoryQueryOutcome.Failed(SecurityException())
        assertEquals(HistoryProbe.GONE, HistoryLiveness.classify(outcome, hasPersistedGrant = false))
        assertEquals(HistoryProbe.UNKNOWN, HistoryLiveness.classify(outcome, hasPersistedGrant = true))
    }

    @Test
    fun `cursor vacio es GONE solo si DocuSmart tiene el permiso persistente`() {
        val empty = HistoryQueryOutcome.EmptyCursor
        assertEquals(HistoryProbe.GONE, HistoryLiveness.classify(empty, hasPersistedGrant = true))
        assertEquals(HistoryProbe.UNKNOWN, HistoryLiveness.classify(empty, hasPersistedGrant = false))
    }

    @Test
    fun `cursor nulo y errores desconocidos son UNKNOWN`() {
        val noProvider = HistoryQueryOutcome.NullCursor
        assertEquals(HistoryProbe.UNKNOWN, HistoryLiveness.classify(noProvider, hasPersistedGrant = true))
        val transient = HistoryQueryOutcome.Failed(IllegalStateException("sin conexion"))
        assertEquals(HistoryProbe.UNKNOWN, HistoryLiveness.classify(transient, hasPersistedGrant = false))
    }

    // ── pruneIfGone ──────────────────────────────────────────────────────────

    @Test
    fun `una ruta de archivo nunca se toca`() =
        runTest {
            assertFalse(HistoryLiveness.pruneIfGone(context, dao, "/storage/emulated/0/Download/x.pdf"))
            coVerify(exactly = 0) { dao.remove(any()) }
        }

    @Test
    fun `un documento borrado cuyo permiso Android ya revoco se quita del historial`() =
        runTest {
            every { resolver.query(uri, any(), null, null, null) } throws SecurityException("revocado")

            assertTrue(HistoryLiveness.pruneIfGone(context, dao, id))

            coVerify { dao.remove(id) }
            verify(exactly = 0) { resolver.releasePersistableUriPermission(any(), any()) }
        }

    @Test
    fun `un documento que el proveedor dice que no existe libera el permiso y se quita`() =
        runTest {
            grant(read = true, write = true)
            every { resolver.query(uri, any(), null, null, null) } throws FileNotFoundException()

            assertTrue(HistoryLiveness.pruneIfGone(context, dao, id))

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            verify { resolver.releasePersistableUriPermission(uri, flags) }
            coVerify { dao.remove(id) }
        }

    @Test
    fun `un cursor vacio con permiso persistente se quita`() =
        runTest {
            grant()
            queryReturns(cursorWithRow(hasRow = false))

            assertTrue(HistoryLiveness.pruneIfGone(context, dao, id))

            verify { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coVerify { dao.remove(id) }
        }

    @Test
    fun `tolera que liberar el permiso falle y igual quita la fila`() =
        runTest {
            grant()
            every { resolver.query(uri, any(), null, null, null) } throws FileNotFoundException()
            every { resolver.releasePersistableUriPermission(any(), any()) } throws SecurityException("ya no esta")

            assertTrue(HistoryLiveness.pruneIfGone(context, dao, id))

            coVerify { dao.remove(id) }
        }

    @Test
    fun `un documento que existe no se toca`() =
        runTest {
            grant()
            queryReturns(cursorWithRow(hasRow = true))

            assertFalse(HistoryLiveness.pruneIfGone(context, dao, id))

            coVerify(exactly = 0) { dao.remove(any()) }
            verify(exactly = 0) { resolver.releasePersistableUriPermission(any(), any()) }
        }

    @Test
    fun `un SecurityException con permiso persistente no se quita`() =
        runTest {
            grant()
            every { resolver.query(uri, any(), null, null, null) } throws SecurityException("transitorio")

            assertFalse(HistoryLiveness.pruneIfGone(context, dao, id))

            coVerify(exactly = 0) { dao.remove(any()) }
        }

    @Test
    fun `un proveedor ausente o un error de red no se quita`() =
        runTest {
            queryReturns(null)
            assertFalse(HistoryLiveness.pruneIfGone(context, dao, id))

            every { resolver.query(uri, any(), null, null, null) } throws IllegalStateException("sin conexion")
            assertFalse(HistoryLiveness.pruneIfGone(context, dao, id))

            coVerify(exactly = 0) { dao.remove(any()) }
        }
}
