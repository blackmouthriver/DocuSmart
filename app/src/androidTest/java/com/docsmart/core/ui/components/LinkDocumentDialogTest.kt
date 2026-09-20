package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.agenda.presentation.components.AgendaLinkDocumentDialog
import com.docsmart.features.agenda.presentation.components.esString
import com.docsmart.features.agenda.presentation.components.setContentEs
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.study.presentation.components.NoteLinkDocumentDialog
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: LinkDocumentDialog (componente compartido) y sus dos wrappers
 * finos, AgendaLinkDocumentDialog y NoteLinkDocumentDialog -- estados de
 * carga/vacío/lista, selección, desvincular y cancelar. Se usa un
 * AppLibraryPickerViewModel real sobre un DocumentRepository mockeado (sin
 * Hilt en la prueba).
 */
class LinkDocumentDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hangTag = "CI_HANG_LinkDocumentDialogTest"

    private fun document(
        id: String,
        name: String,
    ) = DocumentUiModel(id = id, name = name, type = DocumentType.PDF, size = "1.0 MB", date = "Hoy")

    private fun buildViewModel(documents: List<DocumentUiModel>): AppLibraryPickerViewModel {
        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadAllDocuments() } returns documents
        return AppLibraryPickerViewModel(repository)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntilOrDump(hangTag) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun conDocumentos_muestraLaListaYSeleccionaUno() {
        val viewModel = buildViewModel(listOf(document("1", "informe.pdf"), document("2", "contrato.pdf")))
        val selected = mutableListOf<DocumentUiModel>()
        composeRule.setContentEs {
            LinkDocumentDialog(
                currentDocumentId = null,
                title = "Titulo de prueba",
                emptyMessage = "Sin documentos",
                unlinkLabel = "Desvincular",
                onDismiss = {},
                onSelect = { selected.add(it) },
                onUnlink = {},
                viewModel = viewModel,
            )
        }

        waitForText("informe.pdf")
        composeRule.onNodeWithText("Titulo de prueba").assertIsDisplayed()
        composeRule.onNodeWithText("contrato.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("Sin documentos").assertDoesNotExist()
        // Sin documento vinculado no se ofrece "desvincular".
        composeRule.onNodeWithText("Desvincular").assertDoesNotExist()

        composeRule.onNodeWithText("contrato.pdf").performClick()

        assertEquals(listOf("2"), selected.map { it.id })
    }

    @Test
    fun biblioteca_vacia_muestraElMensajeVacio() {
        val viewModel = buildViewModel(emptyList())
        composeRule.setContentEs {
            LinkDocumentDialog(
                currentDocumentId = null,
                title = "Titulo de prueba",
                emptyMessage = "Sin documentos",
                unlinkLabel = "Desvincular",
                onDismiss = {},
                onSelect = {},
                onUnlink = {},
                viewModel = viewModel,
            )
        }

        waitForText("Sin documentos")
        composeRule.onNodeWithText("Sin documentos").assertIsDisplayed()
    }

    @Test
    fun mientrasCarga_noMuestraListaNiMensajeVacio_yLuegoApareceLaLista() {
        val pending = CompletableDeferred<List<DocumentUiModel>>()
        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadAllDocuments() } coAnswers { pending.await() }
        val viewModel = AppLibraryPickerViewModel(repository)
        composeRule.setContentEs {
            LinkDocumentDialog(
                currentDocumentId = null,
                title = "Titulo de prueba",
                emptyMessage = "Sin documentos",
                unlinkLabel = "Desvincular",
                onDismiss = {},
                onSelect = {},
                onUnlink = {},
                viewModel = viewModel,
            )
        }

        waitForText("Titulo de prueba")
        waitUntilLoading(viewModel)
        composeRule.onNodeWithText("Sin documentos").assertDoesNotExist()

        pending.complete(listOf(document("1", "cargado.pdf")))

        waitForText("cargado.pdf")
        composeRule.onNodeWithText("Sin documentos").assertDoesNotExist()
    }

    private fun waitUntilLoading(viewModel: AppLibraryPickerViewModel) {
        composeRule.waitUntilOrDump(hangTag) { viewModel.isLoading.value }
    }

    @Test
    fun conDocumentoVinculado_ofreceDesvincularYCancelar() {
        val viewModel = buildViewModel(listOf(document("1", "informe.pdf")))
        var unlinks = 0
        var dismisses = 0
        composeRule.setContentEs {
            LinkDocumentDialog(
                currentDocumentId = "1",
                title = "Titulo de prueba",
                emptyMessage = "Sin documentos",
                unlinkLabel = "Desvincular",
                onDismiss = { dismisses++ },
                onSelect = {},
                onUnlink = { unlinks++ },
                viewModel = viewModel,
            )
        }
        waitForText("informe.pdf")

        composeRule.onNodeWithText("Desvincular").performClick()
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()

        assertEquals(1, unlinks)
        assertEquals(1, dismisses)
    }

    @Test
    fun agendaLinkDocumentDialog_usaSusTextosYPropagaCallbacks() {
        val viewModel = buildViewModel(listOf(document("7", "agenda.pdf")))
        val selected = mutableListOf<String>()
        var unlinks = 0
        composeRule.setContentEs {
            AgendaLinkDocumentDialog(
                currentDocumentId = "7",
                onDismiss = {},
                onSelect = { selected.add(it.id) },
                onUnlink = { unlinks++ },
                viewModel = viewModel,
            )
        }

        waitForText("agenda.pdf")
        composeRule.onNodeWithText(esString(R.string.agenda_link_document_title)).assertIsDisplayed()
        composeRule.onNodeWithText("agenda.pdf").performClick()
        composeRule.onNodeWithText(esString(R.string.agenda_unlink_document)).performClick()

        assertEquals(listOf("7"), selected)
        assertEquals(1, unlinks)
    }

    @Test
    fun agendaLinkDocumentDialog_bibliotecaVacia_muestraSuMensaje() {
        val viewModel = buildViewModel(emptyList())
        composeRule.setContentEs {
            AgendaLinkDocumentDialog(
                currentDocumentId = null,
                onDismiss = {},
                onSelect = {},
                onUnlink = {},
                viewModel = viewModel,
            )
        }

        waitForText(esString(R.string.agenda_link_document_empty))
    }

    @Test
    fun noteLinkDocumentDialog_usaSusTextosYPropagaCallbacks() {
        val viewModel = buildViewModel(listOf(document("8", "nota.pdf")))
        val selected = mutableListOf<String>()
        var unlinks = 0
        composeRule.setContentEs {
            NoteLinkDocumentDialog(
                currentDocumentId = "8",
                onDismiss = {},
                onSelect = { selected.add(it.id) },
                onUnlink = { unlinks++ },
                viewModel = viewModel,
            )
        }

        waitForText("nota.pdf")
        composeRule.onNodeWithText(esString(R.string.note_link_document_title)).assertIsDisplayed()
        composeRule.onNodeWithText("nota.pdf").performClick()
        composeRule.onNodeWithText(esString(R.string.note_unlink_document)).performClick()

        assertEquals(listOf("8"), selected)
        assertEquals(1, unlinks)
    }

    @Test
    fun noteLinkDocumentDialog_bibliotecaVacia_muestraSuMensaje() {
        val viewModel = buildViewModel(emptyList())
        var dismissed = false
        composeRule.setContentEs {
            NoteLinkDocumentDialog(
                currentDocumentId = null,
                onDismiss = { dismissed = true },
                onSelect = {},
                onUnlink = {},
                viewModel = viewModel,
            )
        }

        waitForText(esString(R.string.note_link_document_empty))
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()

        assertTrue(dismissed)
    }
}
