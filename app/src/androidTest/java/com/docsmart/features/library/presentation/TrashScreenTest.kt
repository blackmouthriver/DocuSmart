package com.docsmart.features.library.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.library.data.TrashedDocumentUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Alta #9 de RF-QA-01 (ver compose-ui-testing.md):
 * Papelera -- restaurar, eliminar uno, "Borrar todo" (RF-VIS-07, el mismo
 * flujo donde se corrigió antes un bug real de resurrección de archivos en
 * `TrashRepository.deleteForever()`).
 *
 * `TrashRepository` se mockea completo -- `TrashViewModel` solo depende de
 * él (no de sus 5 sub-dependencias), así que no hace falta construir
 * `DocumentRepository`/`TrashDao`/etc. reales para probar la UI. No se
 * ejercen los flujos de `NeedsPermission` (diálogo de sistema de
 * `MediaStore.createDeleteRequest`) -- se stubean los resultados como ya
 * confirmados (`Deleted`/`Done`) porque ese diálogo es un proceso externo
 * que Compose UI Testing no puede conducir, mismo criterio ya aplicado en
 * ConverterScreenTest para el picker de archivos.
 */
class TrashScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun documentFixture(id: String, name: String = "$id.pdf") = DocumentUiModel(
        id   = id,
        name = name,
        type = DocumentType.PDF,
        size = "1.2 MB",
        date = "Hoy"
    )

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Restaurar"/"Borrar todo"/etc. vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // TrashScreen usa rememberLauncherForActivityResult() para el
            // IntentSender del borrado con permiso -- mismo motivo que
            // ConverterScreenTest/SecurityScreenTest para re-proveer estos
            // dos apuntando a la Activity real.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tocarRestaurar_llamaARestoreFromTrashConElIdCorrecto() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns listOf(
            TrashedDocumentUiModel(documentFixture("doc1"), System.currentTimeMillis())
        )
        val viewModel = TrashViewModel(repository)

        setContentWithLocale { TrashScreen(viewModel = viewModel) }
        waitForText("Restaurar")

        composeRule.onNodeWithText("Restaurar").performClick()
        composeRule.waitForIdle()

        coVerify { repository.restoreFromTrash("doc1") }
    }

    @Test
    fun eliminarUno_confirmarDialogoLlamaADeleteForever() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns listOf(
            TrashedDocumentUiModel(documentFixture("doc1"), System.currentTimeMillis())
        )
        coEvery { repository.deleteForever("doc1") } returns DocumentRepository.DeleteOutcome.Deleted
        val viewModel = TrashViewModel(repository)

        setContentWithLocale { TrashScreen(viewModel = viewModel) }
        waitForText("Eliminar ahora")

        composeRule.onNodeWithText("Eliminar ahora").performClick()
        waitForText("¿Eliminar definitivamente?")

        // Botón de confirmación del diálogo (general_delete = "Eliminar",
        // distinto del "Eliminar ahora" de la tarjeta que ya se tocó).
        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.waitForIdle()

        coVerify { repository.deleteForever("doc1") }
    }

    @Test
    fun borrarTodo_confirmarDialogoLlamaADeleteAllForever() {
        val repository = mockk<TrashRepository>(relaxed = true)
        // deletedAt distintos a propósito -- loadTrashedDocuments() ordena
        // por fecha descendente, así que el orden de `ids` acá debe
        // coincidir con el orden real que arma el ViewModel al leer
        // uiState.items, o el coVerify de abajo no encuentra la llamada
        // (las listas se comparan por igualdad, orden incluido).
        coEvery { repository.loadTrashedDocuments() } returns listOf(
            TrashedDocumentUiModel(documentFixture("doc1"), deletedAt = 2_000L),
            TrashedDocumentUiModel(documentFixture("doc2"), deletedAt = 1_000L)
        )
        val ids = listOf("doc1", "doc2")
        coEvery { repository.deleteAllForever(ids) } returns TrashRepository.BulkDeleteOutcome.Done
        val viewModel = TrashViewModel(repository)

        setContentWithLocale { TrashScreen(viewModel = viewModel) }
        waitForText("Borrar todo")

        composeRule.onNodeWithText("Borrar todo").performClick()
        waitForText("¿Vaciar la papelera?")

        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.waitForIdle()

        coVerify { repository.deleteAllForever(ids) }
    }
}
