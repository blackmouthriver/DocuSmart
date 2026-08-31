package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Flujo de prioridad Media #5 de RF-QA-01 (ver compose-ui-testing.md):
 * Visor -- renombrar/eliminar (RF-VIS-06). Ambas acciones viven en el menú
 * "⋮" (`viewer_more_options`) de `ViewerTopBar`.
 *
 * `DocumentRepository`/`TrashRepository` se mockean completos (igual que
 * en `LibraryScreenTest`/`TrashScreenTest`) en vez de usarse reales -- a
 * diferencia de `SearchPdfTextUseCase`/`RotatePdfUseCase` (una sola
 * dependencia de `Context`), `DocumentRepository` requiere 5 dependencias
 * propias (`FavoritesRepository`, `DocumentHistoryDao`, `TrashDao`,
 * `MediaDeletePermission`) solo para renombrar un archivo -- no es barato
 * de construir real para lo que aporta acá.
 */
class ViewerRenameDeleteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(
        documentRepository: DocumentRepository = mockk(relaxed = true),
        trashRepository: TrashRepository = mockk(relaxed = true)
    ): ViewerViewModel {
        val favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
        every { favoritesRepository.isFavorite(any()) } returns false
        every { favoritesRepository.getAlias(any()) } returns null

        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        return ViewerViewModel(
            favoritesRepository = favoritesRepository,
            searchPdfText       = SearchPdfTextUseCase(appContext), // real, no mock
            documentHistoryDao  = mockk<DocumentHistoryDao>(relaxed = true),
            documentRepository  = documentRepository,
            trashRepository     = trashRepository,
            adManager           = adManager
        )
    }

    // PDF real de 1 página -- mismo patrón de creación de archivo real que
    // ViewerSearchTest, vía iText7 (ya usado en producción, ver
    // StudyNotesExporter.kt).
    private fun createTestPdf(): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(appContext.cacheDir, "viewer_rename_delete_test_${System.currentTimeMillis()}.pdf")
        val document = Document(PdfDocument(PdfWriter(file)))
        document.add(Paragraph("Documento de prueba para renombrar/eliminar."))
        document.close()
        return file
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun renombrarDocumento_actualizaNombreEnBarraSuperior() {
        val pdfFile = createTestPdf()
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        // El nuevo id devuelto es el mismo path (misma carpeta) -- solo
        // interesa que ViewerViewModel.renameDocument() adopte el nombre
        // tal cual lo escribió el usuario en `document.name`, no depende
        // de un archivo físico renombrado de verdad para esta prueba.
        coEvery { documentRepository.renameDocument(any(), any()) } returns pdfFile.absolutePath
        val viewModel = buildViewModel(documentRepository = documentRepository)

        composeRule.setContent {
            ViewerScreen(documentId = pdfFile.absolutePath, onBack = {}, viewModel = viewModel)
        }
        waitForText(pdfFile.name)

        composeRule.onNodeWithContentDescription("Más opciones").performClick()
        composeRule.onNodeWithText("Renombrar").performClick()
        waitForText("Renombrar documento")

        composeRule.onNodeWithText("Nombre del archivo").performTextClearance()
        composeRule.onNodeWithText("Nombre del archivo").performTextInput("MiDocumentoRenombrado.pdf")
        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("MiDocumentoRenombrado.pdf")
        composeRule.onNodeWithText("MiDocumentoRenombrado.pdf").assertIsDisplayed()
    }

    @Test
    fun eliminarDocumento_muevePapeleraYCierraElVisor() {
        val pdfFile = createTestPdf()
        val trashRepository = mockk<TrashRepository>(relaxed = true)
        coEvery { trashRepository.moveToTrash(any()) } returns true
        val viewModel = buildViewModel(trashRepository = trashRepository)
        var backCalled = false

        composeRule.setContent {
            ViewerScreen(documentId = pdfFile.absolutePath, onBack = { backCalled = true }, viewModel = viewModel)
        }
        waitForText(pdfFile.name)

        composeRule.onNodeWithContentDescription("Más opciones").performClick()
        composeRule.onNodeWithText("Eliminar").performClick()
        waitForText("¿Eliminar este documento?")

        // Botón de confirmación del diálogo (general_delete = "Eliminar",
        // igual texto que el ítem de menú ya cerrado -- sin ambigüedad
        // porque el DropdownMenuItem se desmonta antes de abrir el diálogo).
        composeRule.onNodeWithText("Eliminar").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) { backCalled }
        assertTrue(backCalled)
    }
}
