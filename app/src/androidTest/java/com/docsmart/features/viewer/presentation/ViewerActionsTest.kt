package com.docsmart.features.viewer.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.docsmart.R
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.features.pdftools.presentation.components.RecordingContext
import com.docsmart.features.pdftools.presentation.components.esContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: barra superior y sus acciones (favorito, compartir, renombrar, eliminar, atajos
 * del menú, notas vinculadas, volver) y el diálogo "con anotaciones / original". El documento
 * "1" es el de demostración (sin archivo); los reales van en cacheDir.
 */
class ViewerActionsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val files = ViewerTestFiles()
    private var harness = ViewerHarness()

    @After
    fun tearDown() {
        files.deleteAll()
    }

    private val mockName = "Contrato_Servicios_2024.pdf"

    private fun openMock(
        recording: RecordingContext? = null,
        onBack: () -> Unit = {},
        extra: (String) -> Unit = {},
    ) {
        composeRule.setViewerContent(overrideContext = recording) {
            ViewerScreen(
                documentId = "1",
                onBack = onBack,
                onConvertClick = { extra("convert:${it.id}") },
                onCreateQrClick = { extra("qr:${it.id}") },
                onMakeSearchableClick = { extra("ocr:${it.id}") },
                onSignClick = { extra("sign:${it.id}") },
                onMoveToSecureFolderClick = { extra("secure:${it.id}") },
                viewModel = harness.viewModel,
            )
        }
        composeRule.waitForText(mockName)
    }

    private fun openFile(
        file: File,
        recording: RecordingContext? = null,
        onBack: () -> Unit = {},
    ) {
        composeRule.setViewerContent(overrideContext = recording) {
            ViewerScreen(documentId = file.absolutePath, onBack = onBack, viewModel = harness.viewModel)
        }
        composeRule.waitForText(file.name)
    }

    private fun clickMenuItem(text: String) {
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_more_options)).performClick()
        composeRule.onNodeWithText(text).performClick()
    }

    private fun buttonWithText(text: String) = composeRule.onNode(hasText(text) and hasClickAction())

    @Suppress("DEPRECATION")
    private fun Intent.chooserTarget(): Intent = getParcelableExtra(Intent.EXTRA_INTENT)!!

    private fun annotation(id: String) =
        AnnotationEntity(
            id = id,
            documentId = "doc",
            type = AnnotationType.HIGHLIGHT,
            page = 1,
            xPts = 10f,
            yPts = 10f,
            widthPts = 50f,
            heightPts = 20f,
            color = ANNOTATION_HIGHLIGHT_COLORS.first(),
            text = "",
            createdAt = 0L,
        )

    // ── Atajos del menú "⋮" ─────────────────────────────────────────────────

    @Test
    fun menu_cadaAtajoEntregaElDocumentoAbierto() {
        val calls = mutableListOf<String>()
        openMock(extra = { calls += it })

        clickMenuItem(vs(R.string.viewer_convert))
        clickMenuItem(vs(R.string.viewer_create_qr))
        clickMenuItem(vs(R.string.doc_item_make_searchable))
        clickMenuItem(vs(R.string.doc_item_sign))
        clickMenuItem(vs(R.string.doc_item_move_to_secure_folder))

        assertEquals(listOf("convert:1", "qr:1", "ocr:1", "sign:1", "secure:1"), calls)
    }

    @Test
    fun volver_llamaAOnBack() {
        var backCalls = 0
        openMock(onBack = { backCalls++ })

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_back)).performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun buscar_abreYCierraLaBarraDeBusqueda() {
        openMock()
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.waitForViewerNode(hasSetTextAction())
        composeRule.onNode(hasSetTextAction()).performTextInput("algo")

        composeRule.onNodeWithContentDescription(vs(R.string.general_close)).performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // ── Favorito ────────────────────────────────────────────────────────────

    @Test
    fun favorito_conArchivoRealUsaLaRutaComoIdYRespetaElAlias() {
        val file = files.text(".txt", "contenido")
        every { harness.favorites.getAlias(file.absolutePath) } returns "Mi alias.txt"
        composeRule.setViewerContent {
            ViewerScreen(documentId = file.absolutePath, onBack = {}, viewModel = harness.viewModel)
        }
        composeRule.waitForText("Mi alias.txt")

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_favorite_content_desc)).performClick()
        coVerify(timeout = 5_000) { harness.favorites.toggleFavorite(file.absolutePath) }
        composeRule.waitForState { harness.viewModel.uiState.value.isFavorite }
    }

    // ── Compartir ───────────────────────────────────────────────────────────

    @Test
    fun compartir_sinAnotacionesLanzaElSelectorConElAsuntoYElTipo() {
        val recording = RecordingContext(esContext())
        openMock(recording = recording)

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForState { recording.startedIntents.isNotEmpty() }

        val chooser = recording.startedIntents.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.chooserTarget()
        assertEquals("application/pdf", target.type)
        assertEquals(mockName, target.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun compartir_conAnotacionesPreguntaYElOriginalSeComparteTalCual() {
        harness.annotations.value = listOf(annotation("h1"))
        val recording = RecordingContext(esContext())
        // En cacheDir/scanner/ para que FileProvider pueda servirlo.
        openFile(files.pdf(sub = "scanner"), recording)
        composeRule.waitForState { harness.viewModel.uiState.value.annotations.isNotEmpty() }

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForText(vs(R.string.viewer_share_choice_title))
        composeRule.onNodeWithText(vs(R.string.viewer_share_choice_body)).assertExists()

        buttonWithText(vs(R.string.viewer_share_choice_original)).performClick()
        composeRule.waitForState { recording.startedIntents.isNotEmpty() }
        assertEquals(Intent.ACTION_CHOOSER, recording.startedIntents.single().action)
    }

    @Test
    fun compartir_conAnotacionesAplanaUnaCopiaYLaComparte() {
        harness.annotations.value = listOf(annotation("h1"))
        val flattened = files.sharedFile()
        coEvery { harness.flatten(any(), any()) } returns flattened
        val recording = RecordingContext(esContext())
        openFile(files.pdf(sub = "scanner"), recording)
        composeRule.waitForState { harness.viewModel.uiState.value.annotations.isNotEmpty() }

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForText(vs(R.string.viewer_share_choice_title))
        buttonWithText(vs(R.string.viewer_share_choice_with_annotations)).performClick()

        composeRule.waitForState { recording.startedIntents.isNotEmpty() }
        assertEquals(Intent.ACTION_CHOOSER, recording.startedIntents.single().action)
        assertEquals("application/pdf", recording.startedIntents.single().chooserTarget().type)
        composeRule.waitForState { !harness.viewModel.uiState.value.isFlatteningForShare }
    }

    @Test
    fun compartir_siAplanarFallaAvisaYNoLanzaNada() {
        harness.annotations.value = listOf(annotation("h1"))
        coEvery { harness.flatten(any(), any()) } returns null
        val recording = RecordingContext(esContext())
        openFile(files.pdf(sub = "scanner"), recording)
        composeRule.waitForState { harness.viewModel.uiState.value.annotations.isNotEmpty() }

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForText(vs(R.string.viewer_share_choice_title))
        buttonWithText(vs(R.string.viewer_share_choice_with_annotations)).performClick()

        // El aviso es un Toast transitorio: la pantalla lo consume y limpia el estado.
        coVerify(timeout = 5_000) { harness.flatten(any(), any()) }
        composeRule.waitForState { harness.viewModel.uiState.value.shareError == null }
        composeRule.waitForState { !harness.viewModel.uiState.value.isFlatteningForShare }
        assertTrue(recording.startedIntents.isEmpty())
    }

    @Test
    fun compartir_cancelarElDialogoNoComparteNada() {
        harness.annotations.value = listOf(annotation("h1"))
        val recording = RecordingContext(esContext())
        openFile(files.pdf(sub = "scanner"), recording)
        composeRule.waitForState { harness.viewModel.uiState.value.annotations.isNotEmpty() }

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForText(vs(R.string.viewer_share_choice_title))
        composeRule.onNodeWithContentDescription(vs(R.string.general_cancel)).performClick()

        composeRule.waitForState { !harness.viewModel.uiState.value.showShareChoiceDialog }
        assertTrue(recording.startedIntents.isEmpty())
    }

    @Test
    fun compartir_unArchivoQueFileProviderNoServirAvisaConUnToast() {
        // En la raíz de cacheDir (fuera de file_provider_paths.xml): getUriForFile falla.
        val recording = RecordingContext(esContext())
        openFile(files.text(".txt", "contenido"), recording)

        composeRule.onNodeWithContentDescription(vs(R.string.general_share)).performClick()
        composeRule.waitForState { harness.viewModel.uiState.value.shareError == null }
        composeRule.waitForIdle()
        assertTrue(recording.startedIntents.isEmpty())
    }

    // ── Renombrar ───────────────────────────────────────────────────────────

    @Test
    fun renombrar_validaVacioCancelaYConfirmaElNombreNuevo() {
        coEvery { harness.documentRepository.renameDocument(any(), any()) } returns "1"
        openMock()

        // Cancelar no renombra.
        clickMenuItem(vs(R.string.viewer_rename))
        composeRule.waitForText(vs(R.string.viewer_rename_title))
        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()
        composeRule.waitForState { !harness.viewModel.uiState.value.showRenameDialog }

        // Nombre vacío: error y Guardar deshabilitado.
        clickMenuItem(vs(R.string.viewer_rename))
        composeRule.waitForText(vs(R.string.viewer_rename_title))
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.waitForText(vs(R.string.viewer_rename_empty_error))
        composeRule.onNodeWithText(vs(R.string.general_save)).assertIsNotEnabled()

        // Nombre válido: se guarda recortado y la barra superior lo muestra.
        composeRule.onNode(hasSetTextAction()).performTextInput("  Nuevo nombre.pdf  ")
        buttonWithText(vs(R.string.general_save)).performClick()
        coVerify(timeout = 5_000) { harness.documentRepository.renameDocument("1", "Nuevo nombre.pdf") }
        composeRule.waitForText("Nuevo nombre.pdf")
    }

    // ── Eliminar ────────────────────────────────────────────────────────────

    @Test
    fun eliminar_cancelarCierraElDialogoSinMoverALaPapelera() {
        openMock()
        clickMenuItem(vs(R.string.viewer_delete))
        composeRule.waitForText(vs(R.string.viewer_delete_confirm_title))
        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()

        composeRule.waitForState { !harness.viewModel.uiState.value.showDeleteConfirm }
        coVerify(exactly = 0) { harness.trashRepository.moveToTrash(any()) }
    }

    @Test
    fun eliminar_siLaPapeleraFallaAvisaYSeQuedaEnElVisor() {
        coEvery { harness.trashRepository.moveToTrash(any()) } returns false
        var backCalls = 0
        openMock(onBack = { backCalls++ })

        clickMenuItem(vs(R.string.viewer_delete))
        composeRule.waitForText(vs(R.string.viewer_delete_confirm_title))
        buttonWithText(vs(R.string.general_delete)).performClick()

        coVerify(timeout = 5_000) { harness.trashRepository.moveToTrash("1") }
        // deleteError se muestra como Toast y la pantalla lo consume.
        composeRule.waitForState { harness.viewModel.uiState.value.deleteError == null }
        composeRule.waitForState { !harness.viewModel.uiState.value.showDeleteConfirm }
        assertEquals(0, backCalls)
    }

    // ── Notas vinculadas (Modo Estudio) ─────────────────────────────────────

    @Test
    fun notasVinculadas_apareceEnElMenuYListaSusNotas() {
        val note = NoteEntity("n1", "Titulo de la nota", "Contenido de la nota", 0L, "doc")
        harness.notes.value = listOf(NoteWithImages(note, emptyList()))
        openFile(files.text(".txt", "contenido"))

        clickMenuItem(vs(R.string.viewer_linked_notes, 1))
        composeRule.waitForText(vs(R.string.viewer_linked_notes_title))
        composeRule.onNodeWithText("Titulo de la nota").assertExists()
        composeRule.onNodeWithText("Contenido de la nota").assertExists()

        composeRule.onNodeWithText(vs(R.string.general_close)).performClick()
        composeRule.waitForState { !harness.viewModel.uiState.value.showLinkedNotesDialog }
    }

    @Test
    fun sinNotasVinculadas_elMenuNoTieneElAtajo() {
        openFile(files.text(".txt", "contenido"))

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_more_options)).performClick()
        composeRule.onNodeWithText(vs(R.string.viewer_rename)).assertExists()
        composeRule.onNodeWithText(vs(R.string.viewer_linked_notes, 1)).assertDoesNotExist()
    }

    // ── Anuncios ────────────────────────────────────────────────────────────

    @Test
    fun usuarioFree_conElSdkSinIniciarNoMuestraBannerYElVisorFunciona() {
        harness = ViewerHarness(premium = false)
        openFile(files.text(".txt", "contenido gratuito"))

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_favorite_content_desc)).assertExists()
        composeRule.onNode(hasContentDescription(vs(R.string.viewer_more_options))).assertExists()
    }
}
