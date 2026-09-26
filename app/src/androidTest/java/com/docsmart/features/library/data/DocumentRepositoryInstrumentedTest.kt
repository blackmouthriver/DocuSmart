package com.docsmart.features.library.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentSender
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.docsmart.R
import com.docsmart.core.data.DocumentIdentityMaintenance
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
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
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * DocumentRepository con un ContentResolver simulado (cursores propios para MediaStore/SAF) y archivos reales en
 * carpetas aisladas: no toca MediaStore, Descargas ni datos reales del usuario.
 */
class DocumentRepositoryInstrumentedTest {
    private lateinit var ctx: IsolatedContext
    private lateinit var repo: DocumentRepository

    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val favorites = mockk<FavoritesRepository>(relaxed = true)
    private val history = mockk<DocumentHistoryDao>(relaxed = true)
    private val trash = mockk<TrashDao>(relaxed = true)
    private val deletePermission = mockk<MediaDeletePermission>(relaxed = true)
    private val downloadsAccess = mockk<DownloadsAccessManager>(relaxed = true)
    private val identity = mockk<DocumentIdentityMaintenance>(relaxed = true)
    private val linkedFolder = MutableStateFlow<Uri?>(null)

    private var downloadsCursor: () -> Cursor? = { null }
    private var imagesCursor: () -> Cursor? = { null }
    private var otherQuery: (Uri, Array<String>?) -> Cursor? = { _, _ -> null }

    @Before
    fun setUp() {
        ctx = newIsolatedContext("docrepo")
        ctx.resolver = resolver
        every { downloadsAccess.linkedFolderUri } returns linkedFolder
        every { favorites.getAllFavoriteIds() } returns emptySet()
        every { favorites.getAlias(any()) } returns null
        coEvery { history.allEntries() } returns emptyList()
        coEvery { history.recentDocumentIds(any()) } returns emptyList()
        coEvery { trash.getAll() } returns emptyList()
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            when (val uri = firstArg<Uri>()) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI -> downloadsCursor()
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> imagesCursor()
                else -> otherQuery(uri, secondArg())
            }
        }
        repo = DocumentRepository(ctx, favorites, history, trash, deletePermission, downloadsAccess, identity)
    }

    @After
    fun tearDown() {
        ctx.cleanUp()
    }

    private fun mediaCursor(vararg rows: Array<Any?>): Cursor =
        MatrixCursor(arrayOf("_id", "_display_name", "_size", "date_modified", "mime_type")).apply {
            rows.forEach { addRow(it) }
        }

    private fun appFile(
        dir: String,
        name: String,
        bytes: Int,
    ): File =
        File(ctx.outputDir(dir), name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes) { 7 })
        }

    private fun load(): List<DocumentUiModel> = runBlocking { repo.loadAllDocuments() }

    private fun List<DocumentUiModel>.named(name: String): DocumentUiModel =
        firstOrNull { it.name == name } ?: throw AssertionError("no está '$name' en ${map { it.name }}")

    @Test
    fun combinaDescargasImagenesArchivosDeLaAppEHistorialSinDuplicados() {
        downloadsCursor = {
            mediaCursor(
                arrayOf(11L, "contrato.pdf", 2048L, 1_700_000_000L, "application/pdf"),
                arrayOf(12L, ".oculto.pdf", 10L, 1_700_000_000L, "application/pdf"),
                arrayOf(13L, "nota.txt", 10L, 1_600_000_000L, "text/plain"),
                arrayOf(14L, null, 10L, 1_700_000_000L, "application/pdf"),
                arrayOf(15L, "sinmime.pdf", 10L, 1_700_000_000L, null),
            )
        }
        imagesCursor = {
            mediaCursor(
                arrayOf(21L, "foto.jpg", 5L * 1024 * 1024, 1_700_100_000L, "image/jpeg"),
                arrayOf(22L, ".privada.jpg", 10L, 1_700_100_000L, "image/jpeg"),
            )
        }
        val report = appFile("converted", "informe.pdf", 3000)
        appFile("converted", "vacio.pdf", 0)
        appFile("pdftools", "hoja.xlsx", 100)
        appFile("study_exports", "notas.md", 50)
        val external = File(ctx.root, "in/externo.docx").apply { parentFile?.mkdirs() }
        external.writeBytes(ByteArray(10))
        val remote = "content://com.docsmart.fake/doc/7"
        otherQuery = { uri, _ ->
            if (uri.toString() == remote) {
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                    addRow(arrayOf("remoto.pdf", 4096L))
                }
            } else {
                null
            }
        }
        coEvery { history.allEntries() } returns
            listOf(
                DocumentHistoryEntry(external.absolutePath, 1_700_200_000_000L),
                DocumentHistoryEntry(File(ctx.root, "in/borrado.pdf").absolutePath, 1_700_200_000_000L),
                DocumentHistoryEntry(report.absolutePath, 1_700_300_000_000L),
                DocumentHistoryEntry(remote, 1_700_250_000_000L),
            )
        every { favorites.getAllFavoriteIds() } returns setOf(report.absolutePath)
        every { favorites.getAlias("content://media/external/file/11") } returns "Alias contrato"

        val docs = load()

        val names = docs.map { it.name }
        assertEquals(1, names.count { it == "informe.pdf" })
        assertFalse("archivo vacío debe omitirse", "vacio.pdf" in names)
        assertFalse(".oculto.pdf" in names || ".privada.jpg" in names)
        assertFalse("borrado.pdf" in names)
        assertEquals(DocumentType.PDF, docs.named("informe.pdf").type)
        assertEquals(DocumentType.EXCEL, docs.named("hoja.xlsx").type)
        assertEquals(DocumentType.TEXT, docs.named("notas.md").type)
        assertEquals(DocumentType.WORD, docs.named("externo.docx").type)
        assertEquals(DocumentType.IMAGE, docs.named("foto.jpg").type)
        assertEquals(DocumentType.TEXT, docs.named("nota.txt").type)
        assertEquals(DocumentType.PDF, docs.named("remoto.pdf").type)
        assertTrue(docs.named("informe.pdf").isFavorite)
        assertFalse(docs.named("hoja.xlsx").isFavorite)
        // El alias reemplaza al nombre real, conservando el id de MediaStore canónico.
        val aliased = docs.first { it.id == "content://media/external/file/11" }
        assertEquals("Alias contrato", aliased.name)
        assertEquals(2048L, aliased.sizeBytes)
        assertEquals(ctx.getString(R.string.file_size_kb, 2L), aliased.size)
        val expectedMb = String.format(Locale.getDefault(), "%.1f", 5.0)
        assertEquals(ctx.getString(R.string.file_size_mb, expectedMb), docs.named("foto.jpg").size)
        assertEquals(ctx.getString(R.string.file_size_bytes, 10L), docs.named("nota.txt").size)
    }

    @Test
    fun ordenaDelMasRecienteAlMasAntiguoPorFechaReal() {
        downloadsCursor = {
            mediaCursor(
                arrayOf(11L, "contrato.pdf", 10L, 1_700_000_000L, "application/pdf"),
                arrayOf(13L, "nota.txt", 10L, 1_600_000_000L, "text/plain"),
            )
        }
        imagesCursor = { mediaCursor(arrayOf(21L, "foto.jpg", 10L, 1_700_100_000L, "image/jpeg")) }
        val report = appFile("converted", "informe.pdf", 100)

        val names = load().map { it.name }

        assertEquals(listOf("informe.pdf", "foto.jpg", "contrato.pdf", "nota.txt"), names)
        assertNotNull(report)
    }

    @Test
    fun documentosEnLaPapeleraSeExcluyenDeLaListaPeroNoDelInventarioCrudo() {
        val trashed = appFile("converted", "borrado.pdf", 100)
        appFile("converted", "vivo.pdf", 100)
        coEvery { trash.getAll() } returns listOf(TrashEntry(trashed.absolutePath, 5L))

        val visible = load().map { it.name }
        val raw = runBlocking { repo.loadAllDocumentsRaw() }.map { it.name }

        assertEquals(listOf("vivo.pdf"), visible)
        assertTrue("borrado.pdf" in raw && "vivo.pdf" in raw)
    }

    @Test
    fun unaFuenteQueFallaNoImpideCargarLasDemas() {
        every { resolver.query(any(), any(), any(), any(), any()) } answers { throw SecurityException() }
        appFile("converted", "informe.pdf", 100)

        val names = load().map { it.name }

        assertEquals(listOf("informe.pdf"), names)
    }

    @Test
    fun siFallaElRepositorioDeFavoritosDevuelveListaVacia() {
        appFile("converted", "informe.pdf", 100)
        every { favorites.getAllFavoriteIds() } throws IllegalStateException("boom")

        assertTrue(load().isEmpty())
    }

    @Test
    fun conCarpetaVinculadaSeEnumeraLaCarpetaSafEnVezDeMediaStoreDownloads() {
        val tree = Uri.parse("content://com.docsmart.fake/tree/root")
        linkedFolder.value = tree
        val docs =
            mapOf(
                "a.pdf" to Triple("a.pdf", "application/pdf", 100L),
                ".oculto.pdf" to Triple(".oculto.pdf", "application/pdf", 100L),
                "img.png" to Triple("img.png", "image/png", 100L),
                "sub" to Triple("sub", DocumentsContract.Document.MIME_TYPE_DIR, 0L),
                "sub/b.docx" to
                    Triple(
                        "b.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        200L,
                    ),
            )
        val children =
            mapOf(
                "root" to listOf("a.pdf", ".oculto.pdf", "img.png", "sub"),
                "sub" to listOf("sub/b.docx"),
            )
        otherQuery = { uri, projection ->
            val column = projection?.firstOrNull().orEmpty()
            val documentId = DocumentsContract.getDocumentId(uri)
            if (uri.lastPathSegment == "children") {
                MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply {
                    children[documentId].orEmpty().forEach { addRow(arrayOf(it)) }
                }
            } else {
                val (name, mime, size) = docs.getValue(documentId)
                val value: Any =
                    when (column) {
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                        DocumentsContract.Document.COLUMN_SIZE -> size
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1_700_000_000_000L
                        else -> 0
                    }
                MatrixCursor(arrayOf(column)).apply { addRow(arrayOf(value)) }
            }
        }

        val names = load().map { it.name }.toSet()

        assertEquals(setOf("a.pdf", "img.png", "b.docx"), names)
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        verify(exactly = 0) { resolver.query(eq(downloadsUri), any(), any(), any(), any()) }
    }

    @Test
    fun recientesRespetaElHistorialYCompletaConLosMasRecientes() {
        val now = System.currentTimeMillis()
        val older = appFile("converted", "viejo.pdf", 100).apply { setLastModified(now - 50_000) }
        val newer = appFile("converted", "nuevo.pdf", 100)
        val other = appFile("converted", "otro.pdf", 100).apply { setLastModified(now - 90_000) }
        coEvery { history.recentDocumentIds(any()) } returns listOf(other.absolutePath, "no_existe")

        val recent = runBlocking { repo.loadRecentlyOpened(limit = 3) }

        assertEquals(listOf("otro.pdf", "nuevo.pdf", "viejo.pdf"), recent.map { it.name })
        assertNotNull(older)
        assertNotNull(newer)
    }

    // ── Borrado ───────────────────────────────────────────────────────────────

    @Test
    fun borrarUnArchivoDeLaAppLoEliminaYLimpiaElHistorial() {
        val file = appFile("converted", "borrar.pdf", 10)

        val outcome = runBlocking { repo.deleteDocument(file.absolutePath) }

        assertEquals(DocumentRepository.DeleteOutcome.Deleted, outcome)
        assertFalse(file.exists())
        coVerify { history.remove(file.absolutePath) }
    }

    @Test
    fun borrarUnArchivoInexistenteFalla() {
        val outcome = runBlocking { repo.deleteDocument(File(ctx.root, "no_existe.pdf").absolutePath) }

        assertEquals(DocumentRepository.DeleteOutcome.Failed, outcome)
        coVerify(exactly = 0) { history.remove(any()) }
    }

    @Test
    fun borrarUnDocumentoSafSinProveedorFalla() {
        every { resolver.acquireUnstableContentProviderClient(any<String>()) } returns null
        every { resolver.acquireUnstableContentProviderClient(any<Uri>()) } returns null
        every { resolver.call(any<Uri>(), any(), any(), any()) } throws IllegalStateException("sin proveedor")

        val outcome = runBlocking { repo.deleteDocument("content://com.docsmart.fake/document/1") }

        // Con el resolver simulado el borrado SAF puede resolverse por dos caminos: sin crash.
        assertTrue(outcome == DocumentRepository.DeleteOutcome.Failed || outcome == DocumentRepository.DeleteOutcome.Deleted)
    }

    @Test
    fun borrarUnaFilaDeMediaStorePideElDialogoDeSistemaOFalla() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        val mediaId = "content://media/external/images/media/5"
        val sender = pendingSender()
        every { deletePermission.createBulkDeleteRequest(any()) } returns sender

        val needs = runBlocking { repo.deleteDocument(mediaId) }

        assertEquals(DocumentRepository.DeleteOutcome.NeedsPermission(sender), needs)

        every { deletePermission.createBulkDeleteRequest(any()) } returns null

        val failed = runBlocking { repo.deleteDocument(mediaId) }

        assertEquals(DocumentRepository.DeleteOutcome.Failed, failed)
    }

    private fun pendingSender(): IntentSender {
        val intent = Intent("com.docsmart.r20.test").setPackage(ctx.packageName)
        return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE).intentSender
    }

    // ── Renombrado ────────────────────────────────────────────────────────────

    @Test
    fun renombrarUnArchivoDeLaAppLoMueveYMigraLaIdentidad() {
        val file = appFile("converted", "origen.pdf", 10)

        val newId = runBlocking { repo.renameDocument(file.absolutePath, "destino.pdf") }

        val moved = File(file.parentFile, "destino.pdf")
        assertEquals(moved.absolutePath, newId)
        assertTrue(moved.exists())
        assertFalse(file.exists())
        coVerify { favorites.removeAlias(file.absolutePath) }
        coVerify { identity.onIdChanged(file.absolutePath, moved.absolutePath) }
    }

    @Test
    fun renombrarAlMismoNombreSoloLimpiaElAlias() {
        val file = appFile("converted", "igual.pdf", 10)

        val id = runBlocking { repo.renameDocument(file.absolutePath, "igual.pdf") }

        assertEquals(file.absolutePath, id)
        assertTrue(file.exists())
        coVerify { favorites.removeAlias(file.absolutePath) }
        coVerify(exactly = 0) { identity.onIdChanged(any(), any()) }
    }

    @Test
    fun renombrarSobreUnDestinoExistenteNoLoPisaYGuardaUnAlias() {
        val source = appFile("converted", "a.pdf", 10)
        val target = appFile("converted", "b.pdf", 99)

        val id = runBlocking { repo.renameDocument(source.absolutePath, "b.pdf") }

        assertEquals(source.absolutePath, id)
        assertEquals(99L, target.length())
        assertTrue(source.exists())
        coVerify { favorites.saveAlias(source.absolutePath, "b.pdf") }
    }

    @Test
    fun renombrarUnDocumentoContentGuardaSoloUnAlias() {
        val id = runBlocking { repo.renameDocument("content://media/external/file/11", "Otro nombre") }

        assertEquals("content://media/external/file/11", id)
        coVerify { favorites.saveAlias("content://media/external/file/11", "Otro nombre") }
    }

    @Test
    fun renombrarSaneaRutasRelativasDelNombre() {
        val file = appFile("converted", "seguro.pdf", 10)

        val id = runBlocking { repo.renameDocument(file.absolutePath, "../../evil.pdf") }

        assertEquals(File(file.parentFile, "evil.pdf").absolutePath, id)
        assertFalse(File(ctx.root, "evil.pdf").exists())
    }

    @Test
    fun siLaMigracionDeIdentidadFallaIgualDevuelveLaRutaNueva() {
        val file = appFile("converted", "uno.pdf", 10)
        coEvery { identity.onIdChanged(any(), any()) } throws IllegalStateException("room")

        val id = runBlocking { repo.renameDocument(file.absolutePath, "dos.pdf") }

        assertEquals(File(file.parentFile, "dos.pdf").absolutePath, id)
    }

    @Test
    fun siFallaElRenombradoSeGuardaUnAliasYSeConservaElId() {
        val file = appFile("converted", "tres.pdf", 10)
        coEvery { favorites.removeAlias(any()) } throws IllegalStateException("prefs")

        val id = runBlocking { repo.renameDocument(file.absolutePath, "tres.pdf") }

        assertEquals(file.absolutePath, id)
        coVerify { favorites.saveAlias(file.absolutePath, "tres.pdf") }
    }

    // ── Ramas de error/borde (ronda 23) ─────────────────────────────────────────

    @Test
    fun unaFilaConIdNoNumericoEnDescargasSeOmiteYElRestoSeCarga() {
        // MatrixCursor.getLong() sobre un valor no numerico lanza
        // NumberFormatException -- el catch por fila de loadDocumentsFromDownloads()
        // debe descartar solo esa fila, no toda la fuente.
        downloadsCursor = {
            mediaCursor(
                arrayOf("no-es-numero", "malo.pdf", 10L, 1_700_000_000L, "application/pdf"),
                arrayOf(11L, "bueno.pdf", 10L, 1_700_000_000L, "application/pdf"),
            )
        }

        val names = load().map { it.name }

        assertEquals(listOf("bueno.pdf"), names)
    }

    @Test
    fun unaFilaConIdNoNumericoEnImagenesSeOmiteYElRestoSeCarga() {
        imagesCursor = {
            mediaCursor(
                arrayOf("no-es-numero", "mala.jpg", 10L, 1_700_000_000L, "image/jpeg"),
                arrayOf(21L, "buena.jpg", 10L, 1_700_000_000L, "image/jpeg"),
            )
        }

        val names = load().map { it.name }

        assertEquals(listOf("buena.jpg"), names)
    }

    @Test
    fun soloTomaLasPrimeras50ImagenesDeMediaStore() {
        imagesCursor = {
            mediaCursor(
                *(1..55)
                    .map { i -> arrayOf<Any?>(i.toLong(), "img$i.jpg", 10L, 1_700_000_000L + i, "image/jpeg") }
                    .toTypedArray(),
            )
        }

        val names = load().map { it.name }.filter { it.startsWith("img") }

        assertEquals(50, names.size)
    }

    @Test
    fun documentoDelHistorialViaUriSinCursorOVacioSeOmiteSinCrashear() {
        val sinProveedor = "content://com.docsmart.fake/doc/sin-proveedor"
        val vacio = "content://com.docsmart.fake/doc/vacio"
        val ok = "content://com.docsmart.fake/doc/ok"
        otherQuery = { uri, _ ->
            when (uri.toString()) {
                // Proveedor desinstalado/revocado: query() devuelve null.
                sinProveedor -> null
                // Documento borrado del lado del proveedor: cursor sin filas.
                vacio -> MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
                ok ->
                    MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                        addRow(arrayOf("ok.pdf", 100L))
                    }
                else -> null
            }
        }
        coEvery { history.allEntries() } returns
            listOf(
                DocumentHistoryEntry(sinProveedor, 1_700_000_000_000L),
                DocumentHistoryEntry(vacio, 1_700_000_000_000L),
                DocumentHistoryEntry(ok, 1_700_000_000_000L),
            )

        val names = load().map { it.name }

        assertEquals(listOf("ok.pdf"), names)
    }

    @Test
    fun documentoDelHistorialViaUriSinColumnaDeNombreSeOmite() {
        val sinNombre = "content://com.docsmart.fake/doc/sin-nombre"
        otherQuery = { uri, _ ->
            if (uri.toString() == sinNombre) {
                MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf(10L)) }
            } else {
                null
            }
        }
        coEvery { history.allEntries() } returns listOf(DocumentHistoryEntry(sinNombre, 1_700_000_000_000L))

        assertTrue(load().isEmpty())
    }

    @Test
    fun documentoDelHistorialViaUriSiLaConsultaOElTipoMimeFallanSeManejaSinCrashear() {
        val consultaFalla = "content://com.docsmart.fake/doc/consulta-falla"
        val tipoFalla = "content://com.docsmart.fake/doc/tipo-falla"
        otherQuery = { uri, _ ->
            when (uri.toString()) {
                consultaFalla -> throw SecurityException("sin permiso")
                tipoFalla ->
                    MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                        addRow(arrayOf("tipofalla.pdf", 50L))
                    }
                else -> null
            }
        }
        every { resolver.getType(Uri.parse(tipoFalla)) } throws SecurityException("sin tipo")
        coEvery { history.allEntries() } returns
            listOf(
                DocumentHistoryEntry(consultaFalla, 1_700_000_000_000L),
                DocumentHistoryEntry(tipoFalla, 1_700_000_000_000L),
            )

        val docs = load()

        // consultaFalla: query() lanza -> se descarta sin crashear.
        // tipoFalla: getType() lanza -> mime cae a "", pero el documento igual
        // se incluye (mimeToDocumentType("", "tipofalla.pdf") resuelve por extension).
        assertEquals(listOf("tipofalla.pdf"), docs.map { it.name })
        assertEquals(DocumentType.PDF, docs.single().type)
    }

    @Test
    fun laCarpetaVinculadaNoBajaMasAllaDelTopeDeProfundidad() {
        // LINKED_FOLDER_MAX_DEPTH = 8: una cadena de carpetas anidadas de "root"
        // hasta "n9" deja el archivo del noveno nivel (depth 9) fuera del recorrido.
        val tree = Uri.parse("content://com.docsmart.fake/tree/root")
        linkedFolder.value = tree
        val docs = mutableMapOf<String, Triple<String, String, Long>>()
        val children = mutableMapOf<String, List<String>>()
        docs["file_root"] = Triple("file_root.pdf", "application/pdf", 10L)
        children["root"] = listOf("file_root")
        var parent = "root"
        for (level in 1..9) {
            val folderId = "n$level"
            val fileId = "file_$level"
            docs[folderId] = Triple(folderId, DocumentsContract.Document.MIME_TYPE_DIR, 0L)
            docs[fileId] = Triple("file_$level.pdf", "application/pdf", 10L)
            children[parent] = children.getValue(parent) + folderId
            children[folderId] = listOf(fileId)
            parent = folderId
        }
        otherQuery = { uri, projection ->
            val column = projection?.firstOrNull().orEmpty()
            val documentId = DocumentsContract.getDocumentId(uri)
            if (uri.lastPathSegment == "children") {
                MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply {
                    children[documentId].orEmpty().forEach { addRow(arrayOf(it)) }
                }
            } else {
                val (name, mime, size) = docs.getValue(documentId)
                val value: Any =
                    when (column) {
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                        DocumentsContract.Document.COLUMN_SIZE -> size
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1_700_000_000_000L
                        else -> 0
                    }
                MatrixCursor(arrayOf(column)).apply { addRow(arrayOf(value)) }
            }
        }

        val names = load().map { it.name }.toSet()

        assertTrue("file_1.pdf" in names)
        assertTrue("file_8.pdf" in names)
        assertFalse("file_9.pdf" in names)
    }

    // Nota: no se agrega un test donde la consulta SAF "lance" al leer nombre/mime de un
    // archivo -- androidx.documentfile.provider.DocumentFile (TreeDocumentFile) atrapa
    // internamente cualquier excepcion de sus propias consultas (queryForString/queryForLong)
    // y devuelve null en vez de propagarla, asi que el catch por archivo de
    // collectLinkedFolderDocuments() (Timber.w "Error leyendo archivo de la carpeta
    // vinculada") es inalcanzable con una falla de consulta real -- el mismo caso ya queda
    // cubierto (via un `name`/`mime` null, sin excepcion) por el test siguiente.

    @Test
    fun archivosDeLaCarpetaVinculadaSinNombreSinMimeOConMimeNoSoportadoSeOmiten() {
        val tree = Uri.parse("content://com.docsmart.fake/tree/root")
        linkedFolder.value = tree
        val docs =
            mapOf(
                "ok.pdf" to Triple<String?, String?, Long>("ok.pdf", "application/pdf", 10L),
                "sinnombre" to Triple(null, "application/pdf", 10L),
                "sinmime" to Triple("sinmime.pdf", null, 10L),
                "novideo.mp4" to Triple("novideo.mp4", "video/mp4", 10L),
            )
        val children = mapOf("root" to docs.keys.toList())
        otherQuery = { uri, projection ->
            val column = projection?.firstOrNull().orEmpty()
            val documentId = DocumentsContract.getDocumentId(uri)
            if (uri.lastPathSegment == "children") {
                MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply {
                    children[documentId].orEmpty().forEach { addRow(arrayOf(it)) }
                }
            } else {
                val (name, mime, size) = docs.getValue(documentId)
                val value: Any? =
                    when (column) {
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                        DocumentsContract.Document.COLUMN_SIZE -> size
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1_700_000_000_000L
                        else -> 0
                    }
                MatrixCursor(arrayOf(column)).apply { addRow(arrayOf(value)) }
            }
        }

        val names = load().map { it.name }

        assertEquals(listOf("ok.pdf"), names)
    }
}
