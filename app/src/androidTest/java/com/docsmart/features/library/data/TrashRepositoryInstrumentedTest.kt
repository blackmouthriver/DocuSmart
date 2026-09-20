package com.docsmart.features.library.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import com.docsmart.core.data.DocumentIdentityMaintenance
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.TrashDao
import com.docsmart.core.data.db.TrashEntry
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.converter.domain.usecase.IsolatedContext
import com.docsmart.features.converter.domain.usecase.newIsolatedContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** TrashRepository con Room y DocumentRepository simulados; preferencias de purga aisladas por prueba. */
class TrashRepositoryInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var repo: TrashRepository

    private val documents = mockk<DocumentRepository>(relaxed = true)
    private val trash = mockk<TrashDao>(relaxed = true)
    private val history = mockk<DocumentHistoryDao>(relaxed = true)
    private val deletePermission = mockk<MediaDeletePermission>(relaxed = true)
    private val identity = mockk<DocumentIdentityMaintenance>(relaxed = true)
    private val resolver = mockk<ContentResolver>(relaxed = true)

    private val day = 24 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        ctx = newIsolatedContext("trashrepo")
        ctx.resolver = resolver
        coEvery { trash.getAll() } returns emptyList()
        repo = TrashRepository(documents, trash, history, deletePermission, identity, ctx)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun candidatePrefs() = ctx.getSharedPreferences("docusmart_trash_purge_candidates", Context.MODE_PRIVATE)

    private fun doc(id: String) = DocumentUiModel(id, "doc-$id", DocumentType.PDF, "1 KB", "01/01/2026")

    private fun pendingSender(): IntentSender {
        val intent = Intent("com.docsmart.r20.test").setPackage(ctx.packageName)
        return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE).intentSender
    }

    // ── moveToTrash / restore ─────────────────────────────────────────────────

    @Test
    fun moverALaPapeleraRegistraLaEntradaLimpiaElHistorialYElCandidatoDePurga() {
        candidatePrefs().edit().putLong("candidate_doc1", 5L).commit()

        val ok = runBlocking { repo.moveToTrash("doc1") }

        assertTrue(ok)
        coVerify { trash.insert(match { it.documentId == "doc1" }) }
        coVerify { history.remove("doc1") }
        assertFalse(candidatePrefs().contains("candidate_doc1"))
    }

    @Test
    fun moverALaPapeleraTolerantesAFallosDelHistorialPeroNoDeLaEntrada() {
        coEvery { history.remove(any()) } throws IllegalStateException("room")
        assertTrue(runBlocking { repo.moveToTrash("doc1") })

        coEvery { trash.insert(any()) } throws IllegalStateException("room")
        assertFalse(runBlocking { repo.moveToTrash("doc2") })
    }

    @Test
    fun restaurarUnArchivoExistenteLoSacaDeLaPapelera() {
        val file = File(ctx.root, "vivo.pdf").apply { writeText("x") }

        val ok = runBlocking { repo.restoreFromTrash(file.absolutePath) }

        assertTrue(ok)
        coVerify { trash.remove(file.absolutePath) }
    }

    @Test
    fun restaurarUnArchivoQueYaNoExisteDevuelveFalsoYLimpiaLaEntrada() {
        val missing = File(ctx.root, "no_existe.pdf").absolutePath

        val ok = runBlocking { repo.restoreFromTrash(missing) }

        assertFalse(ok)
        coVerify { trash.remove(missing) }
    }

    @Test
    fun restaurarUnDocumentoContentConsultaSiSigueExistiendo() {
        val present = "content://com.docsmart.fake/doc/1"
        val absent = "content://com.docsmart.fake/doc/2"
        val broken = "content://com.docsmart.fake/doc/3"
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            when (firstArg<Uri>().toString()) {
                present -> MatrixCursor(arrayOf("x")).apply { addRow(arrayOf("1")) }
                absent -> MatrixCursor(arrayOf("x"))
                else -> throw SecurityException()
            }
        }

        assertTrue(runBlocking { repo.restoreFromTrash(present) })
        assertFalse(runBlocking { repo.restoreFromTrash(absent) })
        assertFalse(runBlocking { repo.restoreFromTrash(broken) })
    }

    @Test
    fun siFallaRoomAlRestaurarDevuelveFalso() {
        val file = File(ctx.root, "vivo.pdf").apply { writeText("x") }
        coEvery { trash.remove(any()) } throws IllegalStateException("room")

        assertFalse(runBlocking { repo.restoreFromTrash(file.absolutePath) })
    }

    // ── Listado y purga ───────────────────────────────────────────────────────

    @Test
    fun laPapeleraListaSoloLoEliminadoOrdenadoDelMasRecienteAlMasAntiguo() {
        val now = System.currentTimeMillis()
        coEvery { documents.loadAllDocumentsRaw() } returns listOf(doc("a"), doc("b"), doc("c"))
        coEvery { trash.getAll() } returns listOf(TrashEntry("a", now - 1000), TrashEntry("b", now - 10))

        val trashed = runBlocking { repo.loadTrashedDocuments() }

        assertEquals(listOf("b", "a"), trashed.map { it.document.id })
        assertEquals(now - 10, trashed.first().deletedAt)
    }

    @Test
    fun unFalloDeLaPurgaNoImpideListarLaPapelera() {
        val now = System.currentTimeMillis()
        coEvery { documents.loadAllDocumentsRaw() } returns listOf(doc("a"))
        coEvery { trash.getAll() } throws IllegalStateException("room") andThen listOf(TrashEntry("a", now))

        val trashed = runBlocking { repo.loadTrashedDocuments() }

        assertEquals(listOf("a"), trashed.map { it.document.id })
    }

    @Test
    fun laPurgaExigeTiempoRealTranscurridoAntesDeBorrar() {
        coEvery { trash.getAll() } returns listOf(TrashEntry("viejo", 0L), TrashEntry("reciente", 100 * day))
        coEvery { documents.deleteDocument("viejo") } returns DocumentRepository.DeleteOutcome.Deleted
        val now = 100 * day

        // 1) Primera detección: solo se anota el instante (reloj real), no se borra.
        runBlocking { repo.purgeExpiredTrash(now, nowElapsed = 1_000L) }
        coVerify(exactly = 0) { documents.deleteDocument(any()) }
        assertEquals(1_000L, candidatePrefs().getLong("candidate_viejo", 0L))
        assertFalse(candidatePrefs().contains("candidate_reciente"))

        // 2) Pasó poco tiempo real: sigue sin borrarse (posible manipulación del reloj).
        runBlocking { repo.purgeExpiredTrash(now, nowElapsed = 2_000L) }
        coVerify(exactly = 0) { documents.deleteDocument(any()) }

        // 3) Pasó el mínimo real: se borra, se quita la entrada y se limpia la identidad.
        runBlocking { repo.purgeExpiredTrash(now, nowElapsed = 1_000L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE) }
        coVerify(exactly = 1) { documents.deleteDocument("viejo") }
        coVerify { trash.remove("viejo") }
        coVerify { identity.onPermanentlyDeleted("viejo") }
        assertFalse(candidatePrefs().contains("candidate_viejo"))
    }

    @Test
    fun trasUnReinicioElCandidatoSeReAnclaAlRelojDelNuevoArranque() {
        coEvery { trash.getAll() } returns listOf(TrashEntry("viejo", 0L))
        candidatePrefs().edit().putLong("candidate_viejo", 5_000_000L).commit()

        runBlocking { repo.purgeExpiredTrash(100 * day, nowElapsed = 100L) }

        assertEquals(100L, candidatePrefs().getLong("candidate_viejo", 0L))
        coVerify(exactly = 0) { documents.deleteDocument(any()) }
    }

    @Test
    fun siElBorradoPideAutorizacionOFallaLaEntradaSeConservaYLasDemasSePurgan() {
        coEvery { trash.getAll() } returns
            listOf(TrashEntry("permiso", 0L), TrashEntry("falla", 0L), TrashEntry("ok", 0L))
        coEvery { documents.deleteDocument("permiso") } returns
            DocumentRepository.DeleteOutcome.NeedsPermission(pendingSender())
        coEvery { documents.deleteDocument("falla") } throws IllegalStateException("io")
        coEvery { documents.deleteDocument("ok") } returns DocumentRepository.DeleteOutcome.Deleted
        listOf("permiso", "falla", "ok").forEach { candidatePrefs().edit().putLong("candidate_$it", 10L).commit() }

        runBlocking { repo.purgeExpiredTrash(100 * day, nowElapsed = 10L + TrashRepository.MIN_REAL_MS_BEFORE_PURGE) }

        coVerify(exactly = 1) { trash.remove("ok") }
        coVerify(exactly = 0) { trash.remove("permiso") }
        coVerify(exactly = 0) { trash.remove("falla") }
    }

    // ── Borrado definitivo ────────────────────────────────────────────────────

    @Test
    fun borrarParaSiempreSoloLimpiaSiElBorradoRealSeConfirmo() {
        coEvery { documents.deleteDocument("ok") } returns DocumentRepository.DeleteOutcome.Deleted
        coEvery { documents.deleteDocument("permiso") } returns
            DocumentRepository.DeleteOutcome.NeedsPermission(pendingSender())
        coEvery { documents.deleteDocument("falla") } returns DocumentRepository.DeleteOutcome.Failed

        assertEquals(DocumentRepository.DeleteOutcome.Deleted, runBlocking { repo.deleteForever("ok") })
        assertTrue(runBlocking { repo.deleteForever("permiso") } is DocumentRepository.DeleteOutcome.NeedsPermission)
        assertEquals(DocumentRepository.DeleteOutcome.Failed, runBlocking { repo.deleteForever("falla") })

        coVerify(exactly = 1) { trash.remove("ok") }
        coVerify { identity.onPermanentlyDeleted("ok") }
        coVerify(exactly = 0) { trash.remove("permiso") }
        coVerify(exactly = 0) { trash.remove("falla") }
    }

    @Test
    fun finalizarBorradoLimpiaLasTablasPropiasDeUnoOVariosDocumentos() {
        runBlocking {
            repo.finalizeDeleteForever("uno")
            repo.finalizeDeleteForever(listOf("dos", "tres"))
        }

        listOf("uno", "dos", "tres").forEach {
            coVerify { trash.remove(it) }
            coVerify { identity.onPermanentlyDeleted(it) }
        }
    }

    @Test
    fun borrarTodoSinFotosDeMediaStoreBorraUnoPorUnoYTerminaHecho() {
        coEvery { documents.deleteDocument("/app/a.pdf") } returns DocumentRepository.DeleteOutcome.Deleted
        coEvery { documents.deleteDocument("/app/b.pdf") } returns DocumentRepository.DeleteOutcome.Failed
        coEvery { documents.deleteDocument("content://com.docsmart.fake/document/1") } returns
            DocumentRepository.DeleteOutcome.Deleted
        val ids = listOf("/app/a.pdf", "/app/b.pdf", "content://com.docsmart.fake/document/1")

        val outcome = runBlocking { repo.deleteAllForever(ids) }

        assertEquals(TrashRepository.BulkDeleteOutcome.Done, outcome)
        coVerify { trash.remove("/app/a.pdf") }
        coVerify { trash.remove("content://com.docsmart.fake/document/1") }
        coVerify(exactly = 0) { trash.remove("/app/b.pdf") }
    }

    @Test
    fun borrarTodoAgrupaLasFotosEnUnSoloPedidoDeSistema() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        val sender = pendingSender()
        every { deletePermission.createBulkDeleteRequest(any()) } returns sender
        val media = listOf("content://media/external/images/media/1", "content://media/external/images/media/2")

        val outcome = runBlocking { repo.deleteAllForever(media) }

        assertEquals(TrashRepository.BulkDeleteOutcome.NeedsPermission(sender, media), outcome)
    }

    @Test
    fun sinPedidoMasivoLasFotosSeIntentanUnaPorUnaYSeInformaElPermisoPendiente() {
        every { deletePermission.createBulkDeleteRequest(any()) } returns null
        val done = "content://media/external/images/media/1"
        val pending = "content://media/external/images/media/2"
        coEvery { documents.deleteDocument(done) } returns DocumentRepository.DeleteOutcome.Deleted
        coEvery { documents.deleteDocument(pending) } returns
            DocumentRepository.DeleteOutcome.NeedsPermission(pendingSender())

        val partial = runBlocking { repo.deleteAllForever(listOf(done, pending)) }
        val allDone = runBlocking { repo.deleteAllForever(listOf(done)) }

        assertEquals(TrashRepository.BulkDeleteOutcome.PartialNeedsPermission, partial)
        assertEquals(TrashRepository.BulkDeleteOutcome.Done, allDone)
        coVerify { trash.remove(done) }
        coVerify(exactly = 0) { trash.remove(pending) }
    }
}
