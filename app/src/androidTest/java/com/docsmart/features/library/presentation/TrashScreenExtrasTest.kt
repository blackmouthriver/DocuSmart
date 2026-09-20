package com.docsmart.features.library.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.library.data.TrashedDocumentUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Papelera: estado vacío, textos de plazo y tamaño total, cancelar diálogos y errores de carga/restauración. */
class TrashScreenExtrasTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val strings: Context get() = forceLocale(target, "es-ES")
    private val day = 24 * 60 * 60 * 1000L

    private fun text(id: Int): String = strings.getString(id)

    private fun entry(
        id: String,
        deletedAt: Long,
        sizeBytes: Long = 2048L,
    ) = TrashedDocumentUiModel(
        DocumentUiModel(id, "$id.pdf", DocumentType.PDF, "2 KB", "Hoy", sizeBytes = sizeBytes),
        deletedAt,
    )

    private fun viewModelFor(repository: TrashRepository) = TrashViewModel(repository, mockk(relaxed = true), target)

    private fun setScreen(viewModel: TrashViewModel) {
        composeRule.setContent {
            val base = LocalContext.current
            val localized = remember(base) { forceLocale(base, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalResources provides localized.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) { TrashScreen(viewModel = viewModel) }
        }
    }

    private fun waitForText(value: String) {
        composeRule.waitUntilOrDump("CI_HANG_TrashScreenExtrasTest") {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sinDocumentosMuestraElEstadoVacioYNoOfreceBorrarTodo() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns emptyList()

        setScreen(viewModelFor(repository))

        waitForText(text(R.string.trash_empty_title))
        composeRule.onNodeWithText(text(R.string.trash_empty_body)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.trash_delete_all)).assertCountEquals(0)
    }

    @Test
    fun cadaTarjetaMuestraSuPlazoYElTamanoTotalSumaLosDocumentos() {
        val now = System.currentTimeMillis()
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns
            listOf(entry("reciente", now - day), entry("vencido", now - 40 * day))
        val viewModel = viewModelFor(repository)

        setScreen(viewModel)

        waitForText(text(R.string.trash_deletes_today))
        val remaining = viewModel.uiState.value.items.first { it.document.id == "reciente" }.daysRemaining
        composeRule.onNodeWithText(strings.getString(R.string.trash_days_remaining, remaining)).assertExists()
        // 2 KB + 2 KB = 4 KB.
        val total = strings.getString(R.string.trash_total_size, strings.getString(R.string.file_size_kb, 4L))
        composeRule.onNodeWithText(total).assertExists()
    }

    @Test
    fun cancelarLosDialogosNoBorraNada() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns listOf(entry("doc1", System.currentTimeMillis()))
        setScreen(viewModelFor(repository))
        waitForText(text(R.string.trash_delete_forever))

        composeRule.onNodeWithText(text(R.string.trash_delete_forever)).performClick()
        waitForText(text(R.string.trash_delete_forever_confirm_title))
        composeRule.onNodeWithText(text(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(text(R.string.trash_delete_forever_confirm_title)).assertCountEquals(0)

        composeRule.onNodeWithText(text(R.string.trash_delete_all)).performClick()
        waitForText(text(R.string.trash_delete_all_confirm_title))
        composeRule.onNodeWithText(text(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(text(R.string.trash_delete_all_confirm_title)).assertCountEquals(0)
        coVerify(exactly = 0) { repository.deleteForever(any()) }
        coVerify(exactly = 0) { repository.deleteAllForever(any()) }
    }

    @Test
    fun siNoSePuedeRestaurarSeAvisaYSeRecargaLaLista() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } returns listOf(entry("doc1", System.currentTimeMillis()))
        coEvery { repository.restoreFromTrash("doc1") } returns false
        val viewModel = viewModelFor(repository)
        setScreen(viewModel)
        waitForText(text(R.string.trash_restore))

        composeRule.onNodeWithText(text(R.string.trash_restore)).performClick()

        // Tras un intento fallido el ViewModel vuelve a cargar la lista: esperar la segunda lectura.
        composeRule.waitUntilOrDump("CI_HANG_TrashScreenExtrasTest") {
            runCatching { coVerify(atLeast = 2) { repository.loadTrashedDocuments() } }.isSuccess
        }
        coVerify { repository.restoreFromTrash("doc1") }
        composeRule.waitUntilOrDump("CI_HANG_TrashScreenExtrasTest") {
            viewModel.uiState.value.actionError == null
        }
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun siFallaLaCargaSeDetieneElSpinnerYSeMuestraLaPapeleraVacia() {
        val repository = mockk<TrashRepository>(relaxed = true)
        coEvery { repository.loadTrashedDocuments() } throws IllegalStateException("room")
        val viewModel = viewModelFor(repository)

        setScreen(viewModel)

        waitForText(text(R.string.trash_empty_title))
        assertEquals(emptyList<TrashedItemUi>(), viewModel.uiState.value.items)
    }
}
