package com.docsmart.features.viewer.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
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
import com.docsmart.R
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.features.viewer.presentation.ANNOTATION_HIGHLIGHT_COLORS
import com.docsmart.features.viewer.presentation.AnnotationMode
import com.docsmart.features.viewer.presentation.MAX_NOTE_LENGTH
import com.docsmart.features.viewer.presentation.setViewerContent
import com.docsmart.features.viewer.presentation.vs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 20: componentes del Visor aislados (barra superior/inferior, panel de marcadores,
 * barra y diálogos de anotación, diálogos de renombrar/eliminar/notas vinculadas). Reciben
 * estado y lambdas por parámetro: cada clic se verifica contra un registro de llamadas.
 */
class ViewerComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val log = mutableListOf<String>()

    private fun button(text: String) = composeRule.onNode(hasText(text) and hasClickAction())

    private fun annotation(type: AnnotationType) =
        AnnotationEntity(
            id = "a1",
            documentId = "doc",
            type = type,
            page = 1,
            xPts = 10f,
            yPts = 10f,
            widthPts = 30f,
            heightPts = 10f,
            color = ANNOTATION_HIGHLIGHT_COLORS.first(),
            text = "Texto de la nota",
            createdAt = 0L,
        )

    @Composable
    private fun TopBarUnderTest(
        isPdf: Boolean = true,
        isFavorite: Boolean = false,
        visible: Boolean = true,
        isAnnotating: Boolean = false,
        readOnly: Boolean = false,
        linkedNotes: Int = 0,
    ) {
        ViewerTopBar(
            fileName = "informe.pdf",
            isFavorite = isFavorite,
            visible = visible,
            onBackClick = { log += "back" },
            onFavoriteClick = { log += "favorite" },
            onShareClick = { log += "share" },
            onSearchClick = { log += "search" },
            onConvertClick = { log += "convert" },
            onCreateQrClick = { log += "qr" },
            isPdf = isPdf,
            onMakeSearchableClick = { log += "ocr" },
            onSignClick = { log += "sign" },
            onMoveToSecureFolderClick = { log += "secure" },
            onRenameClick = { log += "rename" },
            onDeleteClick = { log += "delete" },
            isAnnotating = isAnnotating,
            onAnnotateClick = { log += "annotate" },
            isReadOnlyPreview = readOnly,
            linkedNotesCount = linkedNotes,
            onOpenLinkedNotesClick = { log += "notes" },
        )
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_more_options)).performClick()
    }

    // ── ViewerTopBar ────────────────────────────────────────────────────────

    @Test
    fun topBar_cadaBotonYCadaItemDelMenuDisparaSuAccion() {
        composeRule.setViewerContent { TopBarUnderTest(linkedNotes = 2) }
        composeRule.onNodeWithText("informe.pdf").assertExists()

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_back)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_favorite_content_desc)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).performClick()
        assertEquals(listOf("back", "search", "favorite", "share", "annotate"), log)

        log.clear()
        val menuActions =
            listOf(
                vs(R.string.viewer_convert) to "convert",
                vs(R.string.viewer_create_qr) to "qr",
                vs(R.string.doc_item_make_searchable) to "ocr",
                vs(R.string.doc_item_sign) to "sign",
                vs(R.string.doc_item_move_to_secure_folder) to "secure",
                vs(R.string.viewer_linked_notes, 2) to "notes",
                vs(R.string.viewer_rename) to "rename",
                vs(R.string.viewer_delete) to "delete",
            )
        menuActions.forEach { (label, _) ->
            openMenu()
            composeRule.onNodeWithText(label).performClick()
        }
        assertEquals(menuActions.map { it.second }, log)
    }

    @Test
    fun topBar_favoritoYAnotandoSeDibujanSinPerderSusBotones() {
        composeRule.setViewerContent { TopBarUnderTest(isFavorite = true, isAnnotating = true) }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_favorite_content_desc)).assertExists()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).assertExists()
    }

    @Test
    fun topBar_sinPdfNoOfreceAnotarNiAccionesDePdf() {
        composeRule.setViewerContent { TopBarUnderTest(isPdf = false) }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).assertDoesNotExist()

        openMenu()
        composeRule.onNodeWithText(vs(R.string.viewer_convert)).assertExists()
        composeRule.onNodeWithText(vs(R.string.doc_item_make_searchable)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.doc_item_sign)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_linked_notes, 1)).assertDoesNotExist()
    }

    @Test
    fun topBar_soloLecturaOcultaAnotarYTodoElMenuSalvoLasNotas() {
        composeRule.setViewerContent { TopBarUnderTest(readOnly = true, linkedNotes = 1) }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_annotate_content_desc)).assertDoesNotExist()

        openMenu()
        composeRule.onNodeWithText(vs(R.string.viewer_convert)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.doc_item_move_to_secure_folder)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_rename)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_delete)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_linked_notes, 1)).assertExists()
    }

    @Test
    fun topBar_ocultaNoMuestraNada() {
        composeRule.setViewerContent { TopBarUnderTest(visible = false) }
        composeRule.onNodeWithText("informe.pdf").assertDoesNotExist()
    }

    // ── ViewerBottomBar ─────────────────────────────────────────────────────

    @Test
    fun bottomBar_muestraLaPaginaYLosDosBotonesDeMarcadores() {
        composeRule.setViewerContent {
            ViewerBottomBar(
                currentPage = 2,
                totalPages = 5,
                visible = true,
                isCurrentPageBookmarked = false,
                onToggleBookmark = { log += "toggle" },
                onShowBookmarks = { log += "show" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_bottom_bar_page_format, 3, 5)).assertExists()

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmark_add)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmarks_title)).performClick()
        assertEquals(listOf("toggle", "show"), log)
    }

    @Test
    fun bottomBar_paginaMarcadaOfreceQuitarElMarcador() {
        composeRule.setViewerContent {
            ViewerBottomBar(currentPage = 0, totalPages = 1, visible = true, isCurrentPageBookmarked = true)
        }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmark_remove)).assertExists()
    }

    @Test
    fun bottomBar_sinPaginasOOcultaNoSeDibuja() {
        composeRule.setViewerContent {
            ViewerBottomBar(currentPage = 0, totalPages = 0, visible = true)
        }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_bookmarks_title)).assertDoesNotExist()
    }

    // ── ViewerBookmarksSheet ────────────────────────────────────────────────

    @Test
    fun marcadores_sinPaginasMuestraElMensajeVacio() {
        composeRule.setViewerContent {
            ViewerBookmarksSheet(bookmarkedPages = emptyList(), onNavigate = {}, onRemove = {}, onDismiss = {})
        }
        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_title)).assertExists()
        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_empty)).assertExists()
    }

    @Test
    fun marcadores_listaLasPaginasIrAUnaYEliminarOtra() {
        composeRule.setViewerContent {
            ViewerBookmarksSheet(
                bookmarkedPages = listOf(0, 2),
                onNavigate = { log += "go:$it" },
                onRemove = { log += "rm:$it" },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_page_format, 1)).assertExists()

        composeRule.onNodeWithText(vs(R.string.viewer_bookmarks_page_format, 3)).performClick()
        composeRule.onAllNodesWithContentDescription(vs(R.string.general_delete)).onFirst().performClick()
        assertEquals(listOf("go:2", "rm:0"), log)
    }

    // ── ViewerAnnotationToolbar ─────────────────────────────────────────────

    @Test
    fun barraDeAnotacion_coloresNotaYListoDisparanSusCallbacks() {
        composeRule.setViewerContent {
            ViewerAnnotationToolbar(
                mode = AnnotationMode.HIGHLIGHT,
                selectedColor = ANNOTATION_HIGHLIGHT_COLORS[2],
                highlightColors = ANNOTATION_HIGHLIGHT_COLORS,
                onColorSelected = { log += "color:${ANNOTATION_HIGHLIGHT_COLORS.indexOf(it)}" },
                onNoteSelected = { log += "note" },
                onDone = { log += "done" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_toolbar_label)).assertExists()

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_yellow)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_green)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_pink)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_blue)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_chip)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_done)).performClick()

        assertEquals(listOf("color:0", "color:1", "color:2", "color:3", "note", "done"), log)
    }

    @Test
    fun barraDeAnotacion_conModoNotaYSinColoresSeguiSiendoUsable() {
        composeRule.setViewerContent {
            ViewerAnnotationToolbar(
                mode = AnnotationMode.NOTE,
                selectedColor = ANNOTATION_HIGHLIGHT_COLORS.first(),
                highlightColors = ANNOTATION_HIGHLIGHT_COLORS.take(2),
                onColorSelected = {},
                onNoteSelected = { log += "note" },
                onDone = { log += "done" },
            )
        }
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_highlight_color_pink)).assertDoesNotExist()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_done)).performClick()
        assertEquals(listOf("done"), log)
    }

    // ── ViewerNoteInputDialog ───────────────────────────────────────────────

    @Test
    fun notaNueva_guardarSeHabilitaConTextoYEntregaElContenido() {
        composeRule.setViewerContent {
            ViewerNoteInputDialog(onConfirm = { log += "save:$it" }, onDismiss = { log += "dismiss" })
        }
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_dialog_title)).assertExists()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_char_count, 0, MAX_NOTE_LENGTH)).assertExists()
        composeRule.onNodeWithText(vs(R.string.general_save)).assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("Hola")
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_note_char_count, 4, MAX_NOTE_LENGTH)).assertExists()
        button(vs(R.string.general_save)).performClick()
        assertEquals(listOf("save:Hola"), log)
    }

    @Test
    fun notaNueva_unTextoMasLargoQueElLimiteSeRecortaEnVezDeDescartarse() {
        composeRule.setViewerContent {
            ViewerNoteInputDialog(onConfirm = {}, onDismiss = {})
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("x".repeat(MAX_NOTE_LENGTH + 100))

        composeRule.onNodeWithText(
            vs(R.string.viewer_annotate_note_char_count, MAX_NOTE_LENGTH, MAX_NOTE_LENGTH),
        ).assertExists()
    }

    @Test
    fun notaNueva_cancelarLlamaAOnDismiss() {
        composeRule.setViewerContent {
            ViewerNoteInputDialog(onConfirm = { log += "save" }, onDismiss = { log += "dismiss" })
        }
        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()
        assertEquals(listOf("dismiss"), log)
    }

    // ── ViewerAnnotationDetailDialog ────────────────────────────────────────

    @Test
    fun detalleDeNota_muestraElTextoYEliminarPideConfirmacion() {
        composeRule.setViewerContent {
            ViewerAnnotationDetailDialog(
                annotation = annotation(AnnotationType.NOTE),
                onDelete = { log += "delete" },
                onDismiss = { log += "dismiss" },
            )
        }
        composeRule.onNodeWithText("Texto de la nota").assertExists()

        button(vs(R.string.general_delete)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_delete_confirm_note_body)).assertExists()
        assertTrue(log.isEmpty())

        button(vs(R.string.general_delete)).performClick()
        assertEquals(listOf("delete"), log)
    }

    @Test
    fun detalleDeResaltado_muestraElAvisoYCerrarLlamaAOnDismiss() {
        composeRule.setViewerContent {
            ViewerAnnotationDetailDialog(
                annotation = annotation(AnnotationType.HIGHLIGHT),
                onDelete = { log += "delete" },
                onDismiss = { log += "dismiss" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_highlight_detail_body)).assertExists()

        // Confirmación de eliminar de un resaltado, y volver atrás con Cancelar.
        button(vs(R.string.general_delete)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_annotate_delete_confirm_highlight_body)).assertExists()
        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()

        composeRule.onNodeWithText(vs(R.string.general_close)).performClick()
        assertEquals(listOf("dismiss"), log)
    }

    // ── ViewerShareChoiceDialog ─────────────────────────────────────────────

    @Test
    fun compartirConAnotaciones_ofreceLasDosOpcionesYCancelar() {
        composeRule.setViewerContent {
            ViewerShareChoiceDialog(
                isFlattening = false,
                onShareWithAnnotations = { log += "with" },
                onShareOriginal = { log += "original" },
                onDismiss = { log += "dismiss" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_share_choice_body)).assertExists()

        button(vs(R.string.viewer_share_choice_with_annotations)).performClick()
        button(vs(R.string.viewer_share_choice_original)).performClick()
        composeRule.onNodeWithContentDescription(vs(R.string.general_cancel)).performClick()
        assertEquals(listOf("with", "original", "dismiss"), log)
    }

    @Test
    fun compartirConAnotaciones_mientrasPreparaDeshabilitaTodo() {
        composeRule.setViewerContent {
            ViewerShareChoiceDialog(
                isFlattening = true,
                onShareWithAnnotations = { log += "with" },
                onShareOriginal = { log += "original" },
                onDismiss = { log += "dismiss" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_share_choice_preparing)).assertExists()
        composeRule.onNodeWithText(vs(R.string.viewer_share_choice_with_annotations)).assertIsNotEnabled()
        composeRule.onNodeWithText(vs(R.string.viewer_share_choice_original)).assertIsNotEnabled()
        assertTrue(log.isEmpty())
    }

    // ── ViewerDocumentDialogs ───────────────────────────────────────────────

    @Test
    fun renombrar_guardaElNombreRecortadoYNoPermiteVacio() {
        composeRule.setViewerContent {
            ViewerRenameDialog(currentName = "viejo.pdf", onConfirm = { log += "ok:$it" }, onDismiss = { log += "no" })
        }
        composeRule.onNodeWithText(vs(R.string.viewer_rename_title)).assertExists()

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNodeWithText(vs(R.string.viewer_rename_empty_error)).assertExists()
        composeRule.onNodeWithText(vs(R.string.general_save)).assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("  nuevo.pdf ")
        button(vs(R.string.general_save)).performClick()
        assertEquals(listOf("ok:nuevo.pdf"), log)
    }

    @Test
    fun eliminarDocumento_confirmarYCancelar() {
        composeRule.setViewerContent {
            ViewerDeleteConfirmDialog(
                fileName = "informe.pdf",
                onConfirm = { log += "yes" },
                onDismiss = { log += "no" },
            )
        }
        composeRule.onNodeWithText(vs(R.string.viewer_delete_confirm_title)).assertExists()

        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()
        button(vs(R.string.general_delete)).performClick()
        assertEquals(listOf("no", "yes"), log)
    }

    @Test
    fun notasVinculadas_listaTituloYTextoYSeCierra() {
        val notes =
            listOf(
                NoteEntity("n1", "Primera nota", "Contenido uno", 0L, "doc"),
                NoteEntity("n2", "Segunda nota", "Contenido dos", 0L, "doc"),
            )
        composeRule.setViewerContent {
            ViewerLinkedNotesDialog(notes = notes, onDismiss = { log += "close" })
        }
        composeRule.onNodeWithText("Primera nota").assertExists()
        composeRule.onNodeWithText("Contenido dos").assertExists()

        composeRule.onNodeWithText(vs(R.string.general_close)).performClick()
        assertEquals(listOf("close"), log)
    }
}
