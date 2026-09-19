package com.docsmart.features.study.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cubre NoteReminderScheduler (backlog UX #52). Mismas limitaciones de
 * entorno que ReminderSchedulerTest (Agenda): sin Robolectric, un
 * `Intent(context, NoteReminderReceiver::class.java)` real revienta en un
 * test JVM puro, así que se prueban por separado las ramas de `schedule()`
 * que no llegan a construirlo, y la lógica de `scheduleAlarm()`/
 * `releaseAlarm()` (ambas `internal`) con un PendingIntent ya mockeado.
 */
class NoteReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: NoteReminderScheduler

    @BeforeEach
    fun setUp() {
        alarmManager = mockk(relaxed = true)
        context = mockk()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        scheduler = NoteReminderScheduler(context)
    }

    @Test
    fun `schedule no programa nada si la fecha de disparo ya paso`() {
        val past = System.currentTimeMillis() - 60_000

        scheduler.schedule("note-1", "Nota", past)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.set(any(), any(), any()) }
    }

    @Test
    fun `schedule no programa nada si el trigger es exactamente ahora`() {
        // Límite exacto (<=): un recordatorio programado justo "ahora" ya no
        // debería sonar más tarde en el futuro -- ver mismo guard en
        // ReminderScheduler.schedule() (Agenda).
        val now = System.currentTimeMillis()

        scheduler.schedule("note-1", "Nota", now)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `scheduleAlarm con permiso de alarma exacta programa setExactAndAllowWhileIdle con RTC_WAKEUP`() {
        every { alarmManager.canScheduleExactAlarms() } returns true
        val pendingIntent = mockk<PendingIntent>(relaxed = true)
        val triggerAt = System.currentTimeMillis() + 600_000
        val triggerSlot = slot<Long>()

        scheduler.scheduleAlarm(alarmManager, triggerAt, pendingIntent)

        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, capture(triggerSlot), pendingIntent)
        }
        assertEquals(triggerAt, triggerSlot.captured)
    }

    @Test
    fun `scheduleAlarm ante SecurityException al pedir alarma exacta reintenta con set inexacto en vez de crashear`() {
        val pendingIntent = mockk<PendingIntent>(relaxed = true)
        val triggerAt = System.currentTimeMillis() + 600_000
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        } throws SecurityException("permiso revocado justo antes de la llamada")

        scheduler.scheduleAlarm(alarmManager, triggerAt, pendingIntent)

        verify(exactly = 1) { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
    }

    @Test
    fun `releaseAlarm cancela la alarma en AlarmManager Y libera el PendingIntent en si`() {
        val pendingIntent = mockk<PendingIntent>(relaxed = true)

        scheduler.releaseAlarm(alarmManager, pendingIntent)

        verify(exactly = 1) { alarmManager.cancel(pendingIntent) }
        verify(exactly = 1) { pendingIntent.cancel() }
    }
}
