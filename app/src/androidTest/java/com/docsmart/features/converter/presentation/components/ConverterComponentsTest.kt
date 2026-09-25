package com.docsmart.features.converter.presentation.components

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.pdftools.presentation.components.RecordingContext
import com.docsmart.features.pdftools.presentation.components.esContext
import com.docsmart.features.pdftools.presentation.components.setEsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tarjetas de resultado y progreso del Convertidor, renderizadas con estado
 * fijo (sin ViewModel). "Compartir" se verifica con un contexto que registra
 * los Intents en vez de lanzarlos (no se abre el selector real del sistema).
 */
class ConverterComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings = esContext()

    // Archivo real dentro de filesDir/converted (ruta declarada en el FileProvider).
    private fun outputFile(name: String): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(appContext.filesDir, "converted").apply { mkdirs() }
        return File(dir, name).apply { writeText("contenido") }
    }

    private fun missingFile(name: String): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        return File(File(appContext.filesDir, "converted"), name)
    }

    // Hallazgo de seguridad 2026-09-16 (ver file_provider_paths.xml): "secure/"
    // se dejó deliberadamente fuera de las carpetas declaradas al FileProvider
    // -- un archivo real ahí existe en disco, pero FileProvider.getUriForFile()
    // lanza IllegalArgumentException al no encontrar una raíz configurada para
    // esa ruta. Sirve para ejercitar esa rama de shareFile()/shareFiles() sin
    // simular nada, es el mismo camino real que protege la Carpeta Segura.
    private fun fileOutsideProviderPaths(name: String): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(appContext.filesDir, "secure").apply { mkdirs() }
        return File(dir, name).apply { writeText("contenido") }
    }

    @Suppress("DEPRECATION")
    private fun innerIntent(chooser: Intent): Intent? = chooser.getParcelableExtra(Intent.EXTRA_INTENT)

    // ── ConversionProgress ───────────────────────────────────────────

    @Test
    fun progreso_muestraTituloSubtituloYPie() {
        composeRule.setEsContent { ConversionProgress(totalImages = 4) }

        composeRule.onNodeWithText(strings.getString(R.string.converter_progress_title)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_progress_subtitle, 4)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_progress_footer)).assertExists()
    }

    // ── ConversionSuccess ────────────────────────────────────────────

    @Test
    fun exito_muestraDetallesYAccionesInvocanCallbacks() {
        val file = outputFile("resultado.pdf")
        var opened = 0
        var saved = 0
        var another = 0
        composeRule.setEsContent {
            ConversionSuccess(
                result = ConversionResult.Success(outputFile = file, pageCount = 3, fileSizeKb = 12),
                savedToDownloads = false,
                onConvertAnother = { another++ },
                onSaveToDownloads = { saved++ },
                onOpenDocument = { opened++ },
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_success_title)).assertExists()
        composeRule.onNodeWithText("resultado.pdf").assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_success_page_count_size, 3, 12)).assertExists()
        // Sin archivos extra no se muestra la línea "+N archivo(s) más".
        composeRule.onNodeWithText(strings.getString(R.string.converter_success_extra_files, 1)).assertDoesNotExist()

        composeRule.onNodeWithText(strings.getString(R.string.converter_view_document)).performScrollTo().performClick()
        composeRule.onNodeWithText(strings.getString(R.string.converter_save)).performScrollTo().performClick()
        composeRule.onNodeWithText(strings.getString(R.string.converter_convert_another)).performScrollTo().performClick()

        assertEquals(1, opened)
        assertEquals(1, saved)
        assertEquals(1, another)
    }

    @Test
    fun exito_conArchivosExtra_muestraCantidadAdicional() {
        val file = outputFile("pagina1.png")
        val extras = listOf(outputFile("pagina2.png"), outputFile("pagina3.png"))
        composeRule.setEsContent {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 3, fileSizeKb = 40, extraFiles = extras),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_success_extra_files, 2)).assertExists()
    }

    @Test
    fun exito_guardado_ocultaGuardarYMuestraConfirmacion() {
        val file = outputFile("guardado.pdf")
        composeRule.setEsContent {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 1, fileSizeKb = 1),
                savedToDownloads = true,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_saved_to_downloads)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_save)).assertDoesNotExist()
    }

    @Test
    fun exito_guardando_deshabilitaElBotonGuardar() {
        val file = outputFile("guardando.pdf")
        composeRule.setEsContent {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 1, fileSizeKb = 1),
                savedToDownloads = false,
                isSaving = true,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_save)).assertIsNotEnabled()
        composeRule.onNodeWithText(strings.getString(R.string.converter_view_document)).assertIsEnabled()
    }

    @Test
    fun exito_compartirUnArchivo_lanzaSelectorConActionSend() {
        val file = outputFile("compartir.pdf")
        val recording = RecordingContext(esContext())
        composeRule.setEsContent(overrideContext = recording) {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 1, fileSizeKb = 1),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_share)).performScrollTo().performClick()

        assertEquals(1, recording.startedIntents.size)
        val chooser = recording.startedIntents.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val inner = innerIntent(chooser)
        assertNotNull(inner)
        assertEquals(Intent.ACTION_SEND, inner?.action)
    }

    @Test
    fun exito_compartirVariosArchivos_usaActionSendMultiple() {
        val file = outputFile("multi1.png")
        val extras = listOf(outputFile("multi2.png"))
        val recording = RecordingContext(esContext())
        composeRule.setEsContent(overrideContext = recording) {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 2, fileSizeKb = 5, extraFiles = extras),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_share)).performScrollTo().performClick()

        val inner = innerIntent(recording.startedIntents.single())
        assertEquals(Intent.ACTION_SEND_MULTIPLE, inner?.action)
    }

    @Test
    fun exito_compartirArchivosInexistentes_noLanzaNada() {
        val recording = RecordingContext(esContext())
        composeRule.setEsContent(overrideContext = recording) {
            // Un solo archivo inexistente (shareFile) y luego varios (shareFiles).
            ConversionSuccess(
                result = ConversionResult.Success(missingFile("no1.pdf"), pageCount = 1, fileSizeKb = 1),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
            ConversionSuccess(
                result =
                    ConversionResult.Success(
                        missingFile("no2.png"),
                        pageCount = 2,
                        fileSizeKb = 1,
                        extraFiles = listOf(missingFile("no3.png")),
                    ),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        val shareLabel = strings.getString(R.string.converter_share)
        composeRule.onAllNodesWithText(shareLabel)[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText(shareLabel)[1].performScrollTo().performClick()

        assertTrue(recording.startedIntents.isEmpty())
    }

    @Test
    fun exito_compartirArchivoFueraDeLasRutasDelFileProvider_noLanzaNadaYNoCrashea() {
        val file = fileOutsideProviderPaths("bloqueado.pdf")
        val recording = RecordingContext(esContext())
        composeRule.setEsContent(overrideContext = recording) {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 1, fileSizeKb = 1),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_share)).performScrollTo().performClick()

        assertTrue(recording.startedIntents.isEmpty())
    }

    @Test
    fun exito_compartirVariosArchivosConUnoFueraDeLasRutasDelFileProvider_abortaSinLanzarNada() {
        val file = outputFile("multi_ok.png")
        val extras = listOf(fileOutsideProviderPaths("multi_bloqueado.png"))
        val recording = RecordingContext(esContext())
        composeRule.setEsContent(overrideContext = recording) {
            ConversionSuccess(
                result = ConversionResult.Success(file, pageCount = 2, fileSizeKb = 5, extraFiles = extras),
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_share)).performScrollTo().performClick()

        assertTrue(recording.startedIntents.isEmpty())
    }

    // ── BatchConversionSuccess ───────────────────────────────────────

    private fun mixedBatch(): List<BatchConversionItem> {
        return listOf(
            BatchConversionItem(
                originalFileName = "informe.docx",
                result = ConversionResult.Success(outputFile("informe.pdf"), pageCount = 2, fileSizeKb = 8),
            ),
            BatchConversionItem(
                originalFileName = "roto.docx",
                result = ConversionResult.Error("Archivo dañado"),
            ),
        )
    }

    @Test
    fun lote_mixto_muestraResumenFilasYAccionesGenerales() {
        var saveAll = 0
        var another = 0
        composeRule.setEsContent {
            BatchConversionSuccess(
                items = mixedBatch(),
                savedToDownloads = false,
                onConvertAnother = { another++ },
                onSaveAllToDownloads = { saveAll++ },
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_success_title, 1, 2)).assertExists()
        composeRule.onNodeWithText("informe.docx").assertExists()
        composeRule.onNodeWithText("informe.pdf").assertExists()
        composeRule.onNodeWithText("roto.docx").assertExists()
        composeRule.onNodeWithText("Archivo dañado").assertExists()

        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_save_all)).performScrollTo().performClick()
        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_convert_another))
            .performScrollTo()
            .performClick()

        assertEquals(1, saveAll)
        assertEquals(1, another)
    }

    @Test
    fun lote_filaExitosa_verDocumentoYCompartir() {
        val items = mixedBatch()
        val expectedFile = (items[0].result as ConversionResult.Success).outputFile
        val recording = RecordingContext(esContext())
        val opened = mutableListOf<File>()
        composeRule.setEsContent(overrideContext = recording) {
            BatchConversionSuccess(
                items = items,
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveAllToDownloads = {},
                onOpenDocument = { opened += it },
            )
        }

        composeRule.onAllNodesWithContentDescription(strings.getString(R.string.converter_view_document))[0]
            .performClick()
        composeRule.onAllNodesWithContentDescription(strings.getString(R.string.converter_share))[0].performClick()

        assertEquals(listOf(expectedFile), opened)
        assertEquals(Intent.ACTION_CHOOSER, recording.startedIntents.single().action)
    }

    @Test
    fun lote_guardado_ocultaGuardarTodasYMuestraConfirmacion() {
        composeRule.setEsContent {
            BatchConversionSuccess(
                items = mixedBatch(),
                savedToDownloads = true,
                onConvertAnother = {},
                onSaveAllToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_saved_to_downloads)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_save_all)).assertDoesNotExist()
    }

    @Test
    fun lote_sinExitos_noOfreceGuardarTodas() {
        val items =
            listOf(
                BatchConversionItem("a.docx", ConversionResult.Error("Falló A")),
                BatchConversionItem("b.docx", ConversionResult.Loading),
            )
        composeRule.setEsContent {
            BatchConversionSuccess(
                items = items,
                savedToDownloads = false,
                onConvertAnother = {},
                onSaveAllToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_success_title, 0, 2)).assertExists()
        composeRule.onNodeWithText("Falló A").assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_save_all)).assertDoesNotExist()
        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_convert_another)).assertExists()
    }

    @Test
    fun lote_guardando_deshabilitaGuardarTodas() {
        composeRule.setEsContent {
            BatchConversionSuccess(
                items = mixedBatch(),
                savedToDownloads = false,
                isSaving = true,
                onConvertAnother = {},
                onSaveAllToDownloads = {},
                onOpenDocument = {},
            )
        }

        composeRule.onNodeWithText(strings.getString(R.string.converter_batch_save_all)).assertIsNotEnabled()
    }
}
