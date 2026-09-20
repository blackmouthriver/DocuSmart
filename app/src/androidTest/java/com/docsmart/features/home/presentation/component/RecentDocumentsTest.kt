package com.docsmart.features.home.presentation.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
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
 * Ronda 18: sección "Recientes" de Home (cargando / vacío / lista / menú
 * contextual). "Compartir" no se prueba (lanza un chooser real del sistema).
 */
class RecentDocumentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val document =
        DocumentUiModel(id = "r1", name = "Contrato.pdf", type = DocumentType.PDF, size = "1.0 MB", date = "Hoy")

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

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

    private fun openMenu() {
        composeRule.onAllNodesWithContentDescription(string(R.string.viewer_more_options))[0].performClick()
        composeRule.waitForIdle()
    }

    private fun invokeMenuItem(resId: Int) {
        composeRule.onNodeWithText(string(resId)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun cargandoSinDocumentos_noMuestraElEstadoVacio() {
        setSection {
            RecentDocuments(
                documents = emptyList(),
                isLoading = true,
                onDocumentClick = {},
                onFavoriteClick = {},
                onSeeAllClick = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.home_recent)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.home_no_recent_title)).assertCountEquals(0)
    }

    @Test
    fun sinDocumentos_muestraElEstadoVacioYSuAccionAbreArchivo() {
        var openFile = false
        setSection {
            RecentDocuments(
                documents = emptyList(),
                onDocumentClick = {},
                onFavoriteClick = {},
                onSeeAllClick = {},
                onOpenFileClick = { openFile = true },
            )
        }

        composeRule.onNodeWithText(string(R.string.home_no_recent_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.home_no_recent_desc)).assertExists()
        composeRule.onNodeWithText(string(R.string.home_open_file_action)).performClick()
        composeRule.waitForIdle()

        assertEquals(true, openFile)
    }

    @Test
    fun tocarVerTodos_invocaElCallback() {
        var seeAll = false
        setSection {
            RecentDocuments(
                documents = emptyList(),
                onDocumentClick = {},
                onFavoriteClick = {},
                onSeeAllClick = { seeAll = true },
            )
        }

        composeRule.onNodeWithText(string(R.string.home_see_all)).performClick()
        composeRule.waitForIdle()

        assertEquals(true, seeAll)
    }

    @Test
    fun conDocumentos_aunCargandoMuestraLaListaYResponde() {
        val opened = mutableListOf<String>()
        val favorites = mutableListOf<String>()
        setSection {
            RecentDocuments(
                documents = listOf(document),
                isLoading = true,
                onDocumentClick = { opened += it.id },
                onFavoriteClick = { favorites += it },
                onSeeAllClick = {},
            )
        }

        composeRule.onNodeWithText(document.name).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithContentDescription(string(R.string.doc_item_add_favorite))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("r1"), opened)
        assertEquals(listOf("r1"), favorites)
    }

    @Test
    fun menuContextual_ejecutaLasAccionesConElDocumento() {
        val calls = mutableListOf<String>()
        setSection {
            RecentDocuments(
                documents = listOf(document),
                onDocumentClick = { calls += "open:${it.id}" },
                onFavoriteClick = { calls += "fav:$it" },
                onSeeAllClick = {},
                onConvertClick = { calls += "convert:${it.id}" },
                onCreateQrClick = { calls += "qr:${it.id}" },
                onMakeSearchableClick = { calls += "ocr:${it.id}" },
                onSignClick = { calls += "sign:${it.id}" },
                onMoveToSecureFolderClick = { calls += "secure:${it.id}" },
                onDeleteClick = { calls += "delete:$it" },
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
            openMenu()
            invokeMenuItem(itemRes)
        }

        assertEquals(
            listOf("open:r1", "fav:r1", "convert:r1", "qr:r1", "ocr:r1", "sign:r1", "secure:r1", "delete:r1"),
            calls,
        )
    }

    @Test
    fun menuContextual_renombrarConfirmaConLaExtensionOriginal() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            RecentDocuments(
                documents = listOf(document),
                onDocumentClick = {},
                onFavoriteClick = {},
                onSeeAllClick = {},
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenu()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Nuevo")
        composeRule.onNodeWithText(string(R.string.viewer_rename)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("r1" to "Nuevo.pdf"), renames)
    }

    @Test
    fun cancelarElRenombrado_cierraElDialogoSinCambios() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            RecentDocuments(
                documents = listOf(document),
                onDocumentClick = {},
                onFavoriteClick = {},
                onSeeAllClick = {},
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenu()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNodeWithText(string(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<String, String>>(), renames)
        composeRule.onAllNodesWithText(string(R.string.doc_item_rename_title)).assertCountEquals(0)
    }
}
