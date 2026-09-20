package com.docsmart.features.agenda.presentation

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.rule.GrantPermissionRule
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.agenda.data.AgendaRepository
import com.docsmart.features.agenda.presentation.components.esString
import com.docsmart.features.agenda.presentation.components.setContentEs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: AgendaScreen con un AgendaViewModel real armado a mano sobre un
 * AgendaRepository mockeado (mismo patrón que el resto de pantallas) --
 * lista vacía/llena, alta y borrado de eventos, pestaña Calendario, apertura
 * de un evento desde la notificación y los botones Volver/Inicio.
 *
 * No se abre el diálogo de vincular documento: usa `hiltViewModel()` por
 * defecto y aquí no hay Hilt. Tampoco se toca el aviso de alarmas exactas
 * ("Activar" lanza una pantalla de Ajustes del sistema) -- su visibilidad
 * depende del dispositivo, así que no se asevera.
 */
class AgendaScreenTest {
    // AgendaScreen pide POST_NOTIFICATIONS en Android 13+ y el diálogo real
    // del sistema taparía la Activity (mismo hallazgo que StudyScreenTest).
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            },
        )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repository: AgendaRepository

    private val dayMillis = 24 * 60 * 60 * 1_000L
    private val hangTag = "CI_HANG_AgendaScreenTest"

    private fun event(
        id: String,
        title: String,
        dateTimeMillis: Long,
        documentId: String? = null,
    ) = AgendaEventEntity(
        id = id,
        title = title,
        dateTimeMillis = dateTimeMillis,
        documentId = documentId,
        createdAt = 0L,
    )

    private fun buildViewModel(events: List<AgendaEventEntity>): AgendaViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        repository = mockk(relaxed = true)
        every { repository.observeAll() } returns MutableStateFlow(events)
        coEvery { repository.getById(any()) } answers { events.firstOrNull { it.id == firstArg<String>() } }
        return AgendaViewModel(adManager = adManager, repository = repository)
    }

    // El ViewModel se construye ANTES de setContent (no dentro de la
    // composición) para que una recomposición no cree uno nuevo.
    private fun setScreen(
        events: List<AgendaEventEntity> = emptyList(),
        openEventId: String? = null,
        onBack: () -> Unit = {},
        onHome: (() -> Unit)? = null,
    ): AgendaViewModel {
        val viewModel = buildViewModel(events)
        composeRule.setContentEs {
            AgendaScreen(onBack = onBack, openEventId = openEventId, onHome = onHome, viewModel = viewModel)
        }
        return viewModel
    }

    private fun waitForText(text: String) {
        composeRule.waitUntilOrDump(hangTag) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun waitForTextGone(text: String) {
        composeRule.waitUntilOrDump(hangTag) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntilOrDump(hangTag) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForViewMode(
        viewModel: AgendaViewModel,
        mode: AgendaViewMode,
    ) {
        composeRule.waitUntilOrDump(hangTag) { viewModel.uiState.value.viewMode == mode }
    }

    @Test
    fun listaVacia_muestraTituloYEstadoVacio() {
        setScreen()

        waitForText(esString(R.string.agenda_empty_state))
        composeRule.onNodeWithText(esString(R.string.agenda_title)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_view_list)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_view_calendar)).assertIsDisplayed()
    }

    @Test
    fun botonesVolverEInicio_invocanSusCallbacks() {
        var backs = 0
        var homes = 0
        setScreen(onBack = { backs++ }, onHome = { homes++ })
        waitForText(esString(R.string.agenda_empty_state))

        composeRule.onNodeWithText(esString(R.string.general_back)).performClick()
        composeRule.onNodeWithText(esString(R.string.nav_home)).performClick()

        assertEquals(1, backs)
        assertEquals(1, homes)
    }

    @Test
    fun conEventos_muestraTarjetasConSuEstado() {
        val now = System.currentTimeMillis()
        setScreen(
            events =
                listOf(
                    event("1", "Evento pasado", now - 3 * dayMillis),
                    event("2", "Evento de hoy", now, documentId = "doc-1"),
                    event("3", "Evento futuro", now + 3 * dayMillis),
                ),
        )

        waitForText("Evento pasado")
        // La lista es un LazyColumn: en pantallas chicas solo se compone lo visible.
        scrollToText("Evento futuro")
        composeRule.onNodeWithText("Evento futuro").assertExists()
        composeRule.onNodeWithText(esString(R.string.agenda_status_upcoming)).assertExists()
        scrollToText("Evento de hoy")
        composeRule.onNodeWithText("Evento de hoy").assertExists()
        scrollToText(esString(R.string.agenda_status_overdue))
        composeRule.onNodeWithText(esString(R.string.agenda_status_overdue)).assertExists()
        composeRule.onNodeWithText(esString(R.string.agenda_empty_state)).assertDoesNotExist()
    }

    @Test
    fun nuevoEvento_botonFlotanteAbreEditorYGuardaConTitulo() {
        setScreen()
        coEvery { repository.createEvent(any(), any(), any(), any(), any()) } returns
            event("nuevo", "Cita", System.currentTimeMillis())
        waitForText(esString(R.string.agenda_empty_state))

        composeRule.onNodeWithContentDescription(esString(R.string.agenda_new_event)).performClick()
        waitForText(esString(R.string.agenda_editor_title_new))
        composeRule.onNodeWithText(esString(R.string.agenda_field_title)).performTextInput("Cita")
        // "Guardar" se habilita cuando el ViewModel ya publicó el título.
        composeRule.waitUntilOrDump(hangTag) {
            composeRule
                .onAllNodes(hasText(esString(R.string.general_save)) and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(esString(R.string.general_save)).performClick()

        coVerify(timeout = 5_000) {
            repository.createEvent(eq("Cita"), isNull(), any(), isNull(), any())
        }
        waitForTextGone(esString(R.string.agenda_editor_title_new))
    }

    @Test
    fun nuevoEvento_cancelarCierraElEditorSinGuardar() {
        setScreen()
        waitForText(esString(R.string.agenda_empty_state))

        composeRule.onNodeWithContentDescription(esString(R.string.agenda_new_event)).performClick()
        waitForText(esString(R.string.agenda_editor_title_new))
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()

        waitForTextGone(esString(R.string.agenda_editor_title_new))
        coVerify(exactly = 0) { repository.createEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun tocarUnEvento_abreEditorYEliminarConfirmadoBorra() {
        setScreen(events = listOf(event("e1", "Reunion", System.currentTimeMillis() + 2 * dayMillis)))
        waitForText("Reunion")

        composeRule.onNodeWithText("Reunion").performClick()
        waitForText(esString(R.string.agenda_editor_title_edit))
        composeRule.onNodeWithText(esString(R.string.general_delete)).performClick()
        val confirmTitle = esString(R.string.agenda_delete_confirm_title)
        waitForText(confirmTitle)
        // El editor también tiene "Eliminar": se elige el de la ventana de
        // confirmación (la que contiene su título).
        val confirmButton =
            hasText(esString(R.string.general_delete)) and
                hasAnyAncestor(hasAnyDescendant(hasText(confirmTitle))) and
                hasClickAction()
        composeRule.onNode(confirmButton).performClick()

        coVerify(timeout = 5_000) { repository.deleteEvent("e1") }
        waitForTextGone(esString(R.string.agenda_editor_title_edit))
    }

    @Test
    fun openEventId_abreElEditorDeEseEvento() {
        setScreen(
            events = listOf(event("e1", "Desde notificacion", System.currentTimeMillis() + dayMillis)),
            openEventId = "e1",
        )

        waitForText(esString(R.string.agenda_editor_title_edit))
        coVerify(timeout = 5_000) { repository.getById("e1") }
    }

    @Test
    fun openEventId_deEventoInexistente_noAbreEditor() {
        setScreen(openEventId = "fantasma")

        waitForText(esString(R.string.agenda_empty_state))
        coVerify(timeout = 5_000) { repository.getById("fantasma") }
        composeRule.onNodeWithText(esString(R.string.agenda_editor_title_edit)).assertDoesNotExist()
    }
}
