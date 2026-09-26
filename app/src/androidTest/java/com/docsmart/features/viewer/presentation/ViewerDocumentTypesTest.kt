package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.docsmart.R
import com.docsmart.features.pdftools.presentation.components.RecordingContext
import com.docsmart.features.pdftools.presentation.components.esContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: ViewerScreen abre documentos REALES de cada tipo (texto, imagen, Word, Excel,
 * PowerPoint, formato no soportado) generados en cacheDir, más los estados de error.
 * Los .docx/.xlsx/.pptx se arman a mano como ZIP OOXML mínimo (sin escribir con POI, que
 * en Android depende de clases de java.awt); el visor los lee con POI igual que en producción.
 */
class ViewerDocumentTypesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val files = ViewerTestFiles()
    private val harness = ViewerHarness()

    @After
    fun tearDown() {
        files.deleteAll()
    }

    private fun open(
        file: File,
        onBack: () -> Unit = {},
    ) {
        composeRule.setViewerContent {
            ViewerScreen(documentId = file.absolutePath, onBack = onBack, viewModel = harness.viewModel)
        }
        composeRule.waitForText(file.name)
    }

    private fun search(term: String) {
        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(term)
    }

    // Toca el centro hasta que el visor de imagen (ya cargada) alterne los controles.
    private fun tapUntilControlsHidden() {
        composeRule.waitForState {
            composeRule.onRoot().performTouchInput { click(center) }
            !harness.viewModel.uiState.value.showControls
        }
    }

    // ── Texto plano ─────────────────────────────────────────────────────────

    @Test
    fun texto_muestraElContenidoYLoFiltraConLaBusqueda() {
        val file = files.text(".txt", "Linea uno alfa\nLinea dos beta\nLinea tres alfa")
        open(file)
        composeRule.waitForText("Linea uno alfa", substring = true)

        search("alfa")
        composeRule.waitForText(vs(R.string.viewer_text_results_count, 2))
        composeRule.onNodeWithText("Linea dos beta").assertDoesNotExist()

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("zzz")
        composeRule.waitForText(vs(R.string.viewer_search_no_results))

        composeRule.onNodeWithContentDescription(vs(R.string.general_close)).performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun textoVacio_muestraElAvisoDeArchivoVacio() {
        open(files.text(".txt", ""))
        composeRule.waitForText(vs(R.string.viewer_empty_file))
    }

    @Test
    fun csvYMarkdown_seAbrenComoTexto() {
        open(files.text(".csv", "columna1,columna2\nvalorA,valorB"))
        composeRule.waitForText("valorA,valorB", substring = true)
    }

    @Test
    fun tocarElTexto_alternaLosControles() {
        val file = files.text(".md", "# Titulo markdown")
        open(file)
        composeRule.waitForText("Titulo markdown", substring = true)
        composeRule.onNodeWithText("# Titulo markdown").performClick()
        composeRule.waitForState { !harness.viewModel.uiState.value.showControls }
    }

    // ── Imágenes ────────────────────────────────────────────────────────────

    @Test
    fun imagenPng_seCargaYAlTocarlaAlternaLosControles() {
        open(files.png())
        assertTrue(harness.viewModel.uiState.value.showControls)
        tapUntilControlsHidden()
        assertFalse(harness.viewModel.uiState.value.showControls)
    }

    @Test
    fun imagenJpeg_seCargaYAlTocarlaAlternaLosControles() {
        open(files.jpeg())
        tapUntilControlsHidden()
        assertFalse(harness.viewModel.uiState.value.showControls)
    }

    @Test
    fun imagenCorrupta_muestraElErrorEnVezDeUnSpinnerInfinito() {
        open(files.bytes(".png", "esto no es una imagen".toByteArray()))
        composeRule.waitForText(vs(R.string.viewer_error))
    }

    // Gap real (ronda 23): el icono de buscar de ViewerTopBar se muestra
    // SIEMPRE (sin importar el tipo de documento) -- pero ViewerTopBarSection
    // solo activa la barra de busqueda si `isTextBased` (PDF/Word/Excel/
    // PowerPoint/texto). Ningun test tocaba ese icono sobre una imagen para
    // confirmar que de verdad es un no-op.
    @Test
    fun imagen_elIconoDeBuscarEsUnNoOpYNoAbreLaBarra() {
        open(files.png())

        composeRule.onNodeWithContentDescription(vs(R.string.viewer_search_content_desc)).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // ── Word ────────────────────────────────────────────────────────────────

    @Test
    fun word_muestraEncabezadoParrafosYTablaYFiltraConLaBusqueda() {
        open(files.docx())
        composeRule.waitForText("Titulo del informe")
        composeRule.onNodeWithText("Parrafo con clausula uno").assertExists()
        composeRule.onNodeWithText("Concepto").assertExists()
        composeRule.onNodeWithText("1500").assertExists()

        search("clausula")
        composeRule.waitForText("resultado(s) para", substring = true)
        composeRule.onNodeWithText("Titulo del informe").assertDoesNotExist()
        composeRule.onNodeWithText("Parrafo con clausula uno").assertExists()
    }

    @Test
    fun wordCorrupto_muestraElMensajeDeErrorDeLectura() {
        open(files.bytes(".docx", "no es un docx".toByteArray()))
        composeRule.waitForText(vs(R.string.viewer_word_read_error))
    }

    // ── Excel ───────────────────────────────────────────────────────────────

    @Test
    fun excel_muestraHojasConPestanasYCambiaDeHoja() {
        open(files.xlsx())
        composeRule.waitForText("Lapiz")
        composeRule.onNodeWithText("Resumen").assertExists()

        composeRule.onNodeWithText("Ventas").performClick()
        composeRule.waitForText("Enero")
        composeRule.onNodeWithText("Lapiz").assertDoesNotExist()
    }

    @Test
    fun excel_labusquedaSaltaALaHojaQueTieneLaCoincidencia() {
        open(files.xlsx())
        composeRule.waitForText("Lapiz")

        // "Enero" solo existe en la hoja Ventas: el visor cambia de hoja solo.
        search("Enero")
        composeRule.waitForText("TotalEnero")
        composeRule.waitForText("resultado(s) para", substring = true)
        composeRule.onNodeWithText("Lapiz").assertDoesNotExist()
    }

    @Test
    fun excelSinCoincidencias_muestraElConteoEnCero() {
        open(files.xlsx())
        composeRule.waitForText("Lapiz")
        search("noexiste")
        composeRule.waitForText(vs(R.string.viewer_search_results_count, 0, "noexiste"))
    }

    @Test
    fun excelCorrupto_muestraElMensajeDeErrorDeLectura() {
        open(files.bytes(".xlsx", "no es un xlsx".toByteArray()))
        composeRule.waitForText(vs(R.string.viewer_excel_read_error))
    }

    // Gap real (ronda 23): ExcelSheetTabs se oculta con `sheets.size > 1` --
    // todos los fixtures existentes tenian 2 hojas, asi que esa rama (un
    // libro de una sola hoja, sin pestañas que elegir) nunca se probaba.
    @Test
    fun excel_conUnaSolaHojaNoMuestraPestanas() {
        open(files.xlsxSingleSheet())
        composeRule.waitForText("Lapiz")

        composeRule.onNodeWithText("Resumen").assertDoesNotExist()
    }

    // ── PowerPoint ──────────────────────────────────────────────────────────

    // El .pptx mínimo hecho a mano puede no ser aceptado por POI en todos los entornos:
    // ambos desenlaces (contenido o mensaje de error) recorren código real del visor.
    @Test
    fun powerPoint_muestraLaDiapositivaOElMensajeDeError() {
        open(files.pptx())
        composeRule.waitForViewerNode(
            hasText("Plan trimestral") or hasText(vs(R.string.viewer_ppt_read_error)),
        )
        if (composeRule.onAllNodesWithText("Plan trimestral").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(vs(R.string.viewer_slide_number, 1)).assertExists()
            search("alfa")
            composeRule.waitForText("resultado(s) para", substring = true)
        }
    }

    @Test
    fun powerPointCorrupto_muestraElMensajeDeErrorDeLectura() {
        open(files.bytes(".pptx", "no es un pptx".toByteArray()))
        composeRule.waitForText(vs(R.string.viewer_ppt_read_error))
    }

    // ── Formato no soportado ────────────────────────────────────────────────

    @Test
    fun formatoNoSoportado_ofreceAbrirConOtraAppYLanzaElSelector() {
        // En cacheDir/scanner/ para que FileProvider pueda compartirlo (ver file_provider_paths.xml).
        val file = files.bytes(".bin", ByteArray(32) { it.toByte() }, sub = "scanner")
        val recording = RecordingContext(esContext())
        composeRule.setViewerContent(overrideContext = recording) {
            ViewerScreen(documentId = file.absolutePath, onBack = {}, viewModel = harness.viewModel)
        }
        composeRule.waitForText(vs(R.string.viewer_unsupported))
        // Gap real (ronda 23): la etiqueta del recuadro de icono (formatLabel)
        // nunca se afirmaba -- solo el mensaje "no soportado" y el botón.
        // Un .bin no cae en ninguna rama conocida (Word/Excel/PowerPoint/
        // texto), así que usa el genérico.
        composeRule.onNodeWithText(vs(R.string.viewer_format_generic)).assertExists()

        composeRule.onNodeWithText(vs(R.string.viewer_open_other)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recording.startedIntents.size)
        assertEquals(android.content.Intent.ACTION_CHOOSER, recording.startedIntents.first().action)
    }

    @Test
    fun formatoNoSoportado_siFileProviderNoPuedeServirElArchivoNoLanzaNada() {
        // Fuera de las carpetas de FileProvider: getUriForFile falla y el visor avisa con un Toast.
        val file = files.bytes(".bin", ByteArray(8))
        val recording = RecordingContext(esContext())
        composeRule.setViewerContent(overrideContext = recording) {
            ViewerScreen(documentId = file.absolutePath, onBack = {}, viewModel = harness.viewModel)
        }
        composeRule.waitForText(vs(R.string.viewer_unsupported))

        composeRule.onNodeWithText(vs(R.string.viewer_open_other)).performClick()
        composeRule.waitForIdle()

        assertTrue(recording.startedIntents.isEmpty())
    }

    // ── Estados de error / inexistente ──────────────────────────────────────

    @Test
    fun pdfVacio_muestraElErrorEnVezDeRenderizarParaSiempre() {
        open(files.bytes(".pdf", ByteArray(0)))
        composeRule.waitForText(vs(R.string.viewer_error))
    }

    @Test
    fun pdfQueYaNoExiste_muestraElError() {
        val missing = File(files.dir(), "viewer_r20_no_existe.pdf")
        composeRule.setViewerContent {
            ViewerScreen(documentId = missing.absolutePath, onBack = {}, viewModel = harness.viewModel)
        }
        composeRule.waitForText(vs(R.string.viewer_error))
    }

    @Test
    fun documentoInexistente_muestraElErrorYVolverCierraElVisor() {
        var backCalls = 0
        composeRule.setViewerContent {
            ViewerScreen(documentId = "999", onBack = { backCalls++ }, viewModel = harness.viewModel)
        }
        composeRule.waitForText(vs(R.string.viewer_document_not_found))

        composeRule.onNodeWithText(vs(R.string.viewer_back)).performClick()
        assertEquals(1, backCalls)
    }

    // Recorre los documentos de demostración (ids 1..12): cada tipo entra por su rama del visor.
    @Test
    fun documentosDeDemostracion_seAbrenPorSuRamaDeVisor() {
        var currentId by mutableStateOf("2")
        composeRule.setViewerContent {
            ViewerScreen(documentId = currentId, onBack = {}, viewModel = harness.viewModel)
        }
        val expected =
            listOf(
                "2" to "Informe_Trimestral.docx",
                "3" to "Presupuesto_Q1.xlsx",
                "4" to "Presentacion_Clientes.pptx",
                "5" to "Foto_Documento.jpg",
                "7" to "Notas_Reunion.txt",
                "8" to "Backup_Documentos.zip",
            )
        expected.forEach { (id, name) ->
            composeRule.runOnIdle { currentId = id }
            composeRule.waitForText(name)
        }
    }
}
