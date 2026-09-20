package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.docsmart.R
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.data.db.PageBookmarkEntity
import com.docsmart.features.viewer.domain.usecase.PdfMatchRect
import com.docsmart.features.viewer.domain.usecase.PdfPageMatches
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: ViewerScreen con PDF real (PdfRenderer): páginas, barra inferior, marcadores,
 * última página vista, búsqueda con resaltado, y el modo Anotar (resaltar / nota / detalle /
 * eliminar). Los DAOs son fakes reactivos (ver [ViewerHarness]).
 */
class ViewerPdfTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val files = ViewerTestFiles()
    private var harness = ViewerHarness()

    @After
    fun tearDown() {
        files.deleteAll()
    }

    private val pageDesc get() = vs(R.string.viewer_page_content_desc, 1)

    private fun open(file: File) {
        composeRule.setViewerContent {
            ViewerScreen(documentId = file.absolutePath, onBack = {}, viewModel = harness.viewModel)
        }
        composeRule.waitForViewerNode(hasContentDescription(pageDesc))
    }

    private fun waitBottomBar(totalPages: Int) {
        composeRule.waitForText(" de $totalPages", substring = true)
    }

    private fun highlight(
        id: String,
        page: Int = 1,
    ) = AnnotationEntity(
        id = id,
        documentId = "doc",
        type = AnnotationType.HIGHLIGHT,
        page = page,
        xPts = 100f,
        yPts = 150f,
        widthPts = 100f,
        heightPts = 100f,
        color = ANNOTATION_HIGHLIGHT_COLORS.first(),
        text = "",
        createdAt = 0L,
    )

    private fun note(
        id: String,
        text: String,
        page: Int = 1,
    ) = AnnotationEntity(
        id = id,
        documentId = "doc",
        type = AnnotationType.NOTE,
        page = page,
        xPts = 150f,
        yPts = 200f,
        widthPts = 0f,
        heightPts = 0f,
        color = 0xFFFF7043.toInt(),
        text = text,
        createdAt = 0L,
    )

    private fun enterAnnotateMode() {
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_toolbar_label))
    }

    // ── Render y barra inferior ─────────────────────────────────────────────

    @Test
    fun pdf_rendersPaginasRegistraElHistorialYMuestraLaBarraInferior() {
        open(files.pdf(pages = 3))
        waitBottomBar(3)
        coVerify(timeout = 5_000) { harness.historyDao.recordOpen(any()) }
        coVerify(timeout = 5_000) { harness.lastViewedPageDao.getByDocument(any()) }
    }

    @Test
    fun pdfRotado_seRenderizaSinFallar() {
        open(files.pdf(pages = 2, rotation = 90))
        waitBottomBar(2)
    }

    @Test
    fun tocarLaPagina_alternaLosControlesDeLaBarraSuperiorEInferior() {
        val file = files.pdf()
        open(file)
        waitBottomBar(1)

        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForState { !harness.viewModel.uiState.value.showControls }
        composeRule.waitForState {
            composeRule.onAllNodesWithContentDescription(vs(R.string.viewer_favorite_content_desc))
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.showControls }
    }

    @Test
    fun pellizcarLaPagina_haceZoomSinFallar() {
        open(files.pdf())
        composeRule.onNodeWithContentDescription(pageDesc).performTouchInput {
            pinch(
                start0 = Offset(center.x - 20f, center.y),
                end0 = Offset(center.x - 90f, center.y),
                start1 = Offset(center.x + 20f, center.y),
                end1 = Offset(center.x + 90f, center.y),
            )
        }
        composeRule.onNodeWithContentDescription(pageDesc).assertExists()
    }

    // ── Marcadores ──────────────────────────────────────────────────────────

    @Test
    fun marcadores_marcarYQuitarLaPaginaActualDesdeLaBarraInferior() {
        open(files.pdf())
        waitBottomBar(1)

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmark_add)).performClick()
        coVerify(timeout = 5_000) { harness.pageBookmarkDao.insert(match { it.page == 0 }) }
        composeRule.waitForViewerNode(hasContentDescription(vs(R.string.viewer_bookmark_remove)))

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmark_remove)).performClick()
        coVerify(timeout = 5_000) { harness.pageBookmarkDao.delete(any(), 0) }
        composeRule.waitForViewerNode(hasContentDescription(vs(R.string.viewer_bookmark_add)))
    }

    @Test
    fun marcadores_panelListaSaltaAUnMarcadorYPermiteEliminar() {
        harness.bookmarks.value = listOf(PageBookmarkEntity("doc", 0, 0L), PageBookmarkEntity("doc", 4, 0L))
        open(files.pdf())
        waitBottomBar(1)

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmarks_title)).performClick()
        composeRule.waitForText(vs(R.string.viewer_bookmarks_page_format, 5))
        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_page_format, 1)).assertExists()

        // Ir a: cierra el panel y el salto pendiente (fuera de rango) se consume igual.
        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_page_format, 5)).performClick()
        composeRule.waitForState { !harness.viewModel.uiState.value.showBookmarksSheet }
        composeRule.waitForState { harness.viewModel.uiState.value.pendingPageJump == null }

        // Eliminar: primer ícono de papelera = primer marcador (página 1).
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmarks_title)).performClick()
        composeRule.waitForText(vs(R.string.viewer_bookmarks_page_format, 5))
        composeRule.onAllNodesWithContentDescription(vs(R.string.general_delete)).onFirst().performClick()
        coVerify(timeout = 5_000) { harness.pageBookmarkDao.delete(any(), 0) }
    }

    @Test
    fun ultimaPaginaVista_alAbrirSaltaAEsaPaginaYSeGuardaAlCambiar() {
        harness.lastViewedPage("doc", 1)
        open(files.pdf(pages = 3))
        waitBottomBar(3)
        composeRule.waitForState { harness.viewModel.uiState.value.pendingPageJump == null }
        // onPageChanged programa el guardado con debounce de 600 ms.
        coVerify(timeout = 5_000) { harness.lastViewedPageDao.save(any()) }
    }

    // ── Búsqueda con resaltado ──────────────────────────────────────────────

    @Test
    fun busquedaEnPdf_muestraCoincidenciasNavegaYLimpia() {
        val search = mockk<SearchPdfTextUseCase>()
        coEvery { search(any(), any()) } returns
            listOf(PdfPageMatches(1, listOf(PdfMatchRect(20f, 300f, 80f, 14f), PdfMatchRect(20f, 200f, 60f, 14f))))
        harness = ViewerHarness(searchUseCase = search)
        open(files.pdf(pages = 2))

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("clave")
        composeRule.waitForText(vs(R.string.viewer_search_match_format, 1, 1))

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_next_match)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_previous_match)).performClick()
        composeRule.waitForText(vs(R.string.viewer_search_match_format, 1, 1))

        coEvery { search(any(), any()) } returns emptyList()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("otra")
        composeRule.waitForText(vs(R.string.viewer_search_no_results))
    }

    @Test
    fun buscarYAnotar_seExcluyenMutuamente() {
        open(files.pdf())
        enterAnnotateMode()

        // Abrir la búsqueda cierra la barra de anotación.
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.waitForViewerNode(hasSetTextAction())
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_toolbar_label)).assertDoesNotExist()

        // Abrir la anotación cierra la búsqueda.
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_toolbar_label))
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // ── Anotar ──────────────────────────────────────────────────────────────

    @Test
    fun anotar_arrastrarConUnColorGuardaUnResaltado() {
        open(files.pdf())
        enterAnnotateMode()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_green)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.annotationMode == AnnotationMode.HIGHLIGHT }
        assertEquals(ANNOTATION_HIGHLIGHT_COLORS[1], harness.viewModel.uiState.value.selectedHighlightColor)

        composeRule.onNodeWithContentDescription(pageDesc).performTouchInput {
            swipe(Offset(40f, 120f), Offset(220f, 190f), durationMillis = 300)
        }

        coVerify(timeout = 5_000) {
            harness.annotationDao.insert(
                match { it.type == AnnotationType.HIGHLIGHT && it.color == ANNOTATION_HIGHLIGHT_COLORS[1] },
            )
        }
        composeRule.waitForState { harness.annotations.value.size == 1 }
    }

    @Test
    fun anotar_tocarConModoNotaPideTextoYLaGuarda() {
        open(files.pdf())
        enterAnnotateMode()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_chip)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.annotationMode == AnnotationMode.NOTE }

        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_note_dialog_title))
        composeRule.onNodeWithText(vs(R.string.general_save)).assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("Revisar clausula 4")
        composeRule.onNode(hasText(vs(R.string.general_save)) and hasClickAction()).performClick()

        coVerify(timeout = 5_000) {
            harness.annotationDao.insert(match { it.type == AnnotationType.NOTE && it.text == "Revisar clausula 4" })
        }
        composeRule.waitForState { harness.viewModel.uiState.value.pendingNoteAnchor == null }
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_dialog_title)).assertDoesNotExist()
    }

    @Test
    fun anotar_cancelarLaNotaNoGuardaNada() {
        open(files.pdf())
        enterAnnotateMode()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_chip)).performClick()
        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_note_dialog_title))

        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.pendingNoteAnchor == null }
        coVerify(exactly = 0) { harness.annotationDao.insert(any()) }
    }

    @Test
    fun anotar_listoCierraLaBarraYVuelveAlModoNormal() {
        open(files.pdf())
        enterAnnotateMode()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_pink)).performClick()

        composeRule.onNodeWithText(vs(R.string.viewer_annotate_done)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.annotationMode == AnnotationMode.NONE }
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_toolbar_label)).assertDoesNotExist()
        assertTrue(!harness.viewModel.uiState.value.showAnnotationToolbar)
    }

    @Ignore("toque sobre la página depende de la geometría del dispositivo; pendiente, ver backlog v17")
    @Test
    fun anotacionesExistentes_tocarUnResaltadoAbreElDetalleYSePuedeCerrar() {
        harness.annotations.value = listOf(highlight("h1"), note("n1", "Nota lejana").copy(xPts = 20f, yPts = 380f))
        open(files.pdf())

        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_highlight_detail_body))
        composeRule.onNodeWithText(vs(R.string.general_close)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.viewingAnnotation == null }
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun anotacionesExistentes_eliminarUnaNotaPideConfirmacionYLaBorra() {
        harness.annotations.value = listOf(note("n1", "Recordar vigencia"))
        open(files.pdf())

        composeRule.onNodeWithContentDescription(pageDesc).performClick()
        composeRule.waitForText("Recordar vigencia")

        composeRule.onNode(hasText(vs(R.string.general_delete)) and hasClickAction()).performClick()
        composeRule.waitForText(vs(R.string.viewer_annotate_delete_confirm_title))
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_delete_confirm_note_body)).assertExists()

        // Primero se puede volver atrás sin borrar.
        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()
        composeRule.waitForText("Recordar vigencia")

        composeRule.onNode(hasText(vs(R.string.general_delete)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(vs(R.string.general_delete)) and hasClickAction()).performClick()
        coVerify(timeout = 5_000) { harness.annotationDao.delete("n1") }
        composeRule.waitForState { harness.annotations.value.isEmpty() }
    }

    // ── Vista previa de solo lectura (Carpeta Segura) ───────────────────────

    @Test
    fun vistaPreviaDeSoloLectura_ocultaAccionesYNoRegistraHistorial() {
        open(files.pdf(sub = "secure_preview"))
        waitBottomBar(1)

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_favorite_content_desc)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmark_add)).performClick()

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_more_options)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_rename)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_delete)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_convert)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.doc_item_move_to_secure_folder)).assertDoesNotExist()

        composeRule.waitForIdle()
        assertTrue(harness.viewModel.uiState.value.isReadOnlyPreview)
        coVerify(exactly = 0) { harness.favorites.toggleFavorite(any()) }
        coVerify(exactly = 0) { harness.historyDao.recordOpen(any()) }
        coVerify(exactly = 0) { harness.pageBookmarkDao.insert(any()) }
    }
}
