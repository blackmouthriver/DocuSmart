package com.docsmart.features.library.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.core.ui.components.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: filtros de categoría de Biblioteca. Los rótulos de `CategoryFilter`
 * están hardcodeados en español (no vienen de recursos), por eso se buscan
 * como literales.
 */
class CategoryFilterTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setFilter(
        selected: DocumentType?,
        onSelected: (DocumentType?) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme { CategoryFilter(selectedCategory = selected, onCategorySelected = onSelected) }
        }
    }

    @Test
    fun muestraTodasLasCategoriasVisiblesYMarcaLaSeleccionada() {
        setFilter(selected = DocumentType.PDF)

        composeRule.onNodeWithText("PDF").assertIsSelected()
        composeRule.onNodeWithText("Todos").assertIsNotSelected()
        composeRule.onNodeWithText("Imágenes").assertIsNotSelected()
        composeRule.onNodeWithText("Texto").assertExists()
        composeRule.onNodeWithText("ZIP").assertExists()
        composeRule.onNodeWithText("Escaneados").assertExists()
    }

    @Test
    fun sinCategoria_marcaTodos() {
        setFilter(selected = null)

        composeRule.onNodeWithText("Todos").assertIsSelected()
        composeRule.onNodeWithText("PDF").assertIsNotSelected()
    }

    @Test
    fun tocarCadaChip_informaElTipoCorrespondiente() {
        val events = mutableListOf<DocumentType?>()
        setFilter(selected = null, onSelected = { events += it })

        listOf("Todos", "PDF", "Imágenes", "Texto", "ZIP", "Escaneados").forEach {
            composeRule.onNodeWithText(it).performClick()
        }
        composeRule.waitForIdle()

        assertEquals(
            listOf(null, DocumentType.PDF, DocumentType.IMAGE, DocumentType.TEXT, DocumentType.ZIP, DocumentType.OCR),
            events,
        )
    }
}
