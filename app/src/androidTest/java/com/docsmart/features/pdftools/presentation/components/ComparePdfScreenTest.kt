package com.docsmart.features.pdftools.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * ComparePdfScreen ya tenía dos casos cubiertos en PdfToolsFormComponentsTest.kt
 * ("comparar_soloConAmbosPdfsSeHabilitaYSeMuestraElNombre" y
 * "comparar_conAmbosPdfs_habilitaEjecutar"), que entre los dos ya ejercen cada
 * línea del composable: título/subtítulo, las dos zonas de selección, el
 * campo de nombre de salida condicional (if pdfA != null && pdfB != null) y
 * el pie de proceso/ejecutar con canExecuteCompare() habilitado y
 * deshabilitado. El único estado que faltaba por verificar explícitamente es
 * el inicial -- **ningún PDF seleccionado todavía** (ninguno de los dos casos
 * anteriores parte de ahí) -- así que este archivo nuevo se limita a ese
 * caso, sin repetir los ya existentes.
 */
class ComparePdfScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings = esContext()

    private fun s(id: Int): String = strings.getString(id)

    @Test
    fun sinNingunPdfSeleccionado_muestraAmbasInvitacionesYDeshabilitaElBoton() {
        val selects = mutableListOf<String>()
        composeRule.setEsContent {
            ComparePdfScreen(
                pdfA = null,
                pdfB = null,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfA = { selects += "A" },
                onSelectPdfB = { selects += "B" },
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_compare)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_compare_subtitle)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_compare_document_a)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_compare_document_b)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_compare_execute)).assertIsNotEnabled()

        // Las dos zonas están vacías: hay dos invitaciones iguales, en el mismo
        // orden en que se componen (A primero, B después).
        val prompts = composeRule.onAllNodesWithText(s(R.string.pdf_tools_select_pdf_prompt))
        prompts[0].performClick()
        prompts[1].performClick()
        assertEquals(listOf("A", "B"), selects)
    }
}
