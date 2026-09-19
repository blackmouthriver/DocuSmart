package com.docsmart.features.agenda.presentation

import app.cash.turbine.test
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.agenda.data.AgendaRepository
import com.docsmart.features.agenda.domain.ReminderPreset
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cubre el hallazgo B18 de la auditoría general 2026-09-17: cambiar de mes
 * (flechas del calendario) no tocaba `selectedDate` -- el detalle de día
 * seguía mostrando eventos de una fecha que ya no es visible en la grilla
 * del mes nuevo. `goToPreviousMonth()`/`goToNextMonth()` ahora conservan el
 * mismo día-del-mes si existe en el mes nuevo, recortado al último día si
 * el mes nuevo tiene menos días (ej. 31 de enero -> 28/29 de febrero).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    private lateinit var adManager: AdManager
    private lateinit var repository: AgendaRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        adManager = mockk(relaxed = true)
        repository = mockk()
        every { repository.observeAll() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = AgendaViewModel(adManager, repository)

    // `month` en `AgendaCalendarSelection` arranca siempre en
    // `YearMonth.now()` -- `selectDate()` solo toca `selectedDate`, no
    // `month`. Los tests calculan todo en relación a `YearMonth.now()` (no
    // fechas absolutas) para no depender del día real en que corren.

    @Test
    fun `goToNextMonth conserva el mismo dia si existe en el mes nuevo`() =
        runTest {
            val viewModel = buildViewModel()
            val thisMonth = YearMonth.now()
            viewModel.selectDate(thisMonth.atDay(1))

            viewModel.uiState.test {
                awaitItem() // estado inicial tras selectDate
                viewModel.goToNextMonth()
                val afterNext = awaitItem()
                val nextMonth = thisMonth.plusMonths(1)
                assertEquals(nextMonth, afterNext.calendarMonth)
                assertEquals(nextMonth.atDay(1), afterNext.selectedDate)
            }
        }

    @Test
    fun `goToNextMonth recorta el dia al ultimo del mes nuevo si no existe`() =
        runTest {
            val viewModel = buildViewModel()
            val thisMonth = YearMonth.now()
            val lastDay = thisMonth.atEndOfMonth()
            viewModel.selectDate(lastDay)

            viewModel.uiState.test {
                awaitItem()
                viewModel.goToNextMonth()
                val afterNext = awaitItem()
                val nextMonth = thisMonth.plusMonths(1)
                val expectedDay = minOf(lastDay.dayOfMonth, nextMonth.lengthOfMonth())
                assertEquals(nextMonth, afterNext.calendarMonth)
                assertEquals(nextMonth.atDay(expectedDay), afterNext.selectedDate)
            }
        }

    @Test
    fun `goToPreviousMonth tambien actualiza selectedDate`() =
        runTest {
            val viewModel = buildViewModel()
            val thisMonth = YearMonth.now()
            val lastDay = thisMonth.atEndOfMonth()
            viewModel.selectDate(lastDay)

            viewModel.uiState.test {
                awaitItem()
                viewModel.goToPreviousMonth()
                val afterPrevious = awaitItem()
                val previousMonth = thisMonth.minusMonths(1)
                val expectedDay = minOf(lastDay.dayOfMonth, previousMonth.lengthOfMonth())
                assertEquals(previousMonth, afterPrevious.calendarMonth)
                assertEquals(previousMonth.atDay(expectedDay), afterPrevious.selectedDate)
            }
        }

    // ── Borrador: crear / editar / guardar ────────────────────────────────────

    private fun TestScope.collectState(viewModel: AgendaViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
    }

    private fun sampleEvent(
        id: String = "e1",
        createdAt: Long = 111L,
    ) = AgendaEventEntity(
        id = id,
        title = "Renovar pasaporte",
        description = "Llevar copia",
        dateTimeMillis = 5_000L,
        documentId = "doc-1",
        reminderMinutesBefore = 60,
        createdAt = createdAt,
    )

    @Test
    fun `startCreating abre un borrador nuevo sin id`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.startCreating()

            val draft = viewModel.uiState.value.draft
            assertNotNull(draft)
            assertNull(draft!!.id)
            assertEquals("", draft.title)
        }

    @Test
    fun `startEditing copia todos los campos del evento incluido createdAt`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.startEditing(sampleEvent())

            val draft = viewModel.uiState.value.draft!!
            assertEquals("e1", draft.id)
            assertEquals(111L, draft.createdAt)
            assertEquals("Renovar pasaporte", draft.title)
            assertEquals("Llevar copia", draft.description)
            assertEquals(5_000L, draft.dateTimeMillis)
            assertEquals("doc-1", draft.documentId)
            assertEquals(60, draft.reminderMinutesBefore)
        }

    @Test
    fun `saveDraft con titulo en blanco no crea nada y conserva el borrador`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.updateDraft { it.copy(title = "   ") }

            viewModel.saveDraft()

            coVerify(exactly = 0) { repository.createEvent(any(), any(), any(), any(), any()) }
            assertNotNull(viewModel.uiState.value.draft)
        }

    @Test
    fun `saveDraft de un evento nuevo recorta titulo y descripcion y cierra el editor`() =
        runTest {
            coEvery { repository.createEvent(any(), any(), any(), any(), any()) } returns sampleEvent()
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.updateDraft {
                it.copy(title = "  Cita  ", description = "   ", dateTimeMillis = 9_000L, documentId = "d")
            }

            viewModel.saveDraft()

            coVerify {
                repository.createEvent(
                    title = "Cita",
                    description = null,
                    dateTimeMillis = 9_000L,
                    documentId = "d",
                    reminderMinutesBefore = ReminderPreset.AT_TIME,
                )
            }
            assertNull(viewModel.uiState.value.draft)
        }

    // Hallazgo real (sexta ronda): un doble-toque rapido en "Guardar" creaba dos eventos.
    @Test
    fun `saveDraft repetido crea un solo evento`() =
        runTest {
            coEvery { repository.createEvent(any(), any(), any(), any(), any()) } returns sampleEvent()
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.updateDraft { it.copy(title = "Cita") }

            viewModel.saveDraft()
            viewModel.saveDraft()

            coVerify(exactly = 1) { repository.createEvent(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `saveDraft de un evento existente actualiza conservando id y createdAt`() =
        runTest {
            val saved = slot<AgendaEventEntity>()
            coEvery { repository.updateEvent(capture(saved)) } returns Unit
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startEditing(sampleEvent(createdAt = 111L))
            viewModel.updateDraft { it.copy(title = "Titulo nuevo") }

            viewModel.saveDraft()

            assertEquals("e1", saved.captured.id)
            assertEquals(111L, saved.captured.createdAt)
            assertEquals("Titulo nuevo", saved.captured.title)
            coVerify(exactly = 0) { repository.createEvent(any(), any(), any(), any(), any()) }
        }

    // Bug real corregido: una excepcion de Room/AlarmManager en la corrutina
    // (sin manejador) tumbaba la app, y como el dialogo ya estaba cerrado el
    // usuario perdia lo escrito.
    @Test
    fun `saveDraft que falla no propaga la excepcion y reabre el borrador`() =
        runTest {
            coEvery { repository.createEvent(any(), any(), any(), any(), any()) } throws IllegalStateException("db")
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.updateDraft { it.copy(title = "Cita importante") }

            viewModel.saveDraft()

            assertEquals("Cita importante", viewModel.uiState.value.draft?.title)
        }

    @Test
    fun `saveDraft que falla no pisa un borrador nuevo que el usuario ya abrio`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { repository.createEvent(any(), any(), any(), any(), any()) } coAnswers {
                gate.await()
                throw IllegalStateException("db")
            }
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.updateDraft { it.copy(title = "Primero") }
            viewModel.saveDraft()
            viewModel.startCreating()
            viewModel.updateDraft { it.copy(title = "Segundo") }

            gate.complete(Unit)

            assertEquals("Segundo", viewModel.uiState.value.draft?.title)
        }

    @Test
    fun `updateDraft sin borrador abierto no crea uno`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.updateDraft { it.copy(title = "fantasma") }

            assertNull(viewModel.uiState.value.draft)
        }

    // ── Vinculo de documento ──────────────────────────────────────────────────

    @Test
    fun `linkDocument vincula el id al borrador y cierra el dialogo`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startCreating()
            viewModel.showLinkDocumentDialog()
            assertTrue(viewModel.uiState.value.showLinkDocumentDialog)

            viewModel.linkDocument(
                DocumentUiModel(id = "doc-9", name = "a.pdf", type = DocumentType.PDF, size = "1", date = ""),
            )

            assertEquals("doc-9", viewModel.uiState.value.draft?.documentId)
            assertFalse(viewModel.uiState.value.showLinkDocumentDialog)
        }

    @Test
    fun `unlinkDocument quita el vinculo`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startEditing(sampleEvent())

            viewModel.unlinkDocument()

            assertNull(viewModel.uiState.value.draft?.documentId)
        }

    // ── Borrar / notificacion ─────────────────────────────────────────────────

    @Test
    fun `deleteEvent del evento en edicion cierra el editor`() =
        runTest {
            coJustRun { repository.deleteEvent("e1") }
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startEditing(sampleEvent(id = "e1"))

            viewModel.deleteEvent("e1")

            coVerify { repository.deleteEvent("e1") }
            assertNull(viewModel.uiState.value.draft)
        }

    @Test
    fun `deleteEvent de otro evento deja abierto el borrador actual`() =
        runTest {
            coJustRun { repository.deleteEvent("otro") }
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startEditing(sampleEvent(id = "e1"))

            viewModel.deleteEvent("otro")

            assertEquals("e1", viewModel.uiState.value.draft?.id)
        }

    @Test
    fun `deleteEvent que falla no propaga la excepcion ni cierra el editor`() =
        runTest {
            coEvery { repository.deleteEvent("e1") } throws IllegalStateException("db")
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.startEditing(sampleEvent(id = "e1"))

            viewModel.deleteEvent("e1")

            assertEquals("e1", viewModel.uiState.value.draft?.id)
        }

    @Test
    fun `openEventFromNotification abre el editor del evento existente`() =
        runTest {
            coEvery { repository.getById("e1") } returns sampleEvent(id = "e1")
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.openEventFromNotification("e1")

            assertEquals("e1", viewModel.uiState.value.draft?.id)
        }

    @Test
    fun `openEventFromNotification de un evento ya borrado no abre nada`() =
        runTest {
            coEvery { repository.getById("borrado") } returns null
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.openEventFromNotification("borrado")

            assertNull(viewModel.uiState.value.draft)
        }

    // ── Calendario / modo de vista ────────────────────────────────────────────

    @Test
    fun `setViewMode cambia entre lista y calendario`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)

            viewModel.setViewMode(AgendaViewMode.CALENDAR)

            assertEquals(AgendaViewMode.CALENDAR, viewModel.uiState.value.viewMode)
        }

    @Test
    fun `goToToday restablece el mes y el dia seleccionados`() =
        runTest {
            val viewModel = buildViewModel()
            collectState(viewModel)
            viewModel.goToNextMonth()
            viewModel.goToNextMonth()

            viewModel.goToToday()

            assertEquals(YearMonth.now(), viewModel.uiState.value.calendarMonth)
            assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `los eventos del repositorio llegan al estado`() =
        runTest {
            every { repository.observeAll() } returns flowOf(listOf(sampleEvent()))
            val viewModel = buildViewModel()
            collectState(viewModel)

            assertEquals(listOf(sampleEvent()), viewModel.uiState.value.events)
        }

    @Test
    fun `rescheduleAllReminders delega en el repositorio`() =
        runTest {
            coJustRun { repository.rescheduleAllReminders() }
            val viewModel = buildViewModel()

            viewModel.rescheduleAllReminders()

            coVerify { repository.rescheduleAllReminders() }
        }
}
