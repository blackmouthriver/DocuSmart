package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.docsmart.R
import com.docsmart.features.pdftools.domain.usecase.PageNumberFormat
import com.docsmart.features.pdftools.presentation.PdfTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pantallas de Herramientas PDF que solo dependen de estado/lambdas por
 * parámetro y no hacen E/S propia (Unir, Dividir, Comprimir, Numerar, Marca de
 * agua, Editar texto, Comparar, OCR, Extraer imágenes, menú y componentes
 * comunes). Se renderizan con estado fijo y se verifica que los clics y el
 * texto tecleado lleguen a los callbacks.
 */
class PdfToolsFormComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings = esContext()

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = if (args.isEmpty()) strings.getString(id) else strings.getString(id, *args)

    // Uri sin E/S real: solo se usa su lastPathSegment como nombre visible.
    private val pdfUri: Uri = Uri.parse("content://docsmart.test/docs/contrato.pdf")

    private fun slider() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    // ── PdfSelectZone / PdfProcessingFooter / OutputFileNameField ────

    @Test
    fun seleccionDePdf_sinArchivo_muestraInvitacionYClicLlamaCallback() {
        var selected = 0
        composeRule.setEsContent {
            PdfSelectZone(selectedPdf = null, onSelectPdf = { selected++ }, readyText = "listo", label = "Etiqueta")
        }

        composeRule.onNodeWithText("Etiqueta").assertExists()
        composeRule.onNodeWithText("listo").assertDoesNotExist()
        composeRule.onNodeWithText(s(R.string.pdf_tools_select_pdf_prompt)).performClick()
        assertEquals(1, selected)
    }

    @Test
    fun seleccionDePdf_conArchivo_muestraTextoListoYNombre() {
        composeRule.setEsContent {
            PdfSelectZone(selectedPdf = pdfUri, onSelectPdf = {}, readyText = "listo")
        }

        composeRule.onNodeWithText("listo").assertExists()
        composeRule.onNodeWithText("contrato.pdf").assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_select_pdf_prompt)).assertDoesNotExist()
    }

    @Test
    fun seleccionDePdf_uriSinSegmento_usaNombrePorDefecto() {
        composeRule.setEsContent {
            PdfSelectZone(selectedPdf = Uri.parse("content://docsmart.test"), onSelectPdf = {}, readyText = "listo")
        }

        composeRule.onNodeWithText(s(R.string.pdf_tools_default_filename)).assertExists()
    }

    @Test
    fun campoNombreDeSalida_mostraSufijoPdfYPropagaElTexto() {
        var typed = ""
        composeRule.setEsContent {
            OutputFileNameField(fileName = "", onFileNameChange = { typed = it })
        }

        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_hint)).assertExists()
        assertTrue(composeRule.onAllNodesWithText(".pdf").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNode(hasSetTextAction()).performTextInput("informe")
        assertEquals("informe", typed)
    }

    @Test
    fun campoNombreDeSalida_sinSufijo_noMuestraPdf() {
        composeRule.setEsContent {
            OutputFileNameField(fileName = "imagenes", onFileNameChange = {}, showPdfSuffix = false)
        }

        assertTrue(composeRule.onAllNodesWithText(".pdf").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pieDeProcesamiento_alternaEntreBotonYProgreso() {
        var executed = 0
        val processing = mutableStateOf(false)
        composeRule.setEsContent {
            PdfProcessingFooter(
                isProcessing = processing.value,
                enabled = true,
                progressText = "Trabajando",
                buttonLabel = "Ejecutar",
                buttonIcon = Icons.Rounded.Home,
                onExecute = { executed++ },
            )
        }

        composeRule.button("Ejecutar").performClick()
        assertEquals(1, executed)
        composeRule.onNodeWithText("Trabajando").assertDoesNotExist()

        composeRule.runOnUiThread { processing.value = true }
        composeRule.onNodeWithText("Trabajando").assertExists()
        composeRule.onNodeWithText("Ejecutar").assertDoesNotExist()
    }

    // ── Unir ─────────────────────────────────────────────────────────

    @Test
    fun unir_sinPdfs_invitaASeleccionarYDeshabilitaElBoton() {
        var selected = 0
        composeRule.setEsContent {
            MergePdfScreen(
                selectedPdfs = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfs = { selected++ },
                onRemovePdf = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_merge)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_merge_subtitle)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_merge_order_hint)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_merge_select_at_least_2)).assertIsNotEnabled()
        composeRule.onNodeWithText(s(R.string.pdf_merge_select_prompt)).performClick()
        assertEquals(1, selected)
    }

    @Test
    fun unir_conTresPdfs_listaArchivosPermiteQuitarYEjecutar() {
        val uris =
            listOf(
                Uri.parse("content://docsmart.test/docs/a.pdf"),
                Uri.parse("content://docsmart.test/docs/b.pdf"),
                // Sin segmento de ruta: usa el nombre de respaldo "Archivo N".
                Uri.parse("content://docsmart.test"),
            )
        val removed = mutableListOf<Uri>()
        var executed = 0
        composeRule.setEsContent {
            MergePdfScreen(
                selectedPdfs = uris,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfs = {},
                onRemovePdf = { removed += it },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_merge_add_more)).assertExists()
        composeRule.onNodeWithText("a.pdf").assertExists()
        composeRule.onNodeWithText("b.pdf").assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_merge_file_fallback, 3)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_merge_order_hint)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertExists()

        composeRule.onAllNodesWithContentDescription(s(R.string.pdf_merge_remove_desc))[1].performClick()
        assertEquals(listOf(uris[1]), removed)

        composeRule.button(s(R.string.pdf_merge_execute, 3)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun unir_procesando_muestraProgresoConLaCantidad() {
        composeRule.setEsContent {
            MergePdfScreen(
                selectedPdfs = listOf(pdfUri, pdfUri),
                isProcessing = true,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfs = {},
                onRemovePdf = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_merge_progress, 2)).assertExists()
    }

    // ── Dividir ──────────────────────────────────────────────────────

    @Test
    fun dividir_sinPdf_noMuestraRangoNiHabilitaElBoton() {
        composeRule.setEsContent {
            SplitPdfScreen(
                selectedPdf = null,
                fromPage = 1,
                toPage = 1,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFromPageChange = {},
                onToPageChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_split_range_title)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_split)).assertIsNotEnabled()
    }

    @Test
    fun dividir_rangoValido_botonesAjustanPaginasYEjecutar() {
        val fromChanges = mutableListOf<Int>()
        val toChanges = mutableListOf<Int>()
        var executed = 0
        composeRule.setEsContent {
            SplitPdfScreen(
                selectedPdf = pdfUri,
                fromPage = 2,
                toPage = 4,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFromPageChange = { fromChanges += it },
                onToPageChange = { toChanges += it },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_split_summary, 3, 2, 4)).assertExists()
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_from_page_decrease)).performClick()
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_from_page_increase)).performClick()
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_to_page_decrease)).performClick()
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_to_page_increase)).performClick()
        composeRule.button(s(R.string.pdf_split)).performScrollTo().performClick()

        assertEquals(listOf(1, 3), fromChanges)
        assertEquals(listOf(3, 5), toChanges)
        assertEquals(1, executed)
    }

    @Test
    fun dividir_limites_noPermitenBajarMasAllaDelMinimo() {
        val fromChanges = mutableListOf<Int>()
        val toChanges = mutableListOf<Int>()
        composeRule.setEsContent {
            SplitPdfScreen(
                selectedPdf = pdfUri,
                fromPage = 3,
                toPage = 3,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFromPageChange = { fromChanges += it },
                onToPageChange = { toChanges += it },
                onExecute = {},
            )
        }

        // "Hasta" == "Desde": no baja. "Desde" 3 sí puede bajar a 2.
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_to_page_decrease)).performClick()
        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_from_page_decrease)).performClick()

        assertTrue(toChanges.isEmpty())
        assertEquals(listOf(2), fromChanges)
    }

    @Test
    fun dividir_desdeEnUno_noBajaYRangoInvertidoDeshabilitaElBoton() {
        val fromChanges = mutableListOf<Int>()
        composeRule.setEsContent {
            SplitPdfScreen(
                selectedPdf = pdfUri,
                fromPage = 1,
                toPage = 0,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFromPageChange = { fromChanges += it },
                onToPageChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithContentDescriptionText(s(R.string.pdf_split_from_page_decrease)).performClick()
        assertTrue(fromChanges.isEmpty())
        composeRule.onNodeWithText(s(R.string.pdf_split_summary, 0, 1, 0)).assertExists()
        composeRule.button(s(R.string.pdf_split)).assertIsNotEnabled()
    }

    // ── Comprimir ────────────────────────────────────────────────────

    @Test
    fun comprimir_sinPdf_ocultaNombreDeSalidaYDeshabilitaElBoton() {
        composeRule.setEsContent {
            CompressPdfScreen(
                selectedPdf = null,
                quality = 80,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onQualityChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_compress_subtitle)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_compress)).assertIsNotEnabled()
    }

    @Test
    fun comprimir_recorreLosCuatroNivelesDeCalidad() {
        val quality = mutableIntStateOf(90)
        composeRule.setEsContent {
            CompressPdfScreen(
                selectedPdf = pdfUri,
                quality = quality.intValue,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onQualityChange = {},
                onExecute = {},
            )
        }

        val expected =
            listOf(
                90 to (R.string.pdf_compress_level_high_quality to R.string.pdf_compress_reduction_10_20),
                70 to (R.string.pdf_compress_level_balanced to R.string.pdf_compress_reduction_30_50),
                50 to (R.string.pdf_compress_level_high_compression to R.string.pdf_compress_reduction_50_70),
                30 to (R.string.pdf_compress_level_max_compression to R.string.pdf_compress_reduction_70_85),
            )
        expected.forEach { (value, texts) ->
            composeRule.runOnUiThread { quality.intValue = value }
            composeRule.onNodeWithText(s(texts.first)).assertExists()
            composeRule.onNodeWithText(s(texts.second)).assertExists()
            composeRule.onNodeWithText("$value%").assertExists()
        }
    }

    @Test
    fun comprimir_deslizadorYEjecutarLlamanCallbacks() {
        var newQuality = -1
        var executed = 0
        composeRule.setEsContent {
            CompressPdfScreen(
                selectedPdf = pdfUri,
                quality = 80,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onQualityChange = { newQuality = it },
                onExecute = { executed++ },
            )
        }

        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        assertTrue("calidad=$newQuality", newQuality in 59..61)

        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertExists()
        composeRule.button(s(R.string.pdf_compress)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun comprimir_procesando_muestraProgreso() {
        composeRule.setEsContent {
            CompressPdfScreen(
                selectedPdf = pdfUri,
                quality = 80,
                isProcessing = true,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onQualityChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_compress_progress)).assertExists()
    }

    // ── Numerar páginas ──────────────────────────────────────────────

    @Test
    fun numerar_formatosMuestranSuEjemploYPropaganLaSeleccion() {
        val format = mutableStateOf(PageNumberFormat.NUMBER_ONLY)
        val chosen = mutableListOf<PageNumberFormat>()
        var executed = 0
        composeRule.setEsContent {
            NumberPagesScreen(
                selectedPdf = pdfUri,
                format = format.value,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFormatChange = { chosen += it },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_number_pages_example_number_only)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_number_pages_format_number_of_total)).performClick()
        composeRule.onNodeWithText(s(R.string.pdf_number_pages_format_page_of_total)).performClick()
        composeRule.onNodeWithText(s(R.string.pdf_number_pages_format_number_only)).performClick()
        assertEquals(
            listOf(PageNumberFormat.NUMBER_OF_TOTAL, PageNumberFormat.PAGE_OF_TOTAL, PageNumberFormat.NUMBER_ONLY),
            chosen,
        )

        composeRule.runOnUiThread { format.value = PageNumberFormat.NUMBER_OF_TOTAL }
        composeRule.onNodeWithText(s(R.string.pdf_number_pages_example_number_of_total)).assertExists()
        composeRule.runOnUiThread { format.value = PageNumberFormat.PAGE_OF_TOTAL }
        composeRule.onNodeWithText(s(R.string.pdf_number_pages_example_page_of_total)).assertExists()

        composeRule.button(s(R.string.pdf_number_pages_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun numerar_sinPdf_deshabilitaElBotonYOcultaElNombre() {
        composeRule.setEsContent {
            NumberPagesScreen(
                selectedPdf = null,
                format = PageNumberFormat.NUMBER_ONLY,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onFormatChange = {},
                onExecute = {},
            )
        }

        composeRule.button(s(R.string.pdf_number_pages_execute)).assertIsNotEnabled()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
    }

    // ── Marca de agua ────────────────────────────────────────────────

    @Test
    fun marcaDeAgua_botonDependeDelPdfYDelTexto() {
        val text = mutableStateOf("   ")
        var typed = ""
        var executed = 0
        composeRule.setEsContent {
            WatermarkPdfScreen(
                selectedPdf = pdfUri,
                watermarkText = text.value,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onWatermarkTextChange = { typed = it },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_watermark_hint)).assertExists()
        composeRule.button(s(R.string.pdf_watermark_execute)).assertIsNotEnabled()

        composeRule.runOnUiThread { text.value = "CONFIDENCIAL" }
        composeRule.button(s(R.string.pdf_watermark_execute)).assertIsEnabled()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("X")
        assertTrue(typed.contains("X"))

        composeRule.button(s(R.string.pdf_watermark_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun marcaDeAgua_sinPdf_deshabilitaElBoton() {
        composeRule.setEsContent {
            WatermarkPdfScreen(
                selectedPdf = null,
                watermarkText = "CONFIDENCIAL",
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onWatermarkTextChange = {},
                onExecute = {},
            )
        }

        composeRule.button(s(R.string.pdf_watermark_execute)).assertIsNotEnabled()
    }

    // ── Editar texto ─────────────────────────────────────────────────

    @Test
    fun editarTexto_camposBuscarYReemplazarLlamanCallbacks() {
        val search = mutableStateOf("")
        var typedSearch = ""
        var typedReplace = ""
        var executed = 0
        composeRule.setEsContent {
            EditTextPdfScreen(
                selectedPdf = pdfUri,
                searchText = search.value,
                replaceText = "",
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onSearchTextChange = { if (it.isNotEmpty()) typedSearch = it },
                onReplaceTextChange = { if (it.isNotEmpty()) typedReplace = it },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_edit_text_hint)).assertExists()
        composeRule.button(s(R.string.pdf_edit_text_execute)).assertIsNotEnabled()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("hola")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("adios")
        assertEquals("hola", typedSearch)
        assertEquals("adios", typedReplace)

        composeRule.runOnUiThread { search.value = "hola" }
        composeRule.button(s(R.string.pdf_edit_text_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun editarTexto_procesandoYSinPdf() {
        composeRule.setEsContent {
            EditTextPdfScreen(
                selectedPdf = null,
                searchText = "x",
                replaceText = "",
                isProcessing = true,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onSearchTextChange = {},
                onReplaceTextChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_edit_text_progress)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
    }

    // ── Comparar ─────────────────────────────────────────────────────

    @Test
    fun comparar_soloConAmbosPdfsSeHabilitaYSeMuestraElNombre() {
        val pdfA = mutableStateOf<Uri?>(pdfUri)
        val selects = mutableListOf<String>()
        var executed = 0
        composeRule.setEsContent {
            ComparePdfScreen(
                pdfA = pdfA.value,
                pdfB = null,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfA = { selects += "A" },
                onSelectPdfB = { selects += "B" },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_compare_document_a)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_compare_document_b)).assertExists()
        composeRule.button(s(R.string.pdf_compare_execute)).assertIsNotEnabled()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()

        // Solo B sigue sin seleccionar: la única invitación visible es la de B.
        composeRule.onNodeWithText(s(R.string.pdf_tools_select_pdf_prompt)).performClick()
        assertEquals(listOf("B"), selects)
        composeRule.onNodeWithText("contrato.pdf").performClick()
        assertEquals(listOf("B", "A"), selects)
        assertEquals(0, executed)
    }

    @Test
    fun comparar_conAmbosPdfs_habilitaEjecutar() {
        var executed = 0
        composeRule.setEsContent {
            ComparePdfScreen(
                pdfA = pdfUri,
                pdfB = Uri.parse("content://docsmart.test/docs/otro.pdf"),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdfA = {},
                onSelectPdfB = {},
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertExists()
        composeRule.button(s(R.string.pdf_compare_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    // ── OCR ──────────────────────────────────────────────────────────

    @Test
    fun ocr_muestraInformacionYEjecutaConPdf() {
        var executed = 0
        composeRule.setEsContent {
            OcrPdfScreen(
                selectedPdf = pdfUri,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_ocr_info)).assertExists()
        composeRule.button(s(R.string.pdf_ocr_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun ocr_sinPdfDeshabilitaYProcesandoMuestraProgreso() {
        val processing = mutableStateOf(false)
        composeRule.setEsContent {
            OcrPdfScreen(
                selectedPdf = null,
                isProcessing = processing.value,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onExecute = {},
            )
        }

        composeRule.button(s(R.string.pdf_ocr_execute)).assertIsNotEnabled()
        composeRule.runOnUiThread { processing.value = true }
        composeRule.onNodeWithText(s(R.string.pdf_ocr_progress)).assertExists()
    }

    // ── Extraer imágenes ─────────────────────────────────────────────

    @Test
    fun extraerImagenes_conPdf_ejecutaYNoMuestraSufijoPdf() {
        var executed = 0
        composeRule.setEsContent {
            ExtractImagesPdfScreen(
                selectedPdf = pdfUri,
                isProcessing = false,
                fileName = "fotos",
                onFileNameChange = {},
                onSelectPdf = {},
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_extract_images_subtitle)).assertExists()
        assertTrue(composeRule.onAllNodesWithText(".pdf").fetchSemanticsNodes().isEmpty())
        composeRule.button(s(R.string.pdf_extract_images)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun extraerImagenes_sinPdfDeshabilitaYProcesandoMuestraProgreso() {
        val processing = mutableStateOf(false)
        composeRule.setEsContent {
            ExtractImagesPdfScreen(
                selectedPdf = null,
                isProcessing = processing.value,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onExecute = {},
            )
        }

        composeRule.button(s(R.string.pdf_extract_images)).assertIsNotEnabled()
        composeRule.runOnUiThread { processing.value = true }
        composeRule.onNodeWithText(s(R.string.pdf_extract_images_progress)).assertExists()
    }

    // ── Menú de herramientas ─────────────────────────────────────────

    @Test
    fun menu_cadaTarjetaSeleccionaSuHerramienta() {
        val chosen = mutableListOf<PdfTool>()
        composeRule.setEsContent { PdfToolsMenu(onToolSelected = { chosen += it }) }

        val expected =
            listOf(
                R.string.pdf_merge to PdfTool.MERGE,
                R.string.pdf_split to PdfTool.SPLIT,
                R.string.pdf_compress to PdfTool.COMPRESS,
                R.string.pdf_rotate to PdfTool.ROTATE,
                R.string.pdf_number_pages to PdfTool.NUMBER_PAGES,
                R.string.pdf_watermark to PdfTool.WATERMARK,
                R.string.pdf_reorder_pages to PdfTool.REORDER_PAGES,
                R.string.pdf_compare to PdfTool.COMPARE,
                R.string.pdf_redact to PdfTool.REDACT,
                R.string.pdf_crop to PdfTool.CROP,
                R.string.pdf_edit_text to PdfTool.EDIT_TEXT,
                R.string.pdf_sign to PdfTool.SIGN,
                R.string.pdf_fill_form to PdfTool.FILL_FORM,
                R.string.pdf_ocr to PdfTool.OCR,
                R.string.pdf_extract_images to PdfTool.EXTRACT_IMAGES,
            )
        expected.forEach { (titleRes, _) ->
            composeRule.onNodeWithText(s(titleRes)).performScrollTo().performClick()
        }

        assertEquals(expected.map { it.second }, chosen)
    }
}

// Nodo cuya contentDescription es exactamente [text] (los botones de +/- de Dividir).
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionText(
    text: String,
): androidx.compose.ui.test.SemanticsNodeInteraction {
    return onNode(androidx.compose.ui.test.hasContentDescription(text))
}
