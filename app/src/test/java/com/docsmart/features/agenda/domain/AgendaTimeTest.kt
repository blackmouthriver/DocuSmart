package com.docsmart.features.agenda.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class AgendaTimeTest {
    private val zone = ZoneOffset.UTC

    private fun millisOf(
        date: LocalDate,
        time: LocalTime = LocalTime.NOON,
    ): Long =
        LocalDateTime
            .of(date, time)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `un evento de un dia anterior es vencido sin importar la hora`() {
        val today = LocalDate.of(2026, 9, 17)
        val yesterdayLate = millisOf(today.minusDays(1), LocalTime.of(23, 59))

        val status = classifyAgendaEvent(yesterdayLate, nowMillis = millisOf(today), zoneId = zone)

        assertEquals(AgendaEventStatus.OVERDUE, status)
    }

    @Test
    fun `un evento de hoy es hoy aunque la hora ya haya pasado`() {
        val today = LocalDate.of(2026, 9, 17)
        val earlyToday = millisOf(today, LocalTime.of(6, 0))
        val laterToday = millisOf(today, LocalTime.of(18, 0))

        val status = classifyAgendaEvent(earlyToday, nowMillis = laterToday, zoneId = zone)

        assertEquals(AgendaEventStatus.TODAY, status)
    }

    @Test
    fun `un evento de un dia futuro es proximo`() {
        val today = LocalDate.of(2026, 9, 17)
        val nextWeek = millisOf(today.plusDays(7))

        val status = classifyAgendaEvent(nextWeek, nowMillis = millisOf(today), zoneId = zone)

        assertEquals(AgendaEventStatus.UPCOMING, status)
    }

    @Test
    fun `reminderTriggerMillis resta los minutos de antelacion`() {
        val eventAt = millisOf(LocalDate.of(2026, 9, 17), LocalTime.of(15, 0))

        val trigger = reminderTriggerMillis(eventAt, reminderMinutesBefore = 60)

        assertEquals(eventAt - 60 * 60_000L, trigger)
    }

    @Test
    fun `reminderTriggerMillis con 0 minutos coincide exactamente con el evento`() {
        val eventAt = millisOf(LocalDate.of(2026, 9, 17), LocalTime.of(15, 0))

        val trigger = reminderTriggerMillis(eventAt, reminderMinutesBefore = ReminderPreset.AT_TIME)

        assertEquals(eventAt, trigger)
    }

    @Test
    fun `agendaEventLocalDate devuelve el dia calendario en la zona indicada`() {
        val date = LocalDate.of(2026, 9, 17)
        val eventAt = millisOf(date, LocalTime.of(23, 30))

        val localDate = agendaEventLocalDate(eventAt, zoneId = zone)

        assertEquals(date, localDate)
    }

    // Los tests de arriba siempre pasan nowMillis/zoneId explícitos -- nunca
    // ejercen el valor por defecto (System.currentTimeMillis()/ZoneId.systemDefault()).
    @Test
    fun `classifyAgendaEvent sin nowMillis ni zoneId usa el reloj y la zona reales`() {
        val status = classifyAgendaEvent(System.currentTimeMillis())

        assertEquals(AgendaEventStatus.TODAY, status)
    }

    // Mismo motivo: agendaEventLocalDate sin zoneId nunca ejercía el valor
    // por defecto (ZoneId.systemDefault()).
    @Test
    fun `agendaEventLocalDate sin zoneId usa la zona real del sistema`() {
        val localDate = agendaEventLocalDate(System.currentTimeMillis())

        assertEquals(LocalDate.now(), localDate)
    }
}
