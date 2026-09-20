package com.docsmart.features.study.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.docsmart.R
import com.docsmart.features.agenda.presentation.components.esString
import com.docsmart.features.study.domain.PomodoroEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Menú de Modo Estudio (4 tarjetas), navegación Volver/Inicio y atrás del sistema. Sin diálogos
 * ni selectores reales: el registro de resultados es un grabador (ver StudyTestSupport).
 */
class StudyNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val env by lazy { StudyTestEnv(composeRule) }

    // PomodoroEngine es un singleton de proceso: se deja siempre en reposo.
    @Before
    fun resetPomodoro() = PomodoroEngine.reset()

    @After
    fun resetPomodoroAfter() = PomodoroEngine.reset()

    private fun setStudy(
        initialTab: Int,
        openNoteId: String? = null,
        onBack: () -> Unit = {},
        onHome: () -> Unit = {},
        onOpenAgenda: () -> Unit = {},
    ) {
        val fakeNotes = FakeNotes()
        env.setContent {
            StudyScreen(
                onBack = onBack,
                initialTab = initialTab,
                openNoteId = openNoteId,
                onOpenAgenda = onOpenAgenda,
                onHome = onHome,
                viewModel = buildStudyViewModel(),
                notesViewModel = fakeNotes.buildViewModel(),
            )
        }
    }

    private fun waitForMenu() = composeRule.waitForTextExists(esString(R.string.study_menu_prompt))

    private fun clickBack() = composeRule.onNodeWithText(esString(R.string.general_back)).performClick()

    @Test
    fun menu_muestraLasCuatroTarjetas() {
        setStudy(initialTab = STUDY_TAB_MENU)
        waitForMenu()

        composeRule.onNodeWithText(esString(R.string.study_menu_subtitle)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_tab_reading)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_menu_reading_desc)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_tab_notes)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_menu_notes_desc)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_tab_pomodoro)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_menu_pomodoro_desc)).assertExists()
        composeRule.onNodeWithText(esString(R.string.agenda_title)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_menu_agenda_desc)).assertExists()
    }

    @Test
    fun menu_cadaTarjetaAbreSuVistaYVolverRegresaAlMenu() {
        setStudy(initialTab = STUDY_TAB_MENU)
        waitForMenu()

        // Lectura (estado vacío sin documento)
        composeRule.onNodeWithText(esString(R.string.study_tab_reading)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_reading_intro))
        clickBack()
        waitForMenu()

        // Notas
        composeRule.onNodeWithText(esString(R.string.study_tab_notes)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_new_note))
        clickBack()
        waitForMenu()

        // Pomodoro
        composeRule.onNodeWithText(esString(R.string.study_tab_pomodoro)).performScrollTo().performClick()
        composeRule.waitForTextExists(esString(R.string.study_start))
        clickBack()
        waitForMenu()
    }

    @Test
    fun menu_tarjetaAgendaInvocaElCallback() {
        var agendaOpened = 0
        setStudy(initialTab = STUDY_TAB_MENU, onOpenAgenda = { agendaOpened++ })
        waitForMenu()

        composeRule.onNodeWithText(esString(R.string.agenda_title)).performScrollTo().performClick()

        assertEquals(1, agendaOpened)
        // No cambia de vista: el menú sigue visible.
        composeRule.onNodeWithText(esString(R.string.study_menu_prompt)).assertExists()
    }

    @Test
    fun volverEnElMenu_saleDeModoEstudio() {
        var backs = 0
        setStudy(initialTab = STUDY_TAB_MENU, onBack = { backs++ })
        waitForMenu()

        clickBack()

        assertEquals(1, backs)
    }

    @Test
    fun inicio_desdeUnaVistaInternaLlamaOnHome() {
        var homes = 0
        setStudy(initialTab = STUDY_TAB_READING, onHome = { homes++ })
        composeRule.waitForTextExists(esString(R.string.study_reading_intro))

        composeRule.onNodeWithText(esString(R.string.nav_home)).performClick()

        assertEquals(1, homes)
    }

    @Test
    fun atrasDelSistema_desdeUnaVistaInternaVuelveAlMenu() {
        var backs = 0
        setStudy(initialTab = STUDY_TAB_NOTES, onBack = { backs++ })
        composeRule.waitForTextExists(esString(R.string.study_new_note))

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        waitForMenu()
        // Atrás de una vista interna NO sale de Modo Estudio.
        assertEquals(0, backs)
    }

    @Test
    fun pestanaInicialFueraDeRango_seAjustaAPomodoro() {
        setStudy(initialTab = 7)
        composeRule.waitForTextExists(esString(R.string.study_start))
        composeRule.onNodeWithText(esString(R.string.study_pomodoro_technique_info)).assertExists()
    }

    @Test
    fun notaConcreta_fuerzaLaVistaDeNotas() {
        setStudy(initialTab = STUDY_TAB_READING, openNoteId = "n1")
        composeRule.waitForTextExists(esString(R.string.study_new_note))
    }
}
