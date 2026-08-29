package com.docsmart.features.study.domain

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * RF-STU-09: estadísticas de estudio (tiempo total leído, pomodoros
 * completados por semana). Mismo patrón de SharedPreferences fake respaldado
 * por un mapa real ya usado en StudyNotesStorageTest.
 */
class StudyStatsStorageTest {

    @Test
    fun `addReadingTime acumula milisegundos entre llamadas`() {
        val context = contextWith(fakePrefsStore())

        StudyStatsStorage.addReadingTime(context, 5_000)
        StudyStatsStorage.addReadingTime(context, 3_000)

        assertEquals(8_000L, StudyStatsStorage.loadStats(context).totalReadingMillis)
    }

    @Test
    fun `addReadingTime ignora valores no positivos`() {
        val context = contextWith(fakePrefsStore())

        StudyStatsStorage.addReadingTime(context, 0)
        StudyStatsStorage.addReadingTime(context, -500)

        assertEquals(0L, StudyStatsStorage.loadStats(context).totalReadingMillis)
    }

    @Test
    fun `recordPomodoroCompletion agrega un timestamp al historial`() {
        val context = contextWith(fakePrefsStore())

        StudyStatsStorage.recordPomodoroCompletion(context, at = 1000L)
        StudyStatsStorage.recordPomodoroCompletion(context, at = 2000L)

        assertEquals(listOf(1000L, 2000L), StudyStatsStorage.loadStats(context).pomodoroTimestamps)
    }

    @Test
    fun `trimOldTimestamps descarta timestamps mas viejos que el periodo de retencion`() {
        val now = 1_000_000_000L
        val retentionMillis = StudyStatsStorage.POMODORO_HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val recent = now - 1000
        val old = now - retentionMillis - 1000

        val result = StudyStatsStorage.trimOldTimestamps(listOf(recent, old), now)

        assertEquals(listOf(recent), result)
    }

    @Test
    fun `pomodoroCountsByWeekday descarta timestamps fuera de la semana calendario actual`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 26, 12, 0, 0) // miércoles
        val now = calendar.timeInMillis
        val oneDay = 24 * 60 * 60 * 1000L

        // 30 días es más que suficiente para caer en una semana calendario
        // distinta sin importar cuál sea el primer día de semana del locale.
        val farBefore = now - 30 * oneDay
        val farAfter = now + 30 * oneDay

        val counts = pomodoroCountsByWeekday(listOf(now, now, farBefore, farAfter), now)

        assertEquals(2, counts.sum(), "solo los 2 timestamps de la semana actual deben contarse")
    }

    @Test
    fun `pomodoroCountsByWeekday agrupa por dia dentro de la misma semana`() {
        val now = System.currentTimeMillis()

        val counts = pomodoroCountsByWeekday(listOf(now, now, now), now)
        val dayWithAllCounts = counts.indexOfFirst { it == 3 }

        assertTrue(dayWithAllCounts >= 0, "los 3 timestamps idénticos deben caer en el mismo día")
        assertEquals(3, counts.sum())
    }

    @Test
    fun `pomodoroCountThisWeek suma todos los dias de la semana`() {
        val now = System.currentTimeMillis()
        val counts = pomodoroCountsByWeekday(listOf(now, now, now), now)

        assertEquals(counts.sum(), pomodoroCountThisWeek(listOf(now, now, now), now))
        assertTrue(pomodoroCountThisWeek(listOf(now, now, now), now) >= 3)
    }

    @Test
    fun `millisToHoursAndMinutes convierte sin decimales`() {
        assertEquals(1 to 5, millisToHoursAndMinutes(65 * 60_000L))
        assertEquals(0 to 0, millisToHoursAndMinutes(0))
        assertEquals(0 to 59, millisToHoursAndMinutes(59 * 60_000L))
    }

    // ── helpers: SharedPreferences respaldado por un mapa real ─────────────

    private fun fakePrefsStore(): MutableMap<String, Any> = mutableMapOf()

    private fun contextWith(store: MutableMap<String, Any>): Context {
        val editor = mockk<SharedPreferences.Editor>()
        val longKeySlot = slot<String>(); val longValueSlot = slot<Long>()
        every { editor.putLong(capture(longKeySlot), capture(longValueSlot)) } answers {
            store[longKeySlot.captured] = longValueSlot.captured
            editor
        }
        val strKeySlot = slot<String>(); val strValueSlot = slot<String>()
        every { editor.putString(capture(strKeySlot), capture(strValueSlot)) } answers {
            store[strKeySlot.captured] = strValueSlot.captured
            editor
        }
        every { editor.apply() } answers { }

        val prefs = mockk<SharedPreferences>()
        val getLongKey = slot<String>(); val getLongDefault = slot<Long>()
        every { prefs.getLong(capture(getLongKey), capture(getLongDefault)) } answers {
            (store[getLongKey.captured] as? Long) ?: getLongDefault.captured
        }
        val getStrKey = slot<String>(); val getStrDefault = slot<String>()
        every { prefs.getString(capture(getStrKey), capture(getStrDefault)) } answers {
            (store[getStrKey.captured] as? String) ?: getStrDefault.captured
        }
        every { prefs.edit() } returns editor

        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }
}
