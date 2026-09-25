package com.docsmart.features.scanner.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.scanner.domain.ScanColorMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * ScanColorModeSection.kt (HU-41): selector del modo de color de una página
 * escaneada, con y sin miniatura de vista previa.
 */
class ScanColorModeSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(id)

    private val labels =
        mapOf(
            ScanColorMode.COLOR to R.string.scan_color_mode_color,
            ScanColorMode.BLACK_AND_WHITE to R.string.scan_color_mode_bw,
            ScanColorMode.GRAYSCALE to R.string.scan_color_mode_grayscale,
            ScanColorMode.HIGHLIGHT_TEXT to R.string.scan_color_mode_highlight,
        )

    @Test
    fun seccion_muestraTituloYLosCuatroModos_sinMiniatura() {
        composeRule.setContentEs {
            ScanColorModeSection(previewUri = null, selected = ScanColorMode.COLOR, onSelect = {})
        }

        composeRule.onNodeWithText(str(R.string.scan_color_mode_label)).assertExists()
        labels.values.forEach { composeRule.onNodeWithText(str(it)).assertExists() }
        composeRule.onNodeWithText(str(R.string.scan_color_mode_color)).assertIsSelected()
    }

    @Test
    fun seccion_tocarCadaModoInvocaOnSelect_yActualizaLaSeleccion() {
        var selected by mutableStateOf(ScanColorMode.COLOR)
        val picked = mutableListOf<ScanColorMode>()
        composeRule.setContentEs {
            ScanColorModeSection(
                previewUri = null,
                selected = selected,
                onSelect = {
                    picked += it
                    selected = it
                },
            )
        }

        labels.forEach { (mode, label) ->
            // FlowRow (sin scroll): no hace falta desplazar, todos los chips ya
            // están dispuestos en el layout (se envuelven a otra fila si no caben).
            composeRule.onNodeWithText(str(label)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(str(label)).assertIsSelected()
            assertEquals(mode, selected)
        }
        assertEquals(labels.keys.toList(), picked)
    }

    @Test
    fun filaDeChips_conMiniatura_rendereaCadaModoConSuMatrizDeColor() {
        // Archivo inexistente: Coil falla en silencio y el chip conserva su etiqueta.
        val preview = Uri.parse("file:///no-existe/pagina.png")
        var selected by mutableStateOf(ScanColorMode.GRAYSCALE)
        composeRule.setContentEs {
            ScanColorModeChipRow(previewUri = preview, selected = selected, onSelect = { selected = it })
        }

        labels.values.forEach { composeRule.onNodeWithText(str(it)).assertExists() }
        composeRule.onNodeWithText(str(R.string.scan_color_mode_grayscale)).assertIsSelected()

        composeRule.onNodeWithText(str(R.string.scan_color_mode_highlight)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.scan_color_mode_highlight)).assertIsSelected()
    }
}
