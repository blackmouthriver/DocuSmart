package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Ronda 18: chips compartidos (filtro y badge de tipo de archivo). */
class DocuSmartChipTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chipDeFiltro_noSeleccionado_alTocarloPideSeleccionarlo() {
        val events = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                DocuSmartFilterChip(label = "Recientes", selected = false, onSelected = { events += it })
            }
        }

        composeRule.onNodeWithText("Recientes").assertIsNotSelected().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(true), events)
    }

    @Test
    fun chipDeFiltro_seleccionadoConIcono_alTocarloPideDeseleccionarlo() {
        val events = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                Column {
                    DocuSmartFilterChip(
                        label = "Activo",
                        selected = true,
                        onSelected = { events += it },
                        leadingIcon = Icons.Rounded.Check,
                        iconTint = Color.Red,
                    )
                    // Sin color propio: cae al onSurfaceVariant del tema.
                    DocuSmartFilterChip(
                        label = "Otro",
                        selected = false,
                        onSelected = {},
                        leadingIcon = Icons.Rounded.Check,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Activo").assertIsSelected().performClick()
        composeRule.onNodeWithText("Otro").assertIsNotSelected()
        composeRule.waitForIdle()

        assertEquals(listOf(false), events)
    }

    @Test
    fun chipDeTipoDeArchivo_esInformativoYExponeSoloSuEtiqueta() {
        composeRule.setContent {
            MaterialTheme { DocuSmartFileTypeChip(label = "PDF", color = Color.Red) }
        }

        composeRule.onNodeWithContentDescription("PDF").assertExists().assertHasNoClickAction()
    }
}
