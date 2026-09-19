package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

/**
 * Ronda 16: el gráfico semanal de pomodoros y el contador diario calculaban
 * los límites con `+ 24h`, así que en un cambio de hora (día de 23 o 25 horas)
 * un pomodoro del viernes por la noche caía en la barra del sábado y la última
 * hora del sábado quedaba fuera de la semana.
 *
 * En Nueva York el horario de verano termina el domingo 2026-11-01 (ese día
 * dura 25 horas). Con `Locale.US` la semana arranca en domingo.
 */
class PomodoroWeekStatsDstTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tz: TimeZone = TimeZone.getTimeZone(newYork)

    private fun millis(
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, newYork).toInstant().toEpochMilli()

    @Test
    fun `un pomodoro del viernes 23-30 cuenta como viernes tras el cambio de hora`() {
        val now = millis(4, 12, 0)

        val counts = pomodoroCountsByWeekday(listOf(millis(6, 23, 30)), now, tz, Locale.US)

        // Domingo=0 ... Viernes=5, Sábado=6
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 1, 0), counts)
    }

    @Test
    fun `la ultima hora del sabado sigue dentro de la semana`() {
        val now = millis(4, 12, 0)

        val counts = pomodoroCountsByWeekday(listOf(millis(7, 23, 30)), now, tz, Locale.US)

        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 0, 1), counts)
    }

    @Test
    fun `el domingo del cambio de hora agrupa sus 25 horas en una sola barra`() {
        val now = millis(4, 12, 0)
        // 00:30 EDT y 23:30 EST del mismo domingo 1 de noviembre.
        val stamps = listOf(millis(1, 0, 30), millis(1, 23, 30))

        val counts = pomodoroCountsByWeekday(stamps, now, tz, Locale.US)

        assertArrayEquals(intArrayOf(2, 0, 0, 0, 0, 0, 0), counts)
    }

    @Test
    fun `un pomodoro del domingo siguiente queda fuera de la semana`() {
        val now = millis(4, 12, 0)

        val counts = pomodoroCountsByWeekday(listOf(millis(8, 0, 30)), now, tz, Locale.US)

        assertEquals(0, counts.sum())
    }

    @Test
    fun `pomodoroCountToday incluye la ultima hora de un dia de 25 horas`() {
        val now = millis(1, 12, 0)
        val stamps = listOf(millis(1, 23, 30), millis(2, 0, 30))

        assertEquals(1, pomodoroCountToday(stamps, now, tz))
    }
}
