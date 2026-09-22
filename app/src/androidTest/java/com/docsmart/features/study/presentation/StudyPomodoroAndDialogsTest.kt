package com.docsmart.features.study.presentation

import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.features.agenda.presentation.components.esString
import com.docsmart.features.study.domain.PomodoroEngine
import com.docsmart.features.study.domain.StudyStats
import com.docsmart.features.study.domain.StudyStatsStorage
import com.docsmart.features.study.domain.personasForVoices
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * Pomodoro (pestaña y estados), diálogo de estadísticas y selector de voces. El selector se prueba
 * directo con [Voice] reales construidas a mano (el emulador de CI no tiene motor TTS).
 */
class StudyPomodoroAndDialogsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val env by lazy { StudyTestEnv(composeRule) }
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    // PomodoroEngine es un singleton de proceso: siempre en reposo antes y después.
    @Before
    fun resetPomodoro() = PomodoroEngine.reset()

    @After
    fun resetPomodoroAfter() = PomodoroEngine.reset()

    private fun voice(
        name: String,
        quality: Int,
    ) = Voice(name, Locale.forLanguageTag("es-ES"), quality, Voice.LATENCY_NORMAL, false, emptySet())

    // ── PomodoroTab directo ───────────────────────────────────────────────────
    @Test
    fun pestanaPomodoro_muestraEstudioYDescansoConSusControles() {
        var minutes by mutableIntStateOf(25)
        var seconds by mutableIntStateOf(0)
        var running by mutableStateOf(false)
        var isBreak by mutableStateOf(false)
        var toggles = 0
        var resets = 0
        var statsClicks = 0
        // Historial aislado: el contador de la tarjeta lee las prefs en memoria.
        StudyStatsStorage.recordPomodoroCompletion(env.seedContext, 1_700_000_000_000L)
        StudyStatsStorage.recordPomodoroCompletion(env.seedContext, 1_700_000_100_000L)
        env.setContent {
            PomodoroTab(
                minutes = minutes,
                seconds = seconds,
                isRunning = running,
                isBreak = isBreak,
                pomodoroCount = 0,
                onToggle = { toggles++ },
                onReset = { resets++ },
                onShowStats = { statsClicks++ },
            )
        }

        // Estudio en reposo
        composeRule.onNodeWithText(esString(R.string.study_pomodoro_technique_info)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_study_label)).assertExists()
        composeRule.onNodeWithText("25:00").assertExists()
        composeRule.onNodeWithText(esString(R.string.study_paused)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_pomodoros_completed)).performScrollTo().assertExists()
        // Total persistido (no el contador de la sesión).
        composeRule.onNodeWithText("2").assertExists()

        composeRule.onNodeWithText(esString(R.string.study_start)).performScrollTo().performClick()
        assertEquals(1, toggles)
        composeRule.onNodeWithText(esString(R.string.study_restart)).performScrollTo().performClick()
        assertEquals(1, resets)
        composeRule.onNodeWithText(esString(R.string.study_stats_icon_desc)).performScrollTo().performClick()
        assertEquals(1, statsClicks)

        // Descanso corriendo
        composeRule.runOnIdle {
            isBreak = true
            running = true
            minutes = 4
            seconds = 59
        }
        composeRule.onNodeWithText(esString(R.string.study_break_label)).assertExists()
        composeRule.onNodeWithText("04:59").assertExists()
        composeRule.onNodeWithText(esString(R.string.study_in_progress)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_pause)).performScrollTo().performClick()
        assertEquals(2, toggles)
    }

    // ── Pomodoro en la pantalla completa ──────────────────────────────────────
    @Test
    fun pomodoroEnPantalla_iniciarPausarReiniciarYVerEstadisticas() {
        StudyStatsStorage.addReadingTime(env.seedContext, 3_900_000L)
        StudyStatsStorage.recordPomodoroCompletion(env.seedContext, System.currentTimeMillis())
        val fakeNotes = FakeNotes()
        env.setContent {
            StudyScreen(
                initialTab = STUDY_TAB_POMODORO,
                viewModel = buildStudyViewModel(),
                notesViewModel = fakeNotes.buildViewModel(),
            )
        }
        composeRule.waitForTextExists(esString(R.string.study_start))

        composeRule.onNodeWithText(esString(R.string.study_start)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_pause))
        composeRule.onNodeWithText(esString(R.string.study_in_progress)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_pause)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_start))
        composeRule.onNodeWithText(esString(R.string.study_restart)).performScrollTo().performClick()

        // Estadísticas con datos sembrados: 1h 5min leídos y 1 pomodoro.
        composeRule.onNodeWithText(esString(R.string.study_stats_icon_desc)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_stats_title))
        composeRule.onNodeWithText(esString(R.string.study_stats_reading_value, 1, 5)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_total_reading)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_pomodoros_total)).assertExists()
        composeRule.onNodeWithText(esString(R.string.general_close)).performClick()
        composeRule.onNodeWithText(esString(R.string.study_stats_title)).assertDoesNotExist()
    }

    // ── StudyStatsDialog directo ──────────────────────────────────────────────
    @Test
    fun estadisticas_sinDatosMuestraElMensajeVacio() {
        var dismissed = 0
        env.setContent {
            StudyStatsDialog(
                stats = StudyStats(totalReadingMillis = 0L, pomodoroTimestamps = emptyList()),
                onDismiss = { dismissed++ },
            )
        }

        composeRule.onNodeWithText(esString(R.string.study_stats_title)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_empty)).assertExists()
        composeRule.onNodeWithText(esString(R.string.general_close)).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun estadisticas_conDatosMuestraTotalesYBarrasSemanales() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        env.setContent {
            StudyStatsDialog(
                stats =
                    StudyStats(
                        totalReadingMillis = 3_900_000L,
                        pomodoroTimestamps = listOf(now, now - day, now - 2 * day),
                    ),
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText(esString(R.string.study_stats_total_reading)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_reading_value, 1, 5)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_pomodoros_total)).assertExists()
        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNode(hasText(esString(R.string.study_stats_pomodoros_this_week), substring = true)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_stats_empty)).assertDoesNotExist()
    }

    // ── VoiceSelectorDialog directo ───────────────────────────────────────────
    @Test
    fun selectorDeVoces_listaPersonajesCalidadesYAcciones() {
        val voices =
            listOf(
                voice("es-es-x-a-local", Voice.QUALITY_VERY_HIGH),
                voice("es-es-x-b-local", Voice.QUALITY_HIGH),
                voice("es-es-x-c-local", Voice.QUALITY_NORMAL),
                voice("es-es-x-d-local", Voice.QUALITY_LOW),
            )
        var selected: Voice? = null
        var previewed: Voice? = null
        var dismissed = 0
        var previewing by mutableStateOf<String?>(null)
        // Pedido explícito del usuario 2026-09-22: un personaje distinto por voz --
        // `personasForVoices` (no `personaForVoice` sola) es lo que usa el diálogo real.
        val personas = personasForVoices(voices.map { it.name })
        val firstVoiceName = personas.getValue("es-es-x-a-local").name
        env.setContent {
            VoiceSelectorDialog(
                voices = voices,
                personas = personas,
                selectedVoice = voices[0],
                previewingVoiceName = previewing,
                onVoiceSelected = { selected = it },
                onPreviewVoice = {
                    previewed = it
                    previewing = it.name
                },
                onDismiss = { dismissed++ },
            )
        }

        composeRule.onNodeWithText(esString(R.string.study_choose_voice)).assertExists()
        // Un personaje por voz (los 4 nombres técnicos caen en personajes distintos), con su calidad.
        composeRule.onNodeWithText(firstVoiceName).assertExists()
        composeRule.onAllNodesWithText("calidad muy alta", substring = true).assertCountEquals(1)
        val previewDesc = esString(R.string.study_voice_preview)
        composeRule.onAllNodesWithContentDescription(previewDesc).assertCountEquals(voices.size)

        // Elegir una voz por su nombre.
        composeRule.onNodeWithText(firstVoiceName).performClick()
        assertEquals("es-es-x-a-local", selected?.name)

        // Escuchar una muestra: la fila pasa a "reproduciendo" y su botón se deshabilita.
        composeRule.onAllNodesWithContentDescription(previewDesc)[0].performClick()
        assertEquals("es-es-x-a-local", previewed?.name)
        val playingDesc = esString(R.string.study_voice_preview_playing)
        composeRule.onAllNodesWithContentDescription(playingDesc)[0].assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription(previewDesc)[0].assertIsEnabled()

        composeRule.onNodeWithText(esString(R.string.general_close)).performClick()
        assertEquals(1, dismissed)
    }
}
