package com.docsmart.features.library.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: lista de documentos de Biblioteca (contador, estado vacío y
 * acciones del menú contextual). Las acciones del menú (ModalBottomSheet a
 * media altura) se invocan por semántica; "Compartir" no se prueba porque
 * lanzaría un chooser real del sistema.
 */
class DocumentListSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val first =
        DocumentUiModel(id = "d1", name = "Contrato.pdf", type = DocumentType.PDF, size = "1.0 MB", date = "Hoy")
    private val second =
        DocumentUiModel(id = "d2", name = "Informe.pdf", type = DocumentType.PDF, size = "2.0 MB", date = "Ayer")

    private val localizedContext
        get() = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String =
        // Sin argumentos no se formatea (algunos textos llevan un "%" literal).
        if (args.isEmpty()) localizedContext.getString(resId) else localizedContext.getString(resId, *args)

    private fun setSection(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalResources provides localized.resources,
            ) { MaterialTheme { content() } }
        }
    }

    private fun openMenuOfFirstDocument() {
        composeRule.onAllNodesWithContentDescription(string(R.string.viewer_more_options))[0].performClick()
        composeRule.waitForIdle()
    }

    private fun invokeMenuItem(resId: Int) {
        composeRule.onNodeWithText(string(resId)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun conDocumentos_muestraElContadorPluralYLasFilas() {
        setSection {
            DocumentListSection(
                documents = listOf(first, second),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "",
            )
        }

        val count =
            localizedContext.resources.getQuantityString(R.plurals.library_document_count_plural, 2, 2)
        composeRule.onNodeWithText(count).assertExists()
        composeRule.onNodeWithText(first.name).assertExists()
        composeRule.onNodeWithText(second.name).assertExists()
    }

    @Test
    fun tocarUnaFilaYSuFavorito_invocaLosCallbacksConElDocumento() {
        val opened = mutableListOf<String>()
        val favorites = mutableListOf<String>()
        setSection {
            DocumentListSection(
                documents = listOf(first, second),
                onDocumentClick = { opened += it.id },
                onFavoriteClick = { favorites += it },
                searchQuery = "",
            )
        }

        composeRule.onNodeWithText(second.name).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithContentDescription(string(R.string.doc_item_add_favorite))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("d2"), opened)
        assertEquals(listOf("d1"), favorites)
    }

    @Test
    fun sinDocumentosNiBusqueda_muestraElEstadoVacioDeLaCategoria() {
        setSection {
            DocumentListSection(
                documents = emptyList(),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "",
            )
        }

        composeRule.onNodeWithText(string(R.string.library_no_results_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.library_empty_category)).assertExists()
    }

    @Test
    fun sinResultadosParaUnaBusqueda_muestraElContadorYElMensajeConLaConsulta() {
        setSection {
            DocumentListSection(
                documents = emptyList(),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "zzz",
            )
        }

        composeRule.onNodeWithText(string(R.string.library_search_results_count, 0, "zzz")).assertExists()
        composeRule.onNodeWithText(string(R.string.library_no_results_for_query, "zzz")).assertExists()
    }

    @Test
    fun menuContextual_renombrarConfirmaConLaExtensionOriginal() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            DocumentListSection(
                documents = listOf(first),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "",
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenuOfFirstDocument()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Nuevo")
        composeRule.onNodeWithText(string(R.string.viewer_rename)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("d1" to "Nuevo.pdf"), renames)
    }

    @Test
    fun renombrarConNombreVacio_deshabilitaConfirmarYMuestraElError() {
        setSection {
            DocumentListSection(
                documents = listOf(first),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "",
                onRenameClick = { _, _ -> },
            )
        }

        openMenuOfFirstDocument()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("  ")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.viewer_rename_empty_error)).assertExists()
        composeRule.onNodeWithText(string(R.string.viewer_rename)).assertIsNotEnabled()
    }

    @Test
    fun cancelarElRenombrado_cierraElDialogoSinCambios() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            DocumentListSection(
                documents = listOf(first),
                onDocumentClick = {},
                onFavoriteClick = {},
                searchQuery = "",
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenuOfFirstDocument()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNodeWithText(string(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<String, String>>(), renames)
        composeRule.onAllNodesWithText(string(R.string.doc_item_rename_title)).assertCountEquals(0)
    }

    @Test
    fun menuContextual_ejecutaLasDemasAccionesConElDocumento() {
        val calls = mutableListOf<String>()
        setSection {
            DocumentListSection(
                documents = listOf(first),
                onDocumentClick = { calls += "open:${it.id}" },
                onFavoriteClick = { calls += "fav:$it" },
                searchQuery = "",
                onDeleteClick = { calls += "delete:$it" },
                onConvertClick = { calls += "convert:${it.id}" },
                onCreateQrClick = { calls += "qr:${it.id}" },
                onMakeSearchableClick = { calls += "ocr:${it.id}" },
                onSignClick = { calls += "sign:${it.id}" },
                onMoveToSecureFolderClick = { calls += "secure:${it.id}" },
            )
        }

        listOf(
            R.string.qr_open_document,
            R.string.doc_item_add_favorite,
            R.string.viewer_convert,
            R.string.viewer_create_qr,
            R.string.doc_item_make_searchable,
            R.string.doc_item_sign,
            R.string.doc_item_move_to_secure_folder,
            R.string.general_delete,
        ).forEach { itemRes ->
            openMenuOfFirstDocument()
            invokeMenuItem(itemRes)
        }

        assertEquals(
            listOf(
                "open:d1",
                "fav:d1",
                "convert:d1",
                "qr:d1",
                "ocr:d1",
                "sign:d1",
                "secure:d1",
                "delete:d1",
            ),
            calls,
        )
    }
}
