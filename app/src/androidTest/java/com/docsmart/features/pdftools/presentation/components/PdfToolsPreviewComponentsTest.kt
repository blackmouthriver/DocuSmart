package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.center
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.width
import com.docsmart.R
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.pdftools.domain.usecase.FormFieldInfo
import com.docsmart.features.pdftools.domain.usecase.RedactionRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pantallas de Herramientas PDF con vista previa o gestos (Recortar, Rotar,
 * Censurar, Reordenar, Firmar) y Rellenar formulario. Las vistas previas se
 * renderizan sobre un PDF real y liviano creado en cacheDir (ejerce el
 * PdfRenderer real); los gestos de arrastre se inyectan con swipe().
 */
class PdfToolsPreviewComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings = esContext()

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = if (args.isEmpty()) strings.getString(id) else strings.getString(id, *args)

    // Uri sin E/S posible: la carga de la vista previa falla y la pantalla queda en su estado "cargando".
    private val unreadableUri: Uri = Uri.parse("content://docsmart.test/docs/ilegible.pdf")

    private fun slider() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun waitForContentDescription(
        tag: String,
        description: String,
    ) {
        composeRule.waitUntilOrDump(tag, timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // La lista de Reordenar se oculta mientras carga las miniaturas: primero se espera el callback
    // de páginas (llega dentro de la carga) y luego a que la lista vuelva a estar visible.
    private fun waitForReorderList(
        loadedPages: AtomicInteger,
        expectedPages: Int,
    ) {
        composeRule.waitUntilOrDump("R18_Reorder_carga", timeoutMillis = 15_000) {
            loadedPages.get() == expectedPages
        }
        composeRule.waitUntilOrDump("R18_Reorder_lista", timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText(s(R.string.pdf_reorder_pages_page_label, 1))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Recortar ─────────────────────────────────────────────────────

    @Test
    fun recortar_sinPdf_muestraMarcadorYDeshabilitaElBoton() {
        composeRule.setEsContent {
            CropPdfScreen(
                selectedPdf = null,
                marginPercent = 10,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onMarginChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_crop_preview_placeholder)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_crop_margin_label, 10)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_crop_execute)).assertIsNotEnabled()
    }

    @Test
    fun recortar_conPdfReal_cargaVistaPreviaYPropagaMargenYEjecutar() {
        val pdf = Uri.fromFile(createTestPdf(pages = 1))
        var margin = -1
        var executed = 0
        composeRule.setEsContent {
            CropPdfScreen(
                selectedPdf = pdf,
                marginPercent = 40,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onMarginChange = { margin = it },
                onExecute = { executed++ },
            )
        }

        waitForContentDescription("R18_Crop_preview", s(R.string.pdf_crop_preview_desc))

        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        assertTrue("margen=$margin", margin in 14..16)
        composeRule.button(s(R.string.pdf_crop_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun recortar_pdfIlegible_quedaEnCargandoYProcesandoMuestraProgreso() {
        composeRule.setEsContent {
            CropPdfScreen(
                selectedPdf = unreadableUri,
                marginPercent = 0,
                isProcessing = true,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onMarginChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_crop_loading_preview)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_crop_progress)).assertExists()
    }

    // ── Rotar ────────────────────────────────────────────────────────

    @Test
    fun rotar_sinPdf_muestraMarcadorYDeshabilitaElBoton() {
        composeRule.setEsContent {
            RotatePdfScreen(
                selectedPdf = null,
                degrees = 90,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDegreesChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_rotate_preview_placeholder)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_rotate_angle_90)).assertExists()
        composeRule.button(s(R.string.pdf_rotate_execute, 90)).assertIsNotEnabled()
    }

    @Test
    fun rotar_conPdfReal_cargaVistaPreviaYRecorreLosAngulos() {
        val pdf = Uri.fromFile(createTestPdf(pages = 1))
        val degrees = mutableIntStateOf(180)
        val chosen = mutableListOf<Int>()
        var executed = 0
        composeRule.setEsContent {
            RotatePdfScreen(
                selectedPdf = pdf,
                degrees = degrees.intValue,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDegreesChange = { chosen += it },
                onExecute = { executed++ },
            )
        }

        waitForContentDescription("R18_Rotate_preview", s(R.string.pdf_rotate_preview_desc))
        composeRule.onNodeWithText(s(R.string.pdf_rotate_angle_180)).assertExists()

        composeRule.onNodeWithText("90°").performScrollTo().performClick()
        composeRule.onNodeWithText("270°").performScrollTo().performClick()
        assertEquals(listOf(90, 270), chosen)

        composeRule.runOnUiThread { degrees.intValue = 270 }
        composeRule.onNodeWithText(s(R.string.pdf_rotate_angle_270)).assertExists()
        composeRule.runOnUiThread { degrees.intValue = 90 }
        composeRule.onNodeWithText(s(R.string.pdf_rotate_angle_90)).assertExists()
        waitForContentDescription("R18_Rotate_preview90", s(R.string.pdf_rotate_preview_desc))

        composeRule.button(s(R.string.pdf_rotate_execute, 90)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun rotar_procesando_muestraProgresoConElAngulo() {
        composeRule.setEsContent {
            RotatePdfScreen(
                selectedPdf = unreadableUri,
                degrees = 270,
                isProcessing = true,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDegreesChange = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_rotate_loading_preview)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_rotate_progress, 270)).assertExists()
    }

    // ── Censurar ─────────────────────────────────────────────────────

    @Test
    fun censurar_sinPdf_noMuestraElEditorNiHabilitaElBoton() {
        composeRule.setEsContent {
            RedactPdfScreen(
                selectedPdf = null,
                currentPage = 1,
                totalPages = 0,
                rects = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = {},
                onPageChange = {},
                onAddRect = {},
                onUndoLastRect = {},
                onClearRects = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_redact_draw_hint)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_redact_execute)).assertIsNotEnabled()
    }

    @Test
    fun censurar_pdfIlegible_muestraCargandoYNavegacionDeshabilitada() {
        composeRule.setEsContent {
            RedactPdfScreen(
                selectedPdf = unreadableUri,
                currentPage = 1,
                totalPages = 1,
                rects = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = {},
                onPageChange = {},
                onAddRect = {},
                onUndoLastRect = {},
                onClearRects = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_redact_loading_preview)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_redact_page_indicator, 1, 1)).assertExists()
        composeRule.onNodeWithContentDescription(s(R.string.pdf_redact_prev_page)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(s(R.string.pdf_redact_next_page)).assertIsNotEnabled()
        composeRule.onNodeWithText(s(R.string.pdf_redact_zones_marked, 0)).assertExists()
    }

    @Test
    fun censurar_conPdfReal_navegaPaginasYDeshaceOBorraZonas() {
        val pdf = Uri.fromFile(createTestPdf(pages = 3))
        val rects =
            listOf(
                RedactionRect(pageNumber = 2, xFrac = 0.1f, yFrac = 0.1f, wFrac = 0.3f, hFrac = 0.3f),
                RedactionRect(pageNumber = 1, xFrac = 0.2f, yFrac = 0.2f, wFrac = 0.2f, hFrac = 0.2f),
            )
        val loadedPages = AtomicInteger(-1)
        val pageChanges = mutableListOf<Int>()
        var undo = 0
        var clear = 0
        var executed = 0
        composeRule.setEsContent {
            RedactPdfScreen(
                selectedPdf = pdf,
                currentPage = 2,
                totalPages = 3,
                rects = rects,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = { loadedPages.set(it) },
                onPageChange = { pageChanges += it },
                onAddRect = {},
                onUndoLastRect = { undo++ },
                onClearRects = { clear++ },
                onExecute = { executed++ },
            )
        }

        // En la página 2 solo hay una zona marcada (la otra es de la página 1).
        waitForContentDescription("R18_Redact_canvas", s(R.string.pdf_redact_canvas_desc, 1))
        assertEquals(3, loadedPages.get())
        composeRule.onNodeWithText(s(R.string.pdf_redact_page_indicator, 2, 3)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_redact_zones_marked, 2)).assertExists()

        composeRule.onNodeWithContentDescription(s(R.string.pdf_redact_prev_page)).performClick()
        composeRule.onNodeWithContentDescription(s(R.string.pdf_redact_next_page)).performClick()
        assertEquals(listOf(1, 3), pageChanges)

        composeRule.onNodeWithText(s(R.string.pdf_redact_undo)).performScrollTo().performClick()
        composeRule.onNodeWithText(s(R.string.pdf_redact_clear_all)).performScrollTo().performClick()
        assertEquals(1, undo)
        assertEquals(1, clear)

        composeRule.button(s(R.string.pdf_redact_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun censurar_sinZonas_deshaceYBorrarEstanDeshabilitados() {
        val pdf = Uri.fromFile(createTestPdf(pages = 1))
        composeRule.setEsContent {
            RedactPdfScreen(
                selectedPdf = pdf,
                currentPage = 1,
                totalPages = 1,
                rects = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = {},
                onPageChange = {},
                onAddRect = {},
                onUndoLastRect = {},
                onClearRects = {},
                onExecute = {},
            )
        }

        waitForContentDescription("R18_Redact_empty", s(R.string.pdf_redact_canvas_desc, 0))
        composeRule.onNodeWithText(s(R.string.pdf_redact_undo)).assertIsNotEnabled()
        composeRule.onNodeWithText(s(R.string.pdf_redact_clear_all)).assertIsNotEnabled()
        composeRule.button(s(R.string.pdf_redact_execute)).assertIsNotEnabled()
    }

    @Test
    fun censurar_arrastrarSobreLaVistaPrevia_agregaUnaZonaEnFracciones() {
        val pdf = Uri.fromFile(createTestPdf(pages = 3))
        val added = mutableListOf<RedactionRect>()
        composeRule.setEsContent {
            RedactPdfScreen(
                selectedPdf = pdf,
                currentPage = 2,
                totalPages = 3,
                rects = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = {},
                onPageChange = {},
                onAddRect = { added += it },
                onUndoLastRect = {},
                onClearRects = {},
                onExecute = {},
            )
        }

        val canvasDescription = s(R.string.pdf_redact_canvas_desc, 0)
        waitForContentDescription("R18_Redact_drag", canvasDescription)
        val canvas = composeRule.onNode(hasContentDescription(canvasDescription))

        // Un arrastre horizontal (alto ~0) es demasiado fino y se descarta.
        canvas.performScrollTo().performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.6f, height * 0.5f), 300)
        }
        assertTrue(added.isEmpty())

        canvas.performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.2f), Offset(width * 0.6f, height * 0.6f), 300)
        }
        assertEquals(1, added.size)
        val rect = added.single()
        assertEquals(2, rect.pageNumber)
        assertTrue("wFrac=${rect.wFrac}", rect.wFrac in 0.2f..0.5f)
        assertTrue("hFrac=${rect.hFrac}", rect.hFrac in 0.2f..0.5f)
    }

    // ── Reordenar ────────────────────────────────────────────────────

    @Test
    fun reordenar_sinPdf_noMuestraListaNiHabilitaElBoton() {
        composeRule.setEsContent {
            ReorderPagesScreen(
                selectedPdf = null,
                pageOrder = emptyList(),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onPagesLoaded = {},
                onReorder = { _, _ -> },
                onRemovePage = {},
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_reorder_pages_hint)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_reorder_pages_execute)).assertIsNotEnabled()
    }

    @Test
    fun reordenar_conPdfReal_listaPaginasQuitaYEjecuta() {
        val pdf = Uri.fromFile(createTestPdf(pages = 3))
        val loadedPages = AtomicInteger(-1)
        val removed = mutableListOf<Int>()
        var executed = 0
        composeRule.setEsContent {
            ReorderPagesScreen(
                selectedPdf = pdf,
                pageOrder = listOf(1, 2, 3),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onPagesLoaded = { loadedPages.set(it) },
                onReorder = { _, _ -> },
                onRemovePage = { removed += it },
                onExecute = { executed++ },
            )
        }

        waitForReorderList(loadedPages, expectedPages = 3)
        assertEquals(3, loadedPages.get())
        composeRule.onNodeWithText(s(R.string.pdf_reorder_pages_page_label, 2)).assertExists()

        composeRule.onAllNodesWithContentDescription(s(R.string.general_delete))[1].performClick()
        assertEquals(listOf(2), removed)

        composeRule.button(s(R.string.pdf_reorder_pages_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun reordenar_arrastrarElAsa_pideMoverLaPagina() {
        val pdf = Uri.fromFile(createTestPdf(pages = 3))
        val loadedPages = AtomicInteger(-1)
        val reorders = mutableListOf<Pair<Int, Int>>()
        composeRule.setEsContent {
            ReorderPagesScreen(
                selectedPdf = pdf,
                pageOrder = listOf(1, 2, 3),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onPagesLoaded = { loadedPages.set(it) },
                onReorder = { from, to -> reorders += from to to },
                onRemovePage = {},
                onExecute = {},
            )
        }

        waitForReorderList(loadedPages, expectedPages = 3)
        composeRule.onAllNodesWithContentDescription(s(R.string.pdf_reorder_pages_drag_handle_desc))[0]
            .performScrollTo()
            .performTouchInput { swipe(center, Offset(center.x, center.y + 300f), 400) }

        assertTrue("sin llamadas a onReorder", reorders.isNotEmpty())
        assertEquals(0 to 1, reorders.first())
    }

    @Test
    fun reordenar_conUnaSolaPagina_noPermiteQuitarNiEjecutar() {
        val pdf = Uri.fromFile(createTestPdf(pages = 1))
        val loadedPages = AtomicInteger(-1)
        composeRule.setEsContent {
            ReorderPagesScreen(
                selectedPdf = pdf,
                pageOrder = listOf(1),
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onPagesLoaded = { loadedPages.set(it) },
                onReorder = { _, _ -> },
                onRemovePage = {},
                onExecute = {},
            )
        }

        waitForReorderList(loadedPages, expectedPages = 1)
        composeRule.onAllNodesWithContentDescription(s(R.string.general_delete))[0].assertIsNotEnabled()
        composeRule.button(s(R.string.pdf_reorder_pages_execute)).assertIsNotEnabled()
    }

    // ── Firmar ───────────────────────────────────────────────────────

    private fun signContent(
        pdf: Uri?,
        hasSignature: Boolean,
        isProcessing: Boolean = false,
        onTotalPages: (Int) -> Unit = {},
        onPageChange: (Int) -> Unit = {},
        onCaptured: (ByteArray) -> Unit = {},
        onClear: () -> Unit = {},
        onExecute: () -> Unit = {},
    ) {
        composeRule.setEsContent {
            SignPdfScreen(
                selectedPdf = pdf,
                pageNumber = 1,
                totalPages = 3,
                hasSignature = hasSignature,
                isProcessing = isProcessing,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onTotalPagesLoaded = onTotalPages,
                onPageChange = onPageChange,
                onSignatureCaptured = onCaptured,
                onClearSignature = onClear,
                onExecute = onExecute,
            )
        }
    }

    @Test
    fun firmar_sinPdf_noMuestraElLienzoNiHabilitaElBoton() {
        signContent(pdf = null, hasSignature = false)

        composeRule.onNodeWithText(s(R.string.pdf_sign_draw_label)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_sign_execute)).assertIsNotEnabled()
    }

    @Test
    fun firmar_conPdfReal_cargaTotalDePaginasYNavega() {
        val pdf = Uri.fromFile(createTestPdf(pages = 3))
        val totalPages = AtomicInteger(-1)
        val pageChanges = mutableListOf<Int>()
        signContent(
            pdf = pdf,
            hasSignature = false,
            onTotalPages = { totalPages.set(it) },
            onPageChange = { pageChanges += it },
        )

        composeRule.waitUntilOrDump("R18_Sign_paginas", timeoutMillis = 15_000) { totalPages.get() == 3 }
        composeRule.onNodeWithText(s(R.string.pdf_sign_page_indicator, 1, 3)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_sign_hint)).assertExists()
        composeRule.onNodeWithContentDescription(s(R.string.pdf_sign_prev_page)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(s(R.string.pdf_sign_next_page)).performClick()
        assertEquals(listOf(2), pageChanges)
        composeRule.onNodeWithText(s(R.string.pdf_sign_clear)).assertIsNotEnabled()
        composeRule.button(s(R.string.pdf_sign_execute)).assertIsNotEnabled()
    }

    @Test
    fun firmar_dibujarEnElLienzo_capturaUnPng() {
        val captured = mutableListOf<ByteArray>()
        signContent(pdf = unreadableUri, hasSignature = false, onCaptured = { captured += it })

        composeRule.onNode(hasContentDescription(s(R.string.pdf_sign_canvas_desc)))
            .performScrollTo()
            .performTouchInput { swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), 300) }

        assertEquals(1, captured.size)
        val png = captured.single()
        // Firma PNG: 0x89 'P' 'N' 'G'.
        assertTrue(png.size > 8)
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])
    }

    @Test
    fun firmar_escribirElNombre_capturaUnPngYVaciarLoLimpia() {
        val captured = mutableListOf<ByteArray>()
        var cleared = 0
        signContent(
            pdf = unreadableUri,
            hasSignature = false,
            onCaptured = { captured += it },
            onClear = { cleared++ },
        )

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Ana Perez")
        assertEquals(1, captured.size)
        assertTrue(captured.single().size > 8)
        assertEquals(0, cleared)
    }

    @Test
    fun firmar_textoSoloConCaracteresInvisibles_seTrataComoVacio() {
        val captured = mutableListOf<ByteArray>()
        var cleared = 0
        signContent(
            pdf = unreadableUri,
            hasSignature = false,
            onCaptured = { captured += it },
            onClear = { cleared++ },
        )

        // Espacio de ancho cero (categoría Cf): no cuenta como contenido.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("​")

        assertTrue(captured.isEmpty())
        assertEquals(1, cleared)
    }

    @Test
    fun firmar_conFirmaCapturada_permiteBorrarYEjecutar() {
        var cleared = 0
        var executed = 0
        signContent(
            pdf = unreadableUri,
            hasSignature = true,
            onClear = { cleared++ },
            onExecute = { executed++ },
        )

        composeRule.onNodeWithText(s(R.string.pdf_sign_captured)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_sign_clear)).assertIsEnabled().performScrollTo().performClick()
        assertEquals(1, cleared)
        composeRule.button(s(R.string.pdf_sign_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }

    @Test
    fun firmar_procesando_muestraProgreso() {
        signContent(pdf = unreadableUri, hasSignature = true, isProcessing = true)

        composeRule.onNodeWithText(s(R.string.pdf_sign_progress)).assertExists()
    }

    // ── Rellenar formulario ──────────────────────────────────────────

    private val pdfUri: Uri = Uri.parse("content://docsmart.test/docs/formulario.pdf")

    @Test
    fun formulario_sinPdf_noDetectaNiMuestraCampos() {
        val detected = mutableListOf<Uri>()
        composeRule.setEsContent {
            FillFormScreen(
                selectedPdf = null,
                formFields = emptyList(),
                formFieldValues = emptyMap(),
                formFieldsDetected = false,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDetectFields = { detected += it },
                onFieldValueChange = { _, _ -> },
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_fill_form_detecting)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_fill_form_execute)).assertIsNotEnabled()
        assertTrue(detected.isEmpty())
    }

    @Test
    fun formulario_detectando_pideDetectarCamposYMuestraElProgreso() {
        val detected = mutableListOf<Uri>()
        composeRule.setEsContent {
            FillFormScreen(
                selectedPdf = pdfUri,
                formFields = emptyList(),
                formFieldValues = emptyMap(),
                formFieldsDetected = false,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDetectFields = { detected += it },
                onFieldValueChange = { _, _ -> },
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_fill_form_detecting)).assertExists()
        assertEquals(listOf(pdfUri), detected)
        composeRule.button(s(R.string.pdf_fill_form_execute)).assertIsNotEnabled()
    }

    @Test
    fun formulario_sinCampos_muestraElAviso() {
        composeRule.setEsContent {
            FillFormScreen(
                selectedPdf = pdfUri,
                formFields = emptyList(),
                formFieldValues = emptyMap(),
                formFieldsDetected = true,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDetectFields = {},
                onFieldValueChange = { _, _ -> },
                onExecute = {},
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_fill_form_no_fields)).assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertDoesNotExist()
        composeRule.button(s(R.string.pdf_fill_form_execute)).assertIsNotEnabled()
    }

    @Test
    fun formulario_conCampos_muestraValoresPropagaCambiosYEjecuta() {
        val fields = listOf(FormFieldInfo("Nombre", "Juan"), FormFieldInfo("Ciudad", "Lima"))
        val changes = mutableListOf<Pair<String, String>>()
        var executed = 0
        composeRule.setEsContent {
            FillFormScreen(
                selectedPdf = pdfUri,
                formFields = fields,
                // El valor editado prevalece sobre el actual del PDF.
                formFieldValues = mapOf("Nombre" to "Ana"),
                formFieldsDetected = true,
                isProcessing = false,
                fileName = "",
                onFileNameChange = {},
                onSelectPdf = {},
                onDetectFields = {},
                onFieldValueChange = { name, value -> changes += name to value },
                onExecute = { executed++ },
            )
        }

        composeRule.onNodeWithText(s(R.string.pdf_fill_form_fields_title, 2)).assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Lima").assertExists()
        composeRule.onNodeWithText(s(R.string.pdf_tools_filename_label)).assertExists()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Z")
        assertEquals("Nombre", changes.single().first)
        assertTrue(changes.single().second.contains("Z"))

        composeRule.button(s(R.string.pdf_fill_form_execute)).performScrollTo().performClick()
        assertEquals(1, executed)
    }
}
