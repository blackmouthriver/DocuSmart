package com.docsmart.features.agenda.presentation

import app.cash.turbine.test
import com.docsmart.core.ads.AdManager
import com.docsmart.features.agenda.data.AgendaRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `goToNextMonth conserva el mismo dia si existe en el mes nuevo`() = runTest {
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
    fun `goToNextMonth recorta el dia al ultimo del mes nuevo si no existe`() = runTest {
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
    fun `goToPreviousMonth tambien actualiza selectedDate`() = runTest {
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
}
