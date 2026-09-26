package com.docsmart.features.viewer.presentation

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.AnnotationDao
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.LastViewedPageDao
import com.docsmart.core.data.db.LastViewedPageEntity
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.core.data.db.PageBookmarkDao
import com.docsmart.core.data.db.PageBookmarkEntity
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.annotation.PdfRectPts
import com.docsmart.features.viewer.domain.usecase.FlattenAnnotationsPdfUseCase
import com.docsmart.features.viewer.domain.usecase.PdfMatchRect
import com.docsmart.features.viewer.domain.usecase.PdfPageMatches
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

private const val REAL_DOC_ID = "/data/x/doc.txt"
private const val PREVIEW_DOC_ID = "/cache/secure_preview/doc.txt"
private const val LOCKED_DOC_ID = "content://media/locked.pdf"
private const val LOCKED_PREVIEW_DOC_ID = "content://media/secure_preview/locked.pdf"
private const val PDF_PASSWORD = "clave-correcta"

/**
 * Cubre las transiciones de estado de `ViewerViewModel` (carga, favoritos,
 * renombrar/eliminar, marcadores, ultima pagina vista, busqueda, anotaciones,
 * compartir, desbloqueo con contrasena) sin Robolectric.
 *
 * Como se evita Android real:
 * - `Uri.parse`/`Uri.fromFile` se mockean con `mockkStatic(Uri::class)` (mismo
 *   patron que `DocumentSharingTest`); todas las Uri son un mismo mock relajado.
 * - `Context`/`ContentResolver` son mocks; `getString(id)` devuelve
 *   `"str:<id>"` para poder afirmar QUE mensaje se eligio.
 * - Las cargas de un documento "real" (ruta absoluta o `content://`) corren en
 *   `Dispatchers.IO` real -- por eso se espera el estado con [awaitState].
 *
 * Fuera de alcance (no testeable en JVM puro): el camino feliz de
 * compartir (`Intent(...)`, `Intent.createChooser`, `startActivity` y
 * `FileProvider.getUriForFile` lanzan "not mocked"). Los tests de compartir
 * verifican el manejo de ERROR: que un fallo al lanzar el selector deje un
 * `shareError` transitorio y NO reemplace el Visor con `error`. Tampoco se
 * testea `onCleared()` (protegido) ni el render de `PdfRenderer`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var fakeUri: Uri

    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var searchPdfText: SearchPdfTextUseCase
    private lateinit var documentHistoryDao: DocumentHistoryDao
    private lateinit var documentRepository: DocumentRepository
    private lateinit var trashRepository: TrashRepository
    private lateinit var annotationDao: AnnotationDao
    private lateinit var flattenUseCase: FlattenAnnotationsPdfUseCase
    private lateinit var pageBookmarkDao: PageBookmarkDao
    private lateinit var lastViewedPageDao: LastViewedPageDao
    private lateinit var noteDao: NoteDao

    private val annotationsFlow = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    private val bookmarksFlow = MutableStateFlow<List<PageBookmarkEntity>>(emptyList())
    private val notesFlow = MutableStateFlow<List<NoteWithImages>>(emptyList())
    private lateinit var linkedNote: NoteEntity

    private lateinit var viewModel: ViewerViewModel

    @BeforeEach
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        tempDir = Files.createTempDirectory("docsmart_viewer_vm_").toFile()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }

        mockkStatic(Uri::class)
        fakeUri = mockk(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { Uri.fromFile(any()) } returns fakeUri

        resolver = mockk()
        every { resolver.getType(any()) } returns "text/plain"
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { resolver.openInputStream(any()) } returns null

        context = mockk()
        every { context.applicationContext } returns context
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns resolver
        every { context.getString(any<Int>()) } answers { str(firstArg()) }

        favoritesRepository = mockk(relaxed = true)
        every { favoritesRepository.getAlias(any()) } returns null
        every { favoritesRepository.isFavorite(any()) } returns false
        searchPdfText = mockk()
        documentHistoryDao = mockk(relaxed = true)
        documentRepository = mockk()
        trashRepository = mockk()
        annotationDao = mockk(relaxed = true)
        flattenUseCase = mockk()
        pageBookmarkDao = mockk(relaxed = true)
        lastViewedPageDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)

        linkedNote = mockk()
        val noteWithImages = mockk<NoteWithImages>()
        every { noteWithImages.note } returns linkedNote
        notesFlow.value = listOf(noteWithImages)

        annotationsFlow.value = listOf(annotation("existing", page = 1))
        bookmarksFlow.value = emptyList()
        every { annotationDao.observeByDocument(any()) } returns annotationsFlow
        every { pageBookmarkDao.observeByDocument(any()) } returns bookmarksFlow
        every { noteDao.observeByDocument(any()) } returns notesFlow
        coEvery { lastViewedPageDao.getByDocument(any()) } returns null

        viewModel =
            ViewerViewModel(
                favoritesRepository = favoritesRepository,
                searchPdfText = searchPdfText,
                documentHistoryDao = documentHistoryDao,
                documentRepository = documentRepository,
                trashRepository = trashRepository,
                adManager = mockk<AdManager>(relaxed = true),
                annotationDao = annotationDao,
                flattenAnnotationsPdfUseCase = flattenUseCase,
                pageBookmarkDao = pageBookmarkDao,
                lastViewedPageDao = lastViewedPageDao,
                noteDao = noteDao,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    // ── carga ─────────────────────────────────────────────────────────────

    @Test
    fun `documento mock existente publica el documento y su estado de favorito`() {
        every { favoritesRepository.isFavorite("1") } returns true

        viewModel.loadDocument("1", context)

        val state = viewModel.uiState.value
        assertEquals("Contrato_Servicios_2024.pdf", state.document?.name)
        assertTrue(state.isFavorite)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `documento mock inexistente deja error localizado y sin documento`() {
        viewModel.loadDocument("no-existe", context)

        val state = viewModel.uiState.value
        assertNull(state.document)
        assertFalse(state.isLoading)
        assertEquals(str(R.string.viewer_document_not_found), state.error)
    }

    @Test
    fun `documento real publica uri, mime, nombre y registra el historial`() {
        loadRealAndAwait(REAL_DOC_ID)

        val state = viewModel.uiState.value
        assertEquals("doc.txt", state.document?.name)
        assertEquals(REAL_DOC_ID, state.document?.id)
        assertEquals("text/plain", state.mimeType)
        assertSame(fakeUri, state.fileUri)
        assertFalse(state.isReadOnlyPreview)
        assertFalse(state.isLoading)
        coVerify(timeout = 5000) { documentHistoryDao.recordOpen(match { it.documentId == REAL_DOC_ID }) }
        assertEquals(listOf(linkedNote), state.linkedNotes)
    }

    @Test
    fun `el alias guardado tiene prioridad sobre el nombre real del archivo`() {
        every { favoritesRepository.getAlias(REAL_DOC_ID) } returns "Mi alias"

        loadRealAndAwait(REAL_DOC_ID)

        assertEquals("Mi alias", viewModel.uiState.value.document?.name)
    }

    @Test
    fun `resolveFileName usa el DISPLAY_NAME cuando el proveedor lo entrega`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "Reporte Final.docx"
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor

        loadRealAndAwait("content://media/algo")

        assertEquals("Reporte Final.docx", viewModel.uiState.value.document?.name)
    }

    @Test
    fun `un fallo inesperado al cargar deja error localizado y no deja isLoading trabado`() {
        every { favoritesRepository.getAlias(any()) } throws IllegalStateException("boom")

        viewModel.loadDocument(REAL_DOC_ID, context)
        val state = awaitState { !it.isLoading }

        assertEquals(str(R.string.viewer_error), state.error)
        assertNull(state.document)
    }

    // ── vista previa de solo lectura (Carpeta Segura) ──────────────────────

    @Test
    fun `la copia efimera de secure_preview se carga como solo lectura sin historial ni marcadores`() {
        loadRealAndAwait(PREVIEW_DOC_ID)

        assertTrue(viewModel.uiState.value.isReadOnlyPreview)
        coVerify(exactly = 0) { documentHistoryDao.recordOpen(any()) }
        verify(exactly = 0) { pageBookmarkDao.observeByDocument(any()) }
        verify(exactly = 0) { noteDao.observeByDocument(any()) }
        coVerify(exactly = 0) { lastViewedPageDao.getByDocument(any()) }
    }

    @Test
    fun `en solo lectura favorito, anotar, resaltar, nota y marcador son no-ops`() {
        loadRealAndAwait(PREVIEW_DOC_ID)

        viewModel.toggleFavorite()
        viewModel.toggleAnnotationToolbar()
        viewModel.addHighlight(1, PdfRectPts(1f, 2f, 3f, 4f))
        viewModel.requestAddNote(1, PdfRectPts(1f, 2f, 0f, 0f))
        viewModel.confirmNote("texto")
        viewModel.onPageChanged(3, 10)
        viewModel.toggleBookmarkCurrentPage()

        val state = viewModel.uiState.value
        assertFalse(state.showAnnotationToolbar)
        assertNull(state.pendingNoteAnchor)
        coVerify(exactly = 0) { favoritesRepository.toggleFavorite(any()) }
        coVerify(exactly = 0) { annotationDao.insert(any()) }
        coVerify(exactly = 0) { pageBookmarkDao.insert(any()) }
        // Tampoco se persiste la "ultima pagina vista" de la copia efimera.
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
        coVerify(exactly = 0) { lastViewedPageDao.save(any()) }
    }

    @Test
    fun `en solo lectura renombrar y eliminar solo cierran el dialogo, sin tocar repositorios`() {
        loadRealAndAwait(PREVIEW_DOC_ID)
        viewModel.onRenameClick()
        viewModel.onDeleteClick()

        viewModel.renameDocument("otro.txt")
        viewModel.confirmDelete(context)

        val state = viewModel.uiState.value
        assertFalse(state.showRenameDialog)
        assertFalse(state.showDeleteConfirm)
        assertFalse(state.documentDeleted)
        coVerify(exactly = 0) { documentRepository.renameDocument(any(), any()) }
        coVerify(exactly = 0) { trashRepository.moveToTrash(any()) }
    }

    @Test
    fun `en solo lectura compartir no abre el dialogo ni deja error`() {
        loadRealAndAwait(PREVIEW_DOC_ID)

        viewModel.shareDocument(context)
        viewModel.shareWithAnnotations(context)

        val state = viewModel.uiState.value
        assertFalse(state.showShareChoiceDialog)
        assertFalse(state.isFlatteningForShare)
        assertNull(state.shareError)
        coVerify(exactly = 0) { flattenUseCase(any(), any()) }
    }

    // ── favoritos y controles ──────────────────────────────────────────────

    @Test
    fun `toggleFavorite alterna el estado segun lo que devuelve el repositorio`() {
        viewModel.loadDocument("1", context)
        coEvery { favoritesRepository.toggleFavorite("1") } returnsMany listOf(true, false)

        viewModel.toggleFavorite()
        assertTrue(viewModel.uiState.value.isFavorite)
        assertTrue(viewModel.uiState.value.document?.isFavorite == true)

        viewModel.toggleFavorite()
        assertFalse(viewModel.uiState.value.isFavorite)
        assertTrue(viewModel.uiState.value.document?.isFavorite == false)
    }

    @Test
    fun `toggleFavorite sin documento cargado no llama al repositorio`() {
        viewModel.toggleFavorite()

        coVerify(exactly = 0) { favoritesRepository.toggleFavorite(any()) }
    }

    @Test
    fun `toggleControls y los dialogos de notas vinculadas cambian solo su bandera`() {
        assertTrue(viewModel.uiState.value.showControls)
        viewModel.toggleControls()
        assertFalse(viewModel.uiState.value.showControls)

        viewModel.showLinkedNotesDialog()
        assertTrue(viewModel.uiState.value.showLinkedNotesDialog)
        viewModel.dismissLinkedNotesDialog()
        assertFalse(viewModel.uiState.value.showLinkedNotesDialog)
    }

    // ── renombrar / eliminar ───────────────────────────────────────────────

    @Test
    fun `renombrar con nombre en blanco o igual cierra el dialogo sin llamar al repositorio`() {
        viewModel.loadDocument("1", context)
        viewModel.onRenameClick()

        viewModel.renameDocument("   ")
        assertFalse(viewModel.uiState.value.showRenameDialog)

        viewModel.onRenameClick()
        viewModel.renameDocument("  Contrato_Servicios_2024.pdf ")
        assertFalse(viewModel.uiState.value.showRenameDialog)

        coVerify(exactly = 0) { documentRepository.renameDocument(any(), any()) }
    }

    @Test
    fun `renombrar con cambio de id actualiza el documento y re-suscribe anotaciones, marcadores y notas`() {
        viewModel.loadDocument("1", context)
        coEvery { documentRepository.renameDocument("1", "Nuevo.pdf") } returns "/x/Nuevo.pdf"
        viewModel.onRenameClick()

        viewModel.renameDocument("  Nuevo.pdf ")

        val state = viewModel.uiState.value
        assertEquals("/x/Nuevo.pdf", state.document?.id)
        assertEquals("Nuevo.pdf", state.document?.name)
        assertSame(fakeUri, state.fileUri)
        assertFalse(state.showRenameDialog)
        verify { annotationDao.observeByDocument("/x/Nuevo.pdf") }
        verify { pageBookmarkDao.observeByDocument("/x/Nuevo.pdf") }
        verify { noteDao.observeByDocument("/x/Nuevo.pdf") }
    }

    @Test
    fun `renombrar sin cambio de id (alias) solo cambia el nombre y no re-suscribe`() {
        viewModel.loadDocument("1", context)
        coEvery { documentRepository.renameDocument("1", "Alias") } returns "1"

        viewModel.renameDocument("Alias")

        val state = viewModel.uiState.value
        assertEquals("1", state.document?.id)
        assertEquals("Alias", state.document?.name)
        verify(exactly = 0) { annotationDao.observeByDocument(any()) }
    }

    @Test
    fun `eliminar con exito marca documentDeleted y cierra la confirmacion`() {
        viewModel.loadDocument("1", context)
        coEvery { trashRepository.moveToTrash("1") } returns true
        viewModel.onDeleteClick()
        assertTrue(viewModel.uiState.value.showDeleteConfirm)

        viewModel.confirmDelete(context)

        val state = viewModel.uiState.value
        assertTrue(state.documentDeleted)
        assertFalse(state.showDeleteConfirm)
        assertNull(state.deleteError)
    }

    @Test
    fun `eliminar con fallo deja deleteError localizado y no marca documentDeleted`() {
        viewModel.loadDocument("1", context)
        coEvery { trashRepository.moveToTrash("1") } returns false

        viewModel.confirmDelete(context)

        val state = viewModel.uiState.value
        assertFalse(state.documentDeleted)
        assertEquals(str(R.string.general_delete_error), state.deleteError)

        viewModel.dismissDeleteError()
        assertNull(viewModel.uiState.value.deleteError)
    }

    // ── ultima pagina y marcadores ─────────────────────────────────────────

    @Test
    fun `onPageChanged actualiza pagina y total de inmediato`() {
        viewModel.onPageChanged(4, 12)

        assertEquals(4, viewModel.uiState.value.currentPage)
        assertEquals(12, viewModel.uiState.value.totalPages)
    }

    @Test
    fun `la ultima pagina se guarda con debounce y solo la pagina donde el usuario se detuvo`() {
        viewModel.loadDocument("1", context)

        viewModel.onPageChanged(2, 10)
        scheduler.advanceTimeBy(300)
        viewModel.onPageChanged(5, 10)
        scheduler.advanceTimeBy(599)
        scheduler.runCurrent()
        coVerify(exactly = 0) { lastViewedPageDao.save(any()) }

        scheduler.advanceTimeBy(2)
        scheduler.runCurrent()

        val saved = slot<LastViewedPageEntity>()
        coVerify(exactly = 1) { lastViewedPageDao.save(capture(saved)) }
        assertEquals("1", saved.captured.documentId)
        assertEquals(5, saved.captured.page)
    }

    @Test
    fun `sin documento cargado onPageChanged no persiste la ultima pagina`() {
        viewModel.onPageChanged(3, 10)
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()

        coVerify(exactly = 0) { lastViewedPageDao.save(any()) }
    }

    @Test
    fun `al abrir un documento con ultima pagina guardada mayor que 0 se pide el salto`() {
        coEvery { lastViewedPageDao.getByDocument(REAL_DOC_ID) } returns LastViewedPageEntity(REAL_DOC_ID, 4, 0L)

        loadRealAndAwait(REAL_DOC_ID)

        assertEquals(4, viewModel.uiState.value.pendingPageJump)
        viewModel.onPageJumpConsumed()
        assertNull(viewModel.uiState.value.pendingPageJump)
    }

    @Test
    fun `una ultima pagina guardada igual a 0 no dispara ningun salto`() {
        coEvery { lastViewedPageDao.getByDocument(REAL_DOC_ID) } returns LastViewedPageEntity(REAL_DOC_ID, 0, 0L)

        loadRealAndAwait(REAL_DOC_ID)

        coVerify { lastViewedPageDao.getByDocument(REAL_DOC_ID) }
        assertNull(viewModel.uiState.value.pendingPageJump)
    }

    @Test
    fun `los marcadores observados se reflejan como set de paginas`() {
        bookmarksFlow.value = listOf(PageBookmarkEntity(REAL_DOC_ID, 2, 0L), PageBookmarkEntity(REAL_DOC_ID, 7, 0L))

        loadRealAndAwait(REAL_DOC_ID)

        assertEquals(setOf(2, 7), awaitState { it.bookmarkedPages.isNotEmpty() }.bookmarkedPages)
    }

    @Test
    fun `marcar la pagina actual inserta el marcador con documento y pagina correctos`() {
        viewModel.loadDocument("1", context)
        viewModel.onPageChanged(3, 10)

        viewModel.toggleBookmarkCurrentPage()

        coVerify { pageBookmarkDao.insert(match { it.documentId == "1" && it.page == 3 }) }
        coVerify(exactly = 0) { pageBookmarkDao.delete(any(), any()) }
    }

    @Test
    fun `desmarcar una pagina ya marcada borra el marcador en vez de insertarlo`() {
        bookmarksFlow.value = listOf(PageBookmarkEntity(REAL_DOC_ID, 3, 0L))
        loadRealAndAwait(REAL_DOC_ID)
        awaitState { 3 in it.bookmarkedPages }
        viewModel.onPageChanged(3, 10)

        viewModel.toggleBookmarkCurrentPage()

        coVerify { pageBookmarkDao.delete(REAL_DOC_ID, 3) }
        coVerify(exactly = 0) { pageBookmarkDao.insert(any()) }
    }

    @Test
    fun `removeBookmark borra el marcador indicado`() {
        viewModel.loadDocument("1", context)

        viewModel.removeBookmark(6)

        coVerify { pageBookmarkDao.delete("1", 6) }
    }

    @Test
    fun `navegar a un marcador cierra la hoja y pide el salto`() {
        viewModel.showBookmarksSheet()
        assertTrue(viewModel.uiState.value.showBookmarksSheet)

        viewModel.navigateToBookmark(9)

        assertFalse(viewModel.uiState.value.showBookmarksSheet)
        assertEquals(9, viewModel.uiState.value.pendingPageJump)
        viewModel.dismissBookmarksSheet()
        assertFalse(viewModel.uiState.value.showBookmarksSheet)
    }

    // ── busqueda ───────────────────────────────────────────────────────────

    @Test
    fun `buscar publica paginas, indice 0 y posiciones de resaltado`() {
        loadRealAndAwait(REAL_DOC_ID)
        val rect = PdfMatchRect(1f, 2f, 3f, 4f)
        coEvery { searchPdfText(fakeUri, "hola") } returns
            listOf(PdfPageMatches(2, listOf(rect)), PdfPageMatches(5, listOf(rect, rect)))

        viewModel.searchInPdf("hola")

        val state = viewModel.uiState.value
        assertEquals(listOf(2, 5), state.pdfSearchMatches)
        assertEquals(0, state.pdfSearchIndex)
        assertEquals(listOf(rect), state.pdfSearchHighlights[2])
        assertEquals(2, state.pdfSearchHighlights[5]?.size)
    }

    @Test
    fun `buscar sin resultados deja indice -1`() {
        loadRealAndAwait(REAL_DOC_ID)
        coEvery { searchPdfText(any(), any()) } returns emptyList()

        viewModel.searchInPdf("nada")

        assertEquals(-1, viewModel.uiState.value.pdfSearchIndex)
        assertTrue(viewModel.uiState.value.pdfSearchMatches.isEmpty())
    }

    @Test
    fun `siguiente y anterior recorren las coincidencias de forma circular`() {
        loadRealAndAwait(REAL_DOC_ID)
        val rect = PdfMatchRect(0f, 0f, 1f, 1f)
        coEvery { searchPdfText(any(), any()) } returns
            listOf(PdfPageMatches(1, listOf(rect)), PdfPageMatches(3, listOf(rect)), PdfPageMatches(8, listOf(rect)))
        viewModel.searchInPdf("x")

        viewModel.previousPdfSearchResult()
        assertEquals(2, viewModel.uiState.value.pdfSearchIndex)
        viewModel.nextPdfSearchResult()
        assertEquals(0, viewModel.uiState.value.pdfSearchIndex)
        viewModel.nextPdfSearchResult()
        assertEquals(1, viewModel.uiState.value.pdfSearchIndex)
    }

    @Test
    fun `siguiente y anterior sin coincidencias no cambian el indice`() {
        viewModel.nextPdfSearchResult()
        viewModel.previousPdfSearchResult()

        assertEquals(-1, viewModel.uiState.value.pdfSearchIndex)
    }

    @Test
    fun `buscar sin archivo cargado limpia el estado y no llama al caso de uso`() {
        viewModel.searchInPdf("hola")

        assertTrue(viewModel.uiState.value.pdfSearchMatches.isEmpty())
        coVerify(exactly = 0) { searchPdfText(any(), any()) }
    }

    @Test
    fun `limpiar la busqueda durante una busqueda en vuelo no deja resultados viejos (regresion de carrera)`() {
        loadRealAndAwait(REAL_DOC_ID)
        val gate = CompletableDeferred<Unit>()
        coEvery { searchPdfText(any(), "lenta") } coAnswers {
            gate.await()
            listOf(PdfPageMatches(4, listOf(PdfMatchRect(0f, 0f, 1f, 1f))))
        }

        viewModel.searchInPdf("lenta")
        viewModel.clearPdfSearch()
        gate.complete(Unit)

        assertTrue(viewModel.uiState.value.pdfSearchMatches.isEmpty())
        assertEquals(-1, viewModel.uiState.value.pdfSearchIndex)
        assertTrue(viewModel.uiState.value.pdfSearchHighlights.isEmpty())
    }

    @Test
    fun `borrar el texto de busqueda durante una busqueda en vuelo tampoco repuebla resultados`() {
        loadRealAndAwait(REAL_DOC_ID)
        val gate = CompletableDeferred<Unit>()
        coEvery { searchPdfText(any(), "lenta") } coAnswers {
            gate.await()
            listOf(PdfPageMatches(4, listOf(PdfMatchRect(0f, 0f, 1f, 1f))))
        }

        viewModel.searchInPdf("lenta")
        viewModel.searchInPdf("")
        gate.complete(Unit)

        assertTrue(viewModel.uiState.value.pdfSearchMatches.isEmpty())
    }

    @Test
    fun `una busqueda nueva reemplaza a la anterior aunque la vieja termine despues`() {
        loadRealAndAwait(REAL_DOC_ID)
        val gate = CompletableDeferred<Unit>()
        val rect = PdfMatchRect(0f, 0f, 1f, 1f)
        coEvery { searchPdfText(any(), "vieja") } coAnswers {
            gate.await()
            listOf(PdfPageMatches(9, listOf(rect)))
        }
        coEvery { searchPdfText(any(), "nueva") } returns listOf(PdfPageMatches(1, listOf(rect)))

        viewModel.searchInPdf("vieja")
        viewModel.searchInPdf("nueva")
        gate.complete(Unit)

        assertEquals(listOf(1), viewModel.uiState.value.pdfSearchMatches)
    }

    // ── anotaciones ────────────────────────────────────────────────────────

    @Test
    fun `las anotaciones observadas se agrupan por pagina y se actualizan con el flow`() {
        annotationsFlow.value = listOf(annotation("a", 1), annotation("b", 1), annotation("c", 3))

        loadRealAndAwait(REAL_DOC_ID)

        val state = awaitState { it.annotations.size == 2 }
        assertEquals(2, state.annotations[1]?.size)
        assertEquals(1, state.annotations[3]?.size)

        annotationsFlow.value = emptyList()
        assertTrue(awaitState { it.annotations.isEmpty() }.annotations.isEmpty())
    }

    @Test
    fun `abrir y cerrar la barra sincroniza bandera y modo`() {
        viewModel.toggleAnnotationToolbar()
        viewModel.setAnnotationMode(AnnotationMode.HIGHLIGHT)
        assertTrue(viewModel.uiState.value.showAnnotationToolbar)
        assertEquals(AnnotationMode.HIGHLIGHT, viewModel.uiState.value.annotationMode)

        // Reabrir/cerrar: al cerrar el modo vuelve a NONE (zoom/pan liberados).
        viewModel.toggleAnnotationToolbar()
        assertFalse(viewModel.uiState.value.showAnnotationToolbar)
        assertEquals(AnnotationMode.NONE, viewModel.uiState.value.annotationMode)

        viewModel.toggleAnnotationToolbar()
        viewModel.setAnnotationMode(AnnotationMode.NOTE)
        viewModel.closeAnnotationToolbar()
        assertFalse(viewModel.uiState.value.showAnnotationToolbar)
        assertEquals(AnnotationMode.NONE, viewModel.uiState.value.annotationMode)
    }

    @Test
    fun `cambiar de modo descarta una nota pendiente`() {
        viewModel.requestAddNote(2, PdfRectPts(10f, 20f, 0f, 0f))
        assertNotNull(viewModel.uiState.value.pendingNoteAnchor)

        viewModel.setAnnotationMode(AnnotationMode.HIGHLIGHT)

        assertNull(viewModel.uiState.value.pendingNoteAnchor)
        assertNull(viewModel.uiState.value.pendingNotePage)
    }

    @Test
    fun `addHighlight inserta un resaltado con el color elegido y las coordenadas dadas`() {
        viewModel.loadDocument("1", context)
        viewModel.setHighlightColor(ANNOTATION_HIGHLIGHT_COLORS[2])
        val inserted = slot<AnnotationEntity>()
        coEvery { annotationDao.insert(capture(inserted)) } returns Unit

        viewModel.addHighlight(4, PdfRectPts(10f, 20f, 30f, 40f))

        val entity = inserted.captured
        assertEquals(AnnotationType.HIGHLIGHT, entity.type)
        assertEquals("1", entity.documentId)
        assertEquals(4, entity.page)
        assertEquals(ANNOTATION_HIGHLIGHT_COLORS[2], entity.color)
        assertEquals(10f, entity.xPts)
        assertEquals(20f, entity.yPts)
        assertEquals(30f, entity.widthPts)
        assertEquals(40f, entity.heightPts)
        assertEquals("", entity.text)
    }

    @Test
    fun `addHighlight sin documento cargado no inserta nada`() {
        viewModel.addHighlight(1, PdfRectPts(0f, 0f, 1f, 1f))

        coVerify(exactly = 0) { annotationDao.insert(any()) }
    }

    @Test
    fun `confirmNote inserta la nota recortada y acotada a MAX_NOTE_LENGTH y limpia el anclaje`() {
        viewModel.loadDocument("1", context)
        val inserted = slot<AnnotationEntity>()
        coEvery { annotationDao.insert(capture(inserted)) } returns Unit
        viewModel.requestAddNote(3, PdfRectPts(15f, 25f, 0f, 0f))

        viewModel.confirmNote("  " + "x".repeat(MAX_NOTE_LENGTH + 500) + "  ")

        val entity = inserted.captured
        assertEquals(AnnotationType.NOTE, entity.type)
        assertEquals(3, entity.page)
        assertEquals(15f, entity.xPts)
        assertEquals(25f, entity.yPts)
        assertEquals(0f, entity.widthPts)
        assertEquals(0f, entity.heightPts)
        assertEquals(MAX_NOTE_LENGTH, entity.text.length)
        assertNull(viewModel.uiState.value.pendingNoteAnchor)
        assertNull(viewModel.uiState.value.pendingNotePage)
    }

    @Test
    fun `confirmNote con texto en blanco cancela la nota pendiente sin insertar`() {
        viewModel.loadDocument("1", context)
        viewModel.requestAddNote(3, PdfRectPts(15f, 25f, 0f, 0f))

        viewModel.confirmNote("   ")

        assertNull(viewModel.uiState.value.pendingNoteAnchor)
        coVerify(exactly = 0) { annotationDao.insert(any()) }
    }

    @Test
    fun `confirmNote sin anclaje pendiente no inserta nada`() {
        viewModel.loadDocument("1", context)

        viewModel.confirmNote("texto")

        coVerify(exactly = 0) { annotationDao.insert(any()) }
    }

    @Test
    fun `cancelPendingNote descarta el anclaje`() {
        viewModel.requestAddNote(2, PdfRectPts(1f, 1f, 0f, 0f))

        viewModel.cancelPendingNote()

        assertNull(viewModel.uiState.value.pendingNoteAnchor)
        assertNull(viewModel.uiState.value.pendingNotePage)
    }

    @Test
    fun `ver y borrar una anotacion limpia viewingAnnotation y borra por id`() {
        val existing = annotation("a1", 1)
        viewModel.viewAnnotation(existing)
        assertSame(existing, viewModel.uiState.value.viewingAnnotation)

        viewModel.deleteAnnotation("a1")

        coVerify { annotationDao.delete("a1") }
        assertNull(viewModel.uiState.value.viewingAnnotation)

        viewModel.viewAnnotation(existing)
        viewModel.dismissAnnotationDetail()
        assertNull(viewModel.uiState.value.viewingAnnotation)
    }

    // ── compartir ──────────────────────────────────────────────────────────

    @Test
    fun `compartir con anotaciones abre el dialogo de eleccion en vez de compartir`() {
        loadRealAndAwait(REAL_DOC_ID)

        viewModel.shareDocument(context)

        assertTrue(viewModel.uiState.value.showShareChoiceDialog)
        assertNull(viewModel.uiState.value.shareError)
        viewModel.dismissShareChoiceDialog()
        assertFalse(viewModel.uiState.value.showShareChoiceDialog)
    }

    @Test
    fun `un fallo al lanzar el selector deja shareError transitorio y NO reemplaza el Visor con error`() {
        // En JVM puro Intent(...) lanza "not mocked": ejercita el catch real.
        viewModel.loadDocument("1", context)

        viewModel.shareOriginal(context)

        val state = viewModel.uiState.value
        assertEquals(str(R.string.pdf_tools_share_error), state.shareError)
        assertNull(state.error)
        assertNotNull(state.document)
        viewModel.dismissShareError()
        assertNull(viewModel.uiState.value.shareError)
    }

    @Test
    fun `shareWithAnnotations aplana todas las anotaciones y avisa si el aplanado falla`() {
        loadRealAndAwait(REAL_DOC_ID)
        val passed = slot<List<AnnotationEntity>>()
        coEvery { flattenUseCase(fakeUri, capture(passed)) } returns null

        viewModel.shareWithAnnotations(context)

        val state = viewModel.uiState.value
        assertEquals(listOf("existing"), passed.captured.map { it.id })
        assertEquals(str(R.string.pdf_tools_share_error), state.shareError)
        assertFalse(state.isFlatteningForShare)
        assertFalse(state.showShareChoiceDialog)
    }

    @Test
    fun `un aplanado exitoso pero fallo al abrir el selector limpia isFlatteningForShare y deja shareError`() {
        loadRealAndAwait(REAL_DOC_ID)
        coEvery { flattenUseCase(any(), any()) } returns File(tempDir, "anotado.pdf")

        viewModel.shareWithAnnotations(context)

        val state = viewModel.uiState.value
        assertFalse(state.isFlatteningForShare)
        assertEquals(str(R.string.pdf_tools_share_error), state.shareError)
        assertNull(state.error)
    }

    @Test
    fun `doble toque en compartir con anotaciones aplana una sola vez`() {
        loadRealAndAwait(REAL_DOC_ID)
        val gate = CompletableDeferred<Unit>()
        coEvery { flattenUseCase(any(), any()) } coAnswers {
            gate.await()
            null
        }

        viewModel.shareWithAnnotations(context)
        assertTrue(viewModel.uiState.value.isFlatteningForShare)
        viewModel.shareWithAnnotations(context)
        gate.complete(Unit)

        coVerify(exactly = 1) { flattenUseCase(any(), any()) }
        assertFalse(viewModel.uiState.value.isFlatteningForShare)
    }

    // ── contrasena ─────────────────────────────────────────────────────────

    @Test
    fun `un PDF con contrasena pide la contrasena en vez de publicarse como cargado`() {
        loadLockedAndAwait(LOCKED_DOC_ID)

        val state = viewModel.uiState.value
        assertTrue(state.requiresPassword)
        assertFalse(state.isLoading)
        assertNull(state.decryptedFile)
        assertNull(state.passwordError)
        assertFalse(state.isReadOnlyPreview)
        // Los archivos temporales de la deteccion no quedan en cacheDir.
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `contrasena incorrecta muestra el mensaje de reintento y no deja temporales`() {
        loadLockedAndAwait(LOCKED_DOC_ID)
        stubLockedStream()

        viewModel.unlockPdfWithPassword("clave-equivocada")
        val state = awaitState { !it.isLoading }

        assertEquals(str(R.string.pdf_pw_wrong_password_retry), state.passwordError)
        assertTrue(state.requiresPassword)
        assertNull(state.decryptedFile)
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `contrasena correcta publica el PDF desbloqueado, registra historial y observa marcadores`() {
        loadLockedAndAwait(LOCKED_DOC_ID)
        stubLockedStream()

        viewModel.unlockPdfWithPassword(PDF_PASSWORD)
        val state = awaitState { !it.isLoading }

        assertFalse(state.requiresPassword)
        assertNull(state.passwordError)
        assertEquals("application/pdf", state.mimeType)
        val decrypted = state.decryptedFile
        assertNotNull(decrypted)
        assertTrue(decrypted!!.exists())
        assertTrue(decrypted.length() > 100L)
        // Solo queda el PDF desbloqueado: el temporal cifrado se borro.
        assertEquals(listOf(decrypted.name), cacheDir.listFiles().orEmpty().map { it.name })
        coVerify(timeout = 5000) { documentHistoryDao.recordOpen(match { it.documentId == LOCKED_DOC_ID }) }
        verify(timeout = 5000) { annotationDao.observeByDocument(LOCKED_DOC_ID) }
        // Regresion: antes un PDF protegido nunca observaba marcadores ni notas.
        verify(timeout = 5000) { pageBookmarkDao.observeByDocument(LOCKED_DOC_ID) }
        verify(timeout = 5000) { noteDao.observeByDocument(LOCKED_DOC_ID) }
    }

    @Test
    fun `un PDF protegido dentro de secure_preview sigue siendo solo lectura tras desbloquearse`() {
        loadLockedAndAwait(LOCKED_PREVIEW_DOC_ID)
        assertTrue(viewModel.uiState.value.isReadOnlyPreview)
        stubLockedStream()

        viewModel.unlockPdfWithPassword(PDF_PASSWORD)
        awaitState { !it.isLoading && !it.requiresPassword && it.annotations.isNotEmpty() }

        // Regresion: antes se registraba en Recientes sin PIN y se habilitaba anotar.
        coVerify(exactly = 0) { documentHistoryDao.recordOpen(any()) }
        verify(exactly = 0) { pageBookmarkDao.observeByDocument(any()) }
        assertTrue(viewModel.uiState.value.isReadOnlyPreview)
        viewModel.toggleAnnotationToolbar()
        assertFalse(viewModel.uiState.value.showAnnotationToolbar)
    }

    @Test
    fun `si el origen ya no se puede leer el error es de lectura, no de contrasena`() {
        loadLockedAndAwait(LOCKED_DOC_ID)
        every { resolver.openInputStream(any()) } returns null

        viewModel.unlockPdfWithPassword(PDF_PASSWORD)
        val state = awaitState { !it.isLoading }

        assertEquals(str(R.string.pdf_pw_read_error), state.passwordError)
        assertTrue(state.requiresPassword)
    }

    @Test
    fun `cerrar el dialogo de contrasena limpia el estado y desbloquear despues es un no-op`() {
        loadLockedAndAwait(LOCKED_DOC_ID)

        viewModel.dismissPasswordDialog()
        viewModel.unlockPdfWithPassword(PDF_PASSWORD)

        val state = viewModel.uiState.value
        assertFalse(state.requiresPassword)
        assertFalse(state.isLoading)
        assertNull(state.passwordError)
        assertNull(state.decryptedFile)
    }

    // ── deteccion de contrasena: rutas absoluta / file:// (no solo content://) ──

    // isPdfPasswordProtected()/isRealUri() detectan una ruta absoluta real de
    // Android con `startsWith("/")` (esas rutas nunca llevan letra de unidad).
    // `File(...).absolutePath` en Windows devuelve `C:\...` (con backslash y
    // letra de unidad), así que no basta para simular el caso real -- se le
    // quita la unidad y se normaliza a "/", igual que en Linux (donde
    // absolutePath ya es "/..."). En Windows, `File("/Users/...")` sin letra
    // de unidad resuelve contra la unidad actual, que es la misma del
    // directorio temporal, así que sigue apuntando al archivo real.
    private fun File.asAndroidAbsolutePath(): String {
        val normalized = absolutePath.replace('\\', '/')
        val driveEnd = normalized.indexOf(":/")
        return if (driveEnd >= 0) normalized.substring(driveEnd + 1) else normalized
    }

    @Test
    fun `PDF protegido en ruta absoluta pide contrasena y error de lectura si desaparece el origen`() {
        // Cubre la rama `originalId.startsWith("/")` de isPdfPasswordProtected()
        // Y de unlockPdfWithPassword() -- las pruebas existentes solo ejercitan
        // el camino content:// (ver LOCKED_DOC_ID).
        val encryptedFile = File(tempDir, "locked_abs.pdf").apply { writeBytes(createEncryptedPdf()) }
        val absoluteId = encryptedFile.asAndroidAbsolutePath()

        viewModel.loadDocument(absoluteId, context)
        val loaded = awaitState { it.requiresPassword }
        assertEquals(absoluteId, loaded.document?.id)
        assertFalse(loaded.isLoading)

        encryptedFile.delete()
        viewModel.unlockPdfWithPassword(PDF_PASSWORD)
        val state = awaitState { !it.isLoading }

        assertEquals(str(R.string.pdf_pw_read_error), state.passwordError)
        assertTrue(state.requiresPassword)
    }

    @Test
    fun `un PDF protegido en ruta absoluta que no existe no bloquea la carga (no pide contrasena)`() {
        // Rama `if (!sourceFile.exists()) return false` de isPdfPasswordProtected():
        // un .pdf ausente no debe quedar marcado como protegido.
        val missingPath = File(tempDir, "no_existe.pdf").asAndroidAbsolutePath()

        viewModel.loadDocument(missingPath, context)
        val state = awaitState { !it.isLoading }

        assertFalse(state.requiresPassword)
        assertNotNull(state.document)
    }

    @Test
    fun `un PDF protegido via uri file tambien pide la contrasena, y si el origen desaparece el error es de lectura`() {
        // Cubre la rama `uri.scheme == "file"` de isPdfPasswordProtected() y
        // de unlockPdfWithPassword() -- forzamos mimeType via el resolver
        // porque el nombre resuelto para un docId "file://" no termina en
        // ".pdf" con los mocks de este test (ver resolveFileName).
        every { resolver.getType(any()) } returns "application/pdf"
        val encryptedFile = File(tempDir, "locked_file_uri.pdf").apply { writeBytes(createEncryptedPdf()) }
        every { fakeUri.scheme } returns "file"
        every { fakeUri.path } returns encryptedFile.absolutePath

        viewModel.loadDocument("file://${encryptedFile.absolutePath}", context)
        val loaded = awaitState { it.requiresPassword }
        assertFalse(loaded.isLoading)

        encryptedFile.delete()
        viewModel.unlockPdfWithPassword(PDF_PASSWORD)
        val state = awaitState { !it.isLoading }

        assertEquals(str(R.string.pdf_pw_read_error), state.passwordError)
    }

    @Test
    fun `un PDF valido sin cifrar via content no pide contrasena`() {
        // Completa el arbol de isPdfPasswordProtected(): hasta ahora solo se
        // probaba el PDF SI cifrado (LOCKED_DOC_ID) -- falta el "feliz" (PDF
        // real, valido, sin contrasena) que debe cargar normal.
        every { resolver.getType(any()) } returns "application/pdf"
        val plainBytes = createPlainPdf()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(plainBytes) }

        viewModel.loadDocument(LOCKED_DOC_ID, context)
        val state = awaitState { !it.isLoading }

        assertFalse(state.requiresPassword)
        assertNotNull(state.document)
        assertEquals("application/pdf", state.mimeType)
    }

    @Test
    fun `bug real corregido -- un pdf corrupto (sin firma PDF) ya no se confunde con protegido por contrasena`() {
        // Regresion del hallazgo documentado en la ronda 20
        // (docs/requirements/backlog-bugs-2026-09-20-v17.md): antes,
        // isPdfPasswordProtected() marcaba "pdf header" (el mensaje que lanza
        // iText para un archivo que NO empieza con la firma "%PDF-") como
        // sinonimo de "protegido con contrasena" -- un .pdf corrupto/no-PDF
        // quedaba pidiendo una contrasena para siempre, sin ninguna que
        // pudiera "desbloquearlo" (nunca estuvo cifrado). Corregido quitando
        // esa marca de la deteccion -- ahora sigue el camino normal de carga.
        every { resolver.getType(any()) } returns "application/pdf"
        val garbageBytes = "esto no es un pdf, no tiene la firma esperada".toByteArray()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(garbageBytes) }

        viewModel.loadDocument(LOCKED_DOC_ID, context)
        val state = awaitState { !it.isLoading }

        assertFalse(state.requiresPassword)
        assertNotNull(state.document)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun str(id: Int) = "str:$id"

    private fun annotation(
        id: String,
        page: Int,
    ) = AnnotationEntity(
        id = id,
        documentId = "doc",
        type = AnnotationType.HIGHLIGHT,
        page = page,
        xPts = 1f,
        yPts = 2f,
        widthPts = 3f,
        heightPts = 4f,
        color = ANNOTATION_HIGHLIGHT_COLORS.first(),
        text = "",
        createdAt = 0L,
    )

    private fun awaitState(predicate: (ViewerUiState) -> Boolean): ViewerUiState =
        runBlocking {
            withTimeout(10_000) { viewModel.uiState.first { predicate(it) } }
        }

    // Espera a que la carga (en Dispatchers.IO real) haya publicado el
    // documento Y suscrito los flows (anotaciones primero, notas al final).
    private fun loadRealAndAwait(documentId: String) {
        viewModel.loadDocument(documentId, context)
        awaitState {
            val notesReady = it.isReadOnlyPreview || it.linkedNotes.isNotEmpty()
            it.document != null && it.annotations.isNotEmpty() && notesReady
        }
    }

    private fun loadLockedAndAwait(documentId: String) {
        every { resolver.getType(any()) } returns "application/pdf"
        stubLockedStream()
        viewModel.loadDocument(documentId, context)
        awaitState { it.requiresPassword }
    }

    private fun stubLockedStream() {
        val bytes = createEncryptedPdf()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
    }

    private fun createEncryptedPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val props =
            WriterProperties().setStandardEncryption(
                PDF_PASSWORD.toByteArray(),
                "owner".toByteArray(),
                EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.ENCRYPTION_AES_128,
            )
        PdfDocument(PdfWriter(out, props)).use { it.addNewPage() }
        return out.toByteArray()
    }

    // PDF real, SIN cifrado -- para el "camino feliz" de isPdfPasswordProtected().
    private fun createPlainPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        PdfDocument(PdfWriter(out)).use { it.addNewPage() }
        return out.toByteArray()
    }
}
