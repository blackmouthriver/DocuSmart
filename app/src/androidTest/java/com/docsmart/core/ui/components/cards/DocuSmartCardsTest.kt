package com.docsmart.core.ui.components.cards

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 23: tarjetas reutilizables de `core/ui/components/cards` (0% de
 * cobertura, ningún test previo). Cada composable se ejerce con su tinte por
 * defecto (`Color.Unspecified`, rama `takeOrElse`) y con uno personalizado,
 * más el callback de clic.
 */
class DocuSmartCardsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val icon = Icons.Rounded.Star

    @Test
    fun cardBase_sinOnClick_noEsAccionablePeroMuestraSuContenido() {
        composeRule.setContent {
            MaterialTheme {
                DocuSmartCard(onClick = null) { Text("Contenido de la tarjeta") }
            }
        }

        composeRule.onNodeWithText("Contenido de la tarjeta").assertHasNoClickAction()
    }

    @Test
    fun cardBase_conOnClick_esAccionableEInvocaElCallback() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                DocuSmartCard(onClick = { clicks++ }) { Text("Toca aquí") }
            }
        }

        composeRule.onNodeWithText("Toca aquí").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun accesoRapido_conTinteYFondoPorDefecto_muestraElLabelYRespondeAlClic() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                DocuSmartQuickAccessCard(icon = icon, label = "Escanear", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Escanear").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun accesoRapido_conTinteYFondoPersonalizados_muestraElLabelYRespondeAlClic() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                DocuSmartQuickAccessCard(
                    icon = icon,
                    label = "Carpeta segura",
                    onClick = { clicks++ },
                    iconTint = Color.Red,
                    backgroundColor = Color.Blue,
                )
            }
        }

        composeRule.onNodeWithText("Carpeta segura").performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun tileDeHerramienta_muestraTituloYDescripcionYRespondeAlClic() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                Column {
                    // Tinte por defecto (Unspecified) y personalizado, para recorrer
                    // ambas ramas de takeOrElse.
                    DocuSmartToolTile(
                        icon = icon,
                        title = "Comprimir",
                        description = "Reduce el tamaño del PDF",
                        onClick = { clicks++ },
                    )
                    DocuSmartToolTile(
                        icon = icon,
                        title = "Unir PDF",
                        description = "Combina varios documentos",
                        onClick = { clicks++ },
                        iconTint = Color.Green,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Comprimir").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Reduce el tamaño del PDF").assertExists()
        composeRule.onNodeWithText("Unir PDF").performClick()
        composeRule.onNodeWithText("Combina varios documentos").assertExists()
        composeRule.waitForIdle()

        assertEquals(2, clicks)
    }

    @Test
    fun cardDeHerramienta_muestraTituloDescripcionYRespondeAlClic() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                Column {
                    DocuSmartToolCard(
                        icon = icon,
                        title = "Convertir a Word",
                        description = "PDF a DOCX editable",
                        onClick = { clicks++ },
                    )
                    DocuSmartToolCard(
                        icon = icon,
                        title = "OCR avanzado",
                        description = "Extrae texto de imágenes",
                        onClick = { clicks++ },
                        iconTint = Color.Magenta,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Convertir a Word").assertHasClickAction().performClick()
        composeRule.onNodeWithText("PDF a DOCX editable").assertExists()
        composeRule.onNodeWithText("OCR avanzado").performClick()
        composeRule.onNodeWithText("Extrae texto de imágenes").assertExists()
        composeRule.waitForIdle()

        assertEquals(2, clicks)
    }
}
