package com.docsmart.features.agenda.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Ronda 18: AgendaCalendarView -- grilla mensual, detalle del día
 * seleccionado (con y sin eventos), marcas de "hoy"/"con eventos" y los
 * callbacks de navegación de mes, selección de día y clic en un evento.
 */
class AgendaCalendarViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fixedMonth = YearMonth.of(2026, 9)

    private fun millisOf(dateTime: LocalDateTime): Long = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(
        id: String,
        title: String,
        dateTime: LocalDateTime,
    ) = AgendaEventEntity(
        id = id,
        title = title,
        dateTimeMillis = millisOf(dateTime),
        createdAt = 0L,
    )

    private val reunion = event("1", "Reunion de equipo", LocalDateTime.of(2026, 9, 15, 10, 0))
    private val otroDia = event("2", "Evento del veinte", LocalDateTime.of(2026, 9, 20, 18, 30))

    private fun setCalendar(
        month: YearMonth = fixedMonth,
        selectedDate: LocalDate = LocalDate.of(2026, 9, 15),
        events: List<AgendaEventEntity> = listOf(reunion, otroDia),
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
        onSelectDate: (LocalDate) -> Unit = {},
        onEventClick: (AgendaEventEntity) -> Unit = {},
    ) {
        composeRule.setContentEs {
            // Con scroll: la grilla de hasta 6 filas + el detalle puede
            // exceder el alto visible en pantallas chicas.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AgendaCalendarView(
                    month = month,
                    selectedDate = selectedDate,
                    events = events,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate,
                    onEventClick = onEventClick,
                )
            }
        }
    }

    @Test
    fun diaSeleccionadoConEventos_muestraSoloSusEventos() {
        setCalendar()

        composeRule.onNodeWithText("Reunion de equipo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Evento del veinte").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(esString(R.string.agenda_calendar_previous_month)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(esString(R.string.agenda_calendar_next_month)).assertIsDisplayed()
    }

    @Test
    fun diaSinEventos_muestraElMensajeVacio() {
        setCalendar(selectedDate = LocalDate.of(2026, 9, 16))

        composeRule
            .onNodeWithText(esString(R.string.agenda_calendar_no_events_day))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Reunion de equipo").assertDoesNotExist()
    }

    @Test
    fun tocarUnDia_invocaOnSelectDate() {
        val selected = mutableListOf<LocalDate>()
        setCalendar(onSelectDate = { selected.add(it) })

        composeRule.onNodeWithText("20").performScrollTo().performClick()

        assertEquals(listOf(LocalDate.of(2026, 9, 20)), selected)
    }

    @Test
    fun flechasDeMes_invocanSusCallbacks() {
        var previous = 0
        var next = 0
        setCalendar(onPreviousMonth = { previous++ }, onNextMonth = { next++ })

        composeRule.onNodeWithContentDescription(esString(R.string.agenda_calendar_previous_month)).performClick()
        composeRule.onNodeWithContentDescription(esString(R.string.agenda_calendar_next_month)).performClick()
        composeRule.onNodeWithContentDescription(esString(R.string.agenda_calendar_next_month)).performClick()

        assertEquals(1, previous)
        assertEquals(2, next)
    }

    @Test
    fun tocarUnEventoDelDetalle_invocaOnEventClick() {
        val clicked = mutableListOf<AgendaEventEntity>()
        setCalendar(onEventClick = { clicked.add(it) })

        composeRule.onNodeWithText("Reunion de equipo").performScrollTo().performClick()

        assertEquals(listOf(reunion), clicked)
    }

    @Test
    fun hoyYDiaConEventos_seAnuncianEnLaDescripcionDeLaCelda() {
        val today = LocalDate.now()
        // Selecciona otro día del mismo mes para que "hoy" no quede también
        // marcado como seleccionado (rama isToday sin isSelected).
        val other = if (today.dayOfMonth == 1) today.plusDays(1) else today.withDayOfMonth(1)
        val todayEvent = event("t", "Evento de hoy", today.atTime(12, 0))
        setCalendar(month = YearMonth.from(today), selectedDate = other, events = listOf(todayEvent))

        val suffixToday = esString(R.string.agenda_calendar_day_today)
        val suffixEvents = esString(R.string.agenda_calendar_day_has_events)
        composeRule.onNode(hasContentDescription(suffixToday, substring = true)).assertExists()
        composeRule.onNode(hasContentDescription(suffixEvents, substring = true)).assertExists()
    }
}
