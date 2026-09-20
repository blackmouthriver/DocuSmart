package com.docsmart.features.library.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: sección de Favoritos de Biblioteca (carrusel de tarjetas + menú
 * contextual + renombrar). "Compartir" no se prueba (lanza un chooser real).
 */
class FavoritesSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val first =
        DocumentUiModel(
            id = "f1",
            name = "Contrato.pdf",
            type = DocumentType.PDF,
            size = "1.0 MB",
            date = "Hoy",
            isFavorite = true,
        )
    private val second =
        DocumentUiModel(
            id = "f2",
            name = "Informe.pdf",
            type = DocumentType.PDF,
            size = "2.0 MB",
            date = "Ayer",
            isFavorite = true,
        )

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun setSection(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localized) { MaterialTheme { content() } }
        }
    }

    private fun openMenuOfFirstCard() {
        composeRule.onAllNodesWithContentDescription(string(R.string.viewer_more_options))[0].performClick()
        composeRule.waitForIdle()
    }

    private fun invokeMenuItem(resId: Int) {
        composeRule.onNodeWithText(string(resId)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun sinFavoritos_noDibujaNada() {
        setSection { FavoritesSection(favorites = emptyList(), onDocumentClick = {}) }

        composeRule.onAllNodesWithText(string(R.string.library_favorites_title)).assertCountEquals(0)
    }

    @Test
    fun conFavoritos_muestraElTituloLasTarjetasYAbreAlTocarlas() {
        val opened = mutableListOf<String>()
        setSection { FavoritesSection(favorites = listOf(first, second), onDocumentClick = { opened += it.id }) }

        composeRule.onNodeWithText(string(R.string.library_favorites_title)).assertExists()
        composeRule.onNodeWithText(first.name).assertExists()
        composeRule.onNodeWithText(second.size).assertExists()

        composeRule.onNodeWithText(second.name).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("f2"), opened)
    }

    @Test
    fun pulsacionLargaEnLaTarjeta_abreElMenuContextual() {
        setSection { FavoritesSection(favorites = listOf(first), onDocumentClick = {}) }

        composeRule.onNodeWithText(first.name).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.qr_open_document)).assertExists()
    }

    @Test
    fun menuContextual_abrirQuitarFavoritoYEliminar() {
        val calls = mutableListOf<String>()
        setSection {
            FavoritesSection(
                favorites = listOf(first),
                onDocumentClick = { calls += "open:${it.id}" },
                onFavoriteClick = { calls += "fav:$it" },
                onDeleteClick = { calls += "delete:$it" },
            )
        }

        listOf(R.string.qr_open_document, R.string.doc_item_remove_favorite, R.string.general_delete)
            .forEach { itemRes ->
                openMenuOfFirstCard()
                invokeMenuItem(itemRes)
            }

        assertEquals(listOf("open:f1", "fav:f1", "delete:f1"), calls)
    }

    @Test
    fun menuContextual_ejecutaLasAccionesRapidasConElDocumento() {
        val calls = mutableListOf<String>()
        setSection {
            FavoritesSection(
                favorites = listOf(first),
                onDocumentClick = {},
                onConvertClick = { calls += "convert:${it.id}" },
                onCreateQrClick = { calls += "qr:${it.id}" },
                onMakeSearchableClick = { calls += "ocr:${it.id}" },
                onSignClick = { calls += "sign:${it.id}" },
                onMoveToSecureFolderClick = { calls += "secure:${it.id}" },
            )
        }

        listOf(
            R.string.viewer_convert,
            R.string.viewer_create_qr,
            R.string.doc_item_make_searchable,
            R.string.doc_item_sign,
            R.string.doc_item_move_to_secure_folder,
        ).forEach { itemRes ->
            openMenuOfFirstCard()
            invokeMenuItem(itemRes)
        }

        assertEquals(
            listOf("convert:f1", "qr:f1", "ocr:f1", "sign:f1", "secure:f1"),
            calls,
        )
    }

    @Test
    fun menuContextual_renombrarConfirmaConLaExtensionOriginal() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            FavoritesSection(
                favorites = listOf(first),
                onDocumentClick = {},
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenuOfFirstCard()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Nuevo")
        composeRule.onNodeWithText(string(R.string.viewer_rename)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("f1" to "Nuevo.pdf"), renames)
    }

    @Test
    fun cancelarElRenombrado_cierraElDialogoSinCambios() {
        val renames = mutableListOf<Pair<String, String>>()
        setSection {
            FavoritesSection(
                favorites = listOf(first),
                onDocumentClick = {},
                onRenameClick = { id, name -> renames += id to name },
            )
        }

        openMenuOfFirstCard()
        invokeMenuItem(R.string.viewer_rename)
        composeRule.onNodeWithText(string(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<Pair<String, String>>(), renames)
        composeRule.onAllNodesWithText(string(R.string.doc_item_rename_title)).assertCountEquals(0)
    }
}
