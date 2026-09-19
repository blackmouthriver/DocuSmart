package com.docsmart.features.agenda.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.docsmart.core.data.db.AgendaEventEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cubre ReminderScheduler (HU-65). `schedule()`/`cancel()` en sí construyen
 * un `Intent(context, AgendaReminderReceiver::class.java)` real dentro de
 * `reminderPendingIntent()` -- este proyecto no usa Robolectric y un Intent
 * real revienta bajo el stub de Android en un test JVM puro (mismo criterio
 * ya documentado en DocumentSharingTest/resolveShareUri), así que:
 *  - las ramas de `schedule()` que retornan ANTES de construir el Intent
 *    (sin recordatorio, fecha ya vencida) sí se prueban vía la API pública;
 *  - la decisión de alarma exacta/inexacta + el fallback por SecurityException
 *    se prueban contra `scheduleAlarm()` (internal), que recibe el
 *    PendingIntent ya armado;
 *  - la liberación de cancel() se prueba contra `releaseAlarm()` (internal),
 *    por el mismo motivo.
 *
 * Nota sobre el entorno: `Build.VERSION.SDK_INT` vale 0 en los tests JVM
 * puros de este proyecto (confirmado empíricamente), así que
 * `canScheduleExactAlarms()` siempre toma la rama "SDK < S -> true" -- la
 * degradación real a alarma inexacta por falta de permiso (Android 12+) no
 * es alcanzable end-to-end en este entorno; se cubre igual a nivel de
 * `scheduleAlarm()` fingiendo el resultado de `canScheduleExactAlarms()` no
 * es posible tampoco (es una función de la propia clase, no inyectada) --
 * ver limitación documentada en el resumen final de la ronda.
 */
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: ReminderScheduler

    @BeforeEach
    fun setUp() {
        alarmManager = mockk(relaxed = true)
        context = mockk()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        scheduler = ReminderScheduler(context)
    }

    private fun event(
        id: String = "event-1",
        reminderMinutesBefore: Int? = 10,
        dateTimeMillis: Long = System.currentTimeMillis() + 3_600_000,
    ) = AgendaEventEntity(
        id = id,
        title = "Reunión",
        dateTimeMillis = dateTimeMillis,
        reminderMinutesBefore = reminderMinutesBefore,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun `schedule no programa nada si el evento no tiene recordatorio`() {
        scheduler.schedule(event(reminderMinutesBefore = null))

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.set(any(), any(), any()) }
    }

    @Test
    fun `schedule no programa nada si la fecha de disparo ya paso (reprogramando tras un reinicio tardio)`() {
        // dateTimeMillis a solo 5 min, con recordatorio de 10 min antes -> el
        // trigger calculado ya quedó 5 min en el pasado.
        val past =
            event(
                dateTimeMillis = System.currentTimeMillis() + 5 * 60_000,
                reminderMinutesBefore = 10,
            )

        scheduler.schedule(past)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.set(any(), any(), any()) }
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
        verify(exactly = 0) { alarmManager.set(any(), any(), any()) }
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
