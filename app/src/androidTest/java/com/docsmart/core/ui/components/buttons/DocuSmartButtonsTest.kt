package com.docsmart.core.ui.components.buttons

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: botones compartidos. Cada variante se renderiza habilitada (con
 * ícono inicial) y deshabilitada (sin ícono), para recorrer ambas ramas.
 */
class DocuSmartButtonsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val icon = Icons.Rounded.Check
    private var enabledClicks = 0
    private var disabledClicks = 0

    private fun setButtons(content: @Composable () -> Unit) {
        composeRule.setContent { MaterialTheme { Column { content() } } }
    }

    private fun assertOnlyTheEnabledOneReacts() {
        composeRule.onNodeWithText("Activo").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Inactivo").assertIsNotEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals(1, enabledClicks)
        assertEquals(0, disabledClicks)
    }

    @Test
    fun botonPrimario_respondeSoloCuandoEstaHabilitado() {
        setButtons {
            DocuSmartPrimaryButton(text = "Activo", onClick = { enabledClicks++ }, leadingIcon = icon)
            DocuSmartPrimaryButton(text = "Inactivo", onClick = { disabledClicks++ }, enabled = false)
        }

        assertOnlyTheEnabledOneReacts()
    }

    @Test
    fun botonSecundario_respondeSoloCuandoEstaHabilitado() {
        setButtons {
            DocuSmartSecondaryButton(text = "Activo", onClick = { enabledClicks++ }, leadingIcon = icon)
            DocuSmartSecondaryButton(text = "Inactivo", onClick = { disabledClicks++ }, enabled = false)
        }

        assertOnlyTheEnabledOneReacts()
    }

    @Test
    fun botonOutline_respondeSoloCuandoEstaHabilitado() {
        setButtons {
            DocuSmartOutlineButton(text = "Activo", onClick = { enabledClicks++ }, leadingIcon = icon)
            DocuSmartOutlineButton(text = "Inactivo", onClick = { disabledClicks++ }, enabled = false)
        }

        assertOnlyTheEnabledOneReacts()
    }

    @Test
    fun botonDestructivo_respondeSoloCuandoEstaHabilitado() {
        setButtons {
            DocuSmartDestructiveButton(text = "Activo", onClick = { enabledClicks++ }, leadingIcon = icon)
            DocuSmartDestructiveButton(text = "Inactivo", onClick = { disabledClicks++ }, enabled = false)
        }

        assertOnlyTheEnabledOneReacts()
    }

    @Test
    fun botonDeIcono_exponeLaDescripcionYInvocaElClic() {
        setButtons {
            DocuSmartIconButton(
                icon = Icons.Rounded.Check,
                contentDescription = "Aceptar",
                onClick = { enabledClicks++ },
            )
        }

        composeRule.onNodeWithContentDescription("Aceptar").performClick()
        composeRule.waitForIdle()

        assertEquals(1, enabledClicks)
    }
}
