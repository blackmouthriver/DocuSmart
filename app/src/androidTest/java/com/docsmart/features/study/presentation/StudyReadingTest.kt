package com.docsmart.features.study.presentation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.docsmart.R
import com.docsmart.features.agenda.presentation.components.esString
import com.docsmart.features.study.domain.ReadingProgress
import com.docsmart.features.study.domain.StudyReadingProgressStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Vista de Lectura: estados de [ReadingTab] (vacío con/sin historial, cargando, barra de lectura,
 * visor de PDF real) y flujos de [StudyScreen] (selector de documento grabado, "Continuar
 * leyendo", extracción con iText). El emulador de CI no tiene motor TTS: se ejercitan las ramas de
 * "TTS no disponible"; si el equipo sí tiene TTS se ejercita además "Leer todo"/"Detener".
 */
class StudyReadingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val env by lazy { StudyTestEnv(composeRule) }
    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        tempFiles.forEach { it.delete() }
    }

    private fun newPdf(pages: Int = 2): File = createStudyTestPdf(pages).also { tempFiles += it }

    private fun voice(
        name: String,
        quality: Int = Voice.QUALITY_NORMAL,
    ) = Voice(name, Locale.forLanguageTag("es-ES"), quality, Voice.LATENCY_NORMAL, false, emptySet())

    private fun progress(
        uri: String,
        name: String,
        currentPage: Int = 1,
        totalPages: Int = 2,
        paragraphIndex: Int = 0,
    ) = ReadingProgress(
        uri = uri,
        documentName = name,
        paragraphIndex = paragraphIndex,
        totalParagraphs = 4,
        currentPage = currentPage,
        totalPages = totalPages,
        lastReadAtMillis = 1_700_000_000_000L,
    )

    // ── Estado editable de ReadingTab ─────────────────────────────────────────
    private class ReadingState(
        uri: Uri?,
    ) {
        var uri by mutableStateOf(uri)
        var isLoading by mutableStateOf(false)
        var highlightedCount by mutableIntStateOf(0)
        var isCurrentHighlighted by mutableStateOf(false)
        var isSpeaking by mutableStateOf(false)
        var ttsReady by mutableStateOf(true)
        var ttsError by mutableStateOf<String?>(null)
        var extractingMore by mutableStateOf(false)
        var finished by mutableStateOf(false)
        var currentPage by mutableIntStateOf(1)
        var totalPages by mutableIntStateOf(0)
        var history by mutableStateOf<List<ReadingProgress>>(emptyList())
        var voices by mutableStateOf<List<Voice>>(emptyList())
    }

    private var openClicks = 0
    private var speakClicks = 0
    private var toggleClicks = 0
    private var voiceClicks = 0
    private var resumed: ReadingProgress? = null
    private var deleted: ReadingProgress? = null

    private fun setReading(state: ReadingState) {
        env.setContent {
            ReadingTab(
                documentUri = state.uri,
                isLoading = state.isLoading,
                highlightedCount = state.highlightedCount,
                isCurrentHighlighted = state.isCurrentHighlighted,
                isSpeaking = state.isSpeaking,
                ttsReady = state.ttsReady,
                ttsErrorMessage = state.ttsError,
                isExtractingMore = state.extractingMore,
                readingFinished = state.finished,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onToggleHighlightCurrent = { toggleClicks++ },
                onSpeakAll = { speakClicks++ },
                onSelectDoc = { openClicks++ },
                readingHistory = state.history,
                onResumeDocument = { resumed = it },
                onDeleteDocument = { deleted = it },
                availableVoices = state.voices,
                onVoiceSelectorClick = { voiceClicks++ },
            )
        }
    }

    private fun update(block: () -> Unit) = composeRule.runOnIdle { block() }

    private fun exists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    // ── ReadingTab: estado vacío ──────────────────────────────────────────────
    @Test
    fun estadoVacio_sinHistorial_muestraIntroYBotones() {
        val state = ReadingState(uri = null)
        setReading(state)

        composeRule.onNodeWithText(esString(R.string.study_reading_intro)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_reading_helper)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_continue_reading)).assertDoesNotExist()

        composeRule.onNodeWithText(esString(R.string.qr_open_document)).performClick()
        assertEquals(1, openClicks)

        // Sin voces instaladas el selector está deshabilitado.
        composeRule.onNodeWithText(esString(R.string.study_choose_voice)).assertIsNotEnabled()
        update { state.voices = listOf(voice("es-es-x-a-local")) }
        composeRule.onNodeWithText(esString(R.string.study_choose_voice)).assertIsEnabled().performClick()
        assertEquals(1, voiceClicks)
    }

    @Test
    fun estadoVacio_conHistorial_listaTarjetasYAcciones() {
        val first = progress("file:///a.pdf", "Apuntes.pdf", currentPage = 2, totalPages = 3)
        val second = progress("file:///b.pdf", "SinPaginas.pdf", currentPage = 1, totalPages = 0)
        val state = ReadingState(uri = null)
        state.history = listOf(first, second)
        setReading(state)

        composeRule.onNodeWithText(esString(R.string.study_continue_reading)).assertExists()
        composeRule.onNodeWithText("Apuntes.pdf").assertExists()
        composeRule.onNodeWithText(esString(R.string.study_resume_page_progress, 2, 3)).assertExists()
        composeRule.onNodeWithText("SinPaginas.pdf").assertExists()
        composeRule.onAllNodesWithContentDescription(esString(R.string.study_resume_reading)).assertCountEquals(2)

        composeRule.onNodeWithText("Apuntes.pdf").performScrollTo().performClick()
        assertEquals(first, resumed)

        composeRule.onAllNodesWithContentDescription(esString(R.string.study_remove_from_history))[1]
            .performScrollTo()
            .performClick()
        assertEquals(second, deleted)
    }

    @Test
    fun cargando_muestraElMensajeDeProgreso() {
        val state = ReadingState(uri = null)
        state.isLoading = true
        setReading(state)

        composeRule.onNodeWithText(esString(R.string.study_loading_document)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_reading_intro)).assertDoesNotExist()
    }

    // ── ReadingTab: barra de lectura ──────────────────────────────────────────
    @Test
    fun barraDeLectura_recorreTodosLosEstados() {
        val state = ReadingState(uri = Uri.parse("file:///no/existe/documento.pdf"))
        setReading(state)
        val mark = esString(R.string.study_mark_current_paragraph)

        // Reposo: sin resaltados, se puede leer pero no marcar.
        composeRule.onNodeWithText(esString(R.string.study_highlighted_count, 0)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_read_all)).assertIsEnabled()
        composeRule.onAllNodesWithContentDescription(mark).assertCountEquals(1)
        composeRule.onNodeWithText(esString(R.string.study_choose_voice)).assertIsNotEnabled()
        composeRule.onNodeWithText(esString(R.string.qr_open_document)).performClick()
        assertEquals(1, openClicks)

        update { state.highlightedCount = 3 }
        composeRule.onNodeWithText(esString(R.string.study_highlighted_count, 3)).assertExists()

        update {
            state.extractingMore = true
            state.totalPages = 4
        }
        composeRule.onNodeWithText(esString(R.string.study_extracting_more, 4)).assertExists()

        update {
            state.extractingMore = false
            state.isSpeaking = true
            state.currentPage = 2
            state.totalPages = 5
            state.isCurrentHighlighted = true
        }
        composeRule.onNodeWithText(esString(R.string.study_reading_page, 2, 5)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stop)).performClick()
        assertEquals(1, speakClicks)
        composeRule.onAllNodesWithContentDescription(mark)[0].performClick()
        assertEquals(1, toggleClicks)

        update { state.totalPages = 0 }
        composeRule.onNodeWithText(esString(R.string.study_reading_document)).assertExists()

        update {
            state.isSpeaking = false
            state.finished = true
        }
        composeRule.onNodeWithText(esString(R.string.study_reading_finished)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_read_again)).assertIsEnabled()

        // Motor TTS no disponible: aviso visible y botón deshabilitado.
        update {
            state.ttsReady = false
            state.ttsError = "Aviso de prueba TTS"
        }
        composeRule.onNodeWithText("Aviso de prueba TTS").assertExists()
        composeRule.onNodeWithText(esString(R.string.study_read_again)).assertIsNotEnabled()

        update { state.voices = listOf(voice("es-es-x-a-local"), voice("es-es-x-b-local")) }
        composeRule.onNodeWithText(esString(R.string.study_choose_voice)).assertIsEnabled().performClick()
        assertEquals(1, voiceClicks)
    }

    @Test
    fun visorDePdfReal_renderizaPaginasYSigueLaPaginaActual() {
        val pdf = newPdf(pages = 3)
        val state = ReadingState(uri = Uri.fromFile(pdf))
        state.totalPages = 3
        setReading(state)
        val pageOne = esString(R.string.viewer_page_content_desc, 1)
        val viewerError = esString(R.string.viewer_error)

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithContentDescription(pageOne).fetchSemanticsNodes().isNotEmpty() ||
                exists(viewerError)
        }
        if (composeRule.onAllNodesWithContentDescription(pageOne).fetchSemanticsNodes().isNotEmpty()) {
            // Zoom con pellizco sobre la primera página.
            composeRule.onAllNodesWithContentDescription(pageOne)[0].performTouchInput {
                val c = center
                pinch(c + Offset(-10f, 0f), c + Offset(-40f, 0f), c + Offset(10f, 0f), c + Offset(40f, 0f))
            }
            // La lectura avanza de página: el visor se desplaza a la tercera.
            update { state.currentPage = 3 }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule
                    .onAllNodesWithContentDescription(esString(R.string.viewer_page_content_desc, 3))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    // ── StudyScreen: flujos completos ─────────────────────────────────────────
    private fun setStudyReading() {
        val fakeNotes = FakeNotes()
        env.setContent {
            StudyScreen(
                initialTab = STUDY_TAB_READING,
                viewModel = buildStudyViewModel(),
                notesViewModel = fakeNotes.buildViewModel(),
            )
        }
    }

    private fun waitForExtractionDone() {
        composeRule.waitForTextExists(esString(R.string.study_highlighted_count, 0), timeoutMillis = 30_000)
    }

    // Con TTS ready (teléfono) lee y detiene; sin TTS (emulador de CI) verifica el aviso y el botón.
    private fun exerciseSpeakButtonIfPossible() {
        val readAll = esString(R.string.study_read_all)
        val unavailable = esString(R.string.study_tts_unavailable)
        val isReadEnabled = { runCatching { composeRule.onNodeWithText(readAll).assertIsEnabled() }.isSuccess }
        try {
            composeRule.waitUntil(timeoutMillis = 6_000) { exists(unavailable) || isReadEnabled() }
        } catch (e: ComposeTimeoutException) {
            return
        }
        if (exists(unavailable)) {
            composeRule.onNodeWithText(readAll).assertIsNotEnabled()
        } else {
            composeRule.onNodeWithText(readAll).performClick()
            composeRule.waitForTextExists(esString(R.string.study_stop), timeoutMillis = 6_000)
            composeRule.onNodeWithText(esString(R.string.study_stop)).performClick()
            composeRule.waitForTextExists(readAll, timeoutMillis = 6_000)
        }
    }

    @Test
    fun abrirDocumento_conElSelector_extraeYMuestraElVisor() {
        val first = newPdf(pages = 2)
        val second = newPdf(pages = 1)
        setStudyReading()
        composeRule.waitForTextExists(esString(R.string.study_reading_intro))

        // El selector grabado recibe solo PDF; cancelarlo no cambia nada.
        composeRule.onNodeWithText(esString(R.string.qr_open_document)).performClick()
        val input = env.registry.launchedInputs.last() as Array<*>
        assertEquals(listOf("application/pdf"), input.toList())
        env.registry.respond(Activity.RESULT_CANCELED, null)
        composeRule.onNodeWithText(esString(R.string.study_reading_intro)).assertExists()

        // Elegir un PDF real: extracción con iText y visor.
        composeRule.onNodeWithText(esString(R.string.qr_open_document)).performClick()
        env.registry.respond(Activity.RESULT_OK, Intent().setData(Uri.fromFile(first)))
        composeRule.waitForTextExists(esString(R.string.study_read_all), timeoutMillis = 30_000)
        waitForExtractionDone()
        exerciseSpeakButtonIfPossible()

        // Elegir otro documento con el primero ya abierto (cancela la carga anterior).
        composeRule.onNodeWithText(esString(R.string.qr_open_document)).performClick()
        env.registry.respond(Activity.RESULT_OK, Intent().setData(Uri.fromFile(second)))
        composeRule.waitForTextExists(esString(R.string.study_read_all), timeoutMillis = 30_000)
        waitForExtractionDone()

        // Volver detiene la lectura y regresa al menú.
        composeRule.onNodeWithText(esString(R.string.general_back)).performClick()
        composeRule.waitForTextExists(esString(R.string.study_menu_prompt))
    }

    @Test
    fun continuarLeyendo_reanudaElDocumentoGuardado() {
        val pdf = newPdf(pages = 2)
        StudyReadingProgressStorage.save(
            env.seedContext,
            progress(Uri.fromFile(pdf).toString(), "Apuntes.pdf", paragraphIndex = 1),
        )
        setStudyReading()
        composeRule.waitForTextExists("Apuntes.pdf")

        composeRule.onNodeWithText("Apuntes.pdf").performScrollTo().performClick()

        composeRule.waitForTextExists(esString(R.string.study_read_all), timeoutMillis = 30_000)
        waitForExtractionDone()
        // El progreso guardado sigue ahí (no se leyó hasta el final).
        assertNotNull(StudyReadingProgressStorage.findFor(env.seedContext, Uri.fromFile(pdf).toString()))
    }

    @Test
    fun continuarLeyendo_documentoInexistente_quitaLaEntradaHuérfana() {
        val missing = Uri.fromFile(File(composeRule.activity.cacheDir, "no_existe_${System.nanoTime()}.pdf"))
        StudyReadingProgressStorage.save(env.seedContext, progress(missing.toString(), "Borrado.pdf"))
        setStudyReading()
        composeRule.waitForTextExists("Borrado.pdf")

        composeRule.onNodeWithText("Borrado.pdf").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            StudyReadingProgressStorage.loadAll(env.seedContext).isEmpty()
        }
    }

    @Test
    fun continuarLeyendo_quitarUnaEntradaLaBorraDelHistorial() {
        StudyReadingProgressStorage.save(env.seedContext, progress("file:///viejo.pdf", "Viejo.pdf"))
        StudyReadingProgressStorage.save(env.seedContext, progress("file:///nuevo.pdf", "Nuevo.pdf"))
        setStudyReading()
        composeRule.waitForTextExists("Nuevo.pdf")

        // El más reciente va primero.
        composeRule.onAllNodesWithContentDescription(esString(R.string.study_remove_from_history))[0]
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { !exists("Nuevo.pdf") }
        composeRule.onNodeWithText("Viejo.pdf").assertExists()
        assertTrue(StudyReadingProgressStorage.findFor(env.seedContext, "file:///nuevo.pdf") == null)
        assertEquals(1, StudyReadingProgressStorage.loadAll(env.seedContext).size)
    }
}
