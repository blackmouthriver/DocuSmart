package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DocumentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: selector de origen del archivo (dispositivo / biblioteca). El
 * `AppLibraryPickerViewModel` se arma a mano con un `DocumentRepository`
 * mockeado (sin Hilt), y se pasa como parámetro.
 */
class FileSourcePickerDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pdf =
        DocumentUiModel(id = "d1", name = "Contrato.pdf", type = DocumentType.PDF, size = "1.0 MB", date = "Hoy")
    private val image =
        DocumentUiModel(id = "d2", name = "Foto.jpg", type = DocumentType.IMAGE, size = "2.0 MB", date = "Hoy")

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun buildViewModel(
        documents: List<DocumentUiModel>,
        neverLoads: Boolean = false,
    ): AppLibraryPickerViewModel {
        val repository = mockk<DocumentRepository>(relaxed = true)
        if (neverLoads) {
            coEvery { repository.loadAllDocuments() } coAnswers { awaitCancellation() }
        } else {
            coEvery { repository.loadAllDocuments() } returns documents
        }
        return AppLibraryPickerViewModel(repository)
    }

    private fun setDialog(
        viewModel: AppLibraryPickerViewModel,
        onDismiss: () -> Unit = {},
        onChooseFromDevice: () -> Unit = {},
        onChooseDocument: (DocumentUiModel) -> Unit = {},
        filter: (DocumentUiModel) -> Boolean = { true },
    ) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localizedContext, LocalResources provides localizedContext.resources) {
                MaterialTheme {
                    FileSourcePickerDialog(
                        title = "Elegir PDF",
                        onDismiss = onDismiss,
                        onChooseFromDevice = onChooseFromDevice,
                        onChooseDocument = onChooseDocument,
                        filter = filter,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun conDocumentos_muestraOpcionesYLaListaDeLaBiblioteca() {
        setDialog(buildViewModel(listOf(pdf, image)))
        waitForText(pdf.name)

        composeRule.onNodeWithText("Elegir PDF").assertExists()
        composeRule.onNodeWithText(string(R.string.filepicker_source_question)).assertExists()
        composeRule.onNodeWithText(string(R.string.filepicker_from_device)).assertExists()
        composeRule.onNodeWithText(string(R.string.filepicker_from_library)).assertExists()
        composeRule.onNodeWithText(image.name).assertExists()
    }

    @Test
    fun tocarUnDocumentoDeLaBiblioteca_invocaOnChooseDocument() {
        val chosen = mutableListOf<DocumentUiModel>()
        setDialog(buildViewModel(listOf(pdf, image)), onChooseDocument = { chosen += it })
        waitForText(pdf.name)

        composeRule.onNodeWithText(pdf.name).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(pdf), chosen)
    }

    @Test
    fun elFiltro_ocultaLosDocumentosQueNoCumplen() {
        setDialog(buildViewModel(listOf(pdf, image)), filter = { it.type == DocumentType.PDF })
        waitForText(pdf.name)

        composeRule.onAllNodesWithText(image.name).assertCountEquals(0)
    }

    @Test
    fun sinDocumentos_muestraElMensajeDeBibliotecaVacia() {
        setDialog(buildViewModel(emptyList()))

        waitForText(string(R.string.filepicker_no_library_files))
        composeRule.onNodeWithText(string(R.string.filepicker_no_library_files)).assertExists()
    }

    @Test
    fun mientrasCarga_noMuestraNiListaNiMensajeDeVacio() {
        val viewModel = buildViewModel(emptyList(), neverLoads = true)
        setDialog(viewModel)

        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.isLoading.value }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(string(R.string.filepicker_no_library_files)).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.filepicker_from_library)).assertExists()
    }

    @Test
    fun tocarDesdeElDispositivo_invocaOnChooseFromDevice() {
        var fromDevice = false
        setDialog(buildViewModel(emptyList()), onChooseFromDevice = { fromDevice = true })

        composeRule.onNodeWithText(string(R.string.filepicker_from_device)).performClick()
        composeRule.waitForIdle()

        assertEquals(true, fromDevice)
    }

    @Test
    fun tocarCancelar_invocaOnDismiss() {
        var dismissed = false
        setDialog(buildViewModel(emptyList()), onDismiss = { dismissed = true })

        composeRule.onNodeWithText(string(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(true, dismissed)
    }
}
