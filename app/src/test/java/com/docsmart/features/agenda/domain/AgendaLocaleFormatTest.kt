package com.docsmart.features.agenda.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

/**
 * Ronda 16 (i18n de Agenda): los formatos de fecha/hora estaban fijos en
 * español ("EEEE d 'de' MMMM") y 24h ("HH:mm"), y la grilla del calendario
 * siempre arrancaba en lunes.
 */
class AgendaLocaleFormatTest {
    private val monday = LocalDate.of(2026, 9, 14)

    @Test
    fun `el detalle del dia en ingles no lleva el conector de espanol`() {
        val text = monday.format(agendaDayDetailFormatter(Locale.US))

        assertTrue(text.contains("September"), text)
        assertTrue(text.contains("Monday"), text)
        assertFalse(text.contains(" de "), text)
    }

    @Test
    fun `el detalle del dia en espanol usa el nombre del mes en espanol`() {
        val text = monday.format(agendaDayDetailFormatter(Locale("es", "ES")))

        assertTrue(text.contains("septiembre"), text)
        assertTrue(text.contains("lunes"), text)
    }

    @Test
    fun `el titulo del mes usa la forma independiente del nombre del mes`() {
        val text = YearMonth.of(2026, 9).atDay(1).format(agendaMonthYearFormatter(Locale.US))

        assertEquals("September 2026", text)
    }

    @Test
    fun `la hora respeta la preferencia de 24 horas`() {
        val millis = LocalDate.of(2026, 9, 14).atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

        val text = formatAgendaDateTime(millis, Locale.US, is24Hour = true, zoneId = ZoneOffset.UTC)

        assertTrue(text.contains("15:30"), text)
        assertTrue(text.contains("2026"), text)
    }

    @Test
    fun `la hora respeta la preferencia de 12 horas`() {
        val millis = LocalDate.of(2026, 9, 14).atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

        val text = formatAgendaDateTime(millis, Locale.US, is24Hour = false, zoneId = ZoneOffset.UTC)

        assertTrue(text.contains("3:30"), text)
        assertTrue(text.contains("PM"), text)
        assertFalse(text.contains("15:30"), text)
    }

    @Test
    fun `el primer dia de la semana sale de la region`() {
        assertEquals(DayOfWeek.SUNDAY, agendaWeekStart(Locale.US))
        assertEquals(DayOfWeek.MONDAY, agendaWeekStart(Locale("es", "ES")))
        assertEquals(DayOfWeek.MONDAY, agendaWeekStart(Locale.FRANCE))
    }

    // Septiembre 2026 empieza en martes.
    @Test
    fun `celdas vacias antes del dia 1 con semana que empieza en lunes o domingo`() {
        val september = YearMonth.of(2026, 9)

        assertEquals(1, calendarLeadingBlanks(september, DayOfWeek.MONDAY))
        assertEquals(2, calendarLeadingBlanks(september, DayOfWeek.SUNDAY))
        assertEquals(0, calendarLeadingBlanks(september, DayOfWeek.TUESDAY))
    }

    // Noviembre 2026 empieza en domingo: con semana en lunes son 6 celdas
    // vacías, con semana en domingo ninguna.
    @Test
    fun `un mes que empieza en domingo deja 6 celdas con semana en lunes y ninguna con semana en domingo`() {
        val november = YearMonth.of(2026, 11)

        assertEquals(6, calendarLeadingBlanks(november, DayOfWeek.MONDAY))
        assertEquals(0, calendarLeadingBlanks(november, DayOfWeek.SUNDAY))
    }
}
