package com.docsmart.features.agenda.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.docsmart.core.data.db.AgendaEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// HU-65 (backlog UX 2026-09-16, feedback real de testers de la prueba
// cerrada): decisión de producto explícita del usuario -- alarma EXACTA
// (AlarmManager.setExactAndAllowWhileIdle), no WorkManager, para que el
// recordatorio suene justo a la hora del evento. En Android 12+ esto
// depende del permiso especial SCHEDULE_EXACT_ALARM, que el usuario puede
// negar/revocar desde Ajustes del sistema -- si no está concedido, se
// degrada a una alarma inexacta (AlarmManager.set()) en vez de no
// recordar nada en absoluto (RF5 pide que el recordatorio funcione igual).
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    // No programa nada si el evento no tiene recordatorio, o si la fecha de
    // disparo ya pasó (ej. se está reprogramando tras un reinicio y el
    // recordatorio venció mientras el dispositivo estaba apagado).
    fun schedule(event: AgendaEventEntity) {
        val offsetMinutes = event.reminderMinutesBefore ?: return
        val manager = alarmManager
        val triggerAt = reminderTriggerMillis(event.dateTimeMillis, offsetMinutes)
        if (manager == null || triggerAt <= System.currentTimeMillis()) return
        val pendingIntent = reminderPendingIntent(event.id, event.title)
        scheduleAlarm(manager, triggerAt, pendingIntent, event.id)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleAlarm(
        manager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent, eventId: String
    ) {
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                Timber.w("Sin permiso de alarma exacta -- recordatorio $eventId degradado a inexacto")
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Carrera posible: el usuario revoca el permiso entre el chequeo
            // de canScheduleExactAlarms() y esta llamada -- no debe crashear
            // la app, solo perder precisión.
            Timber.e(e, "SecurityException programando recordatorio $eventId, reintentando inexacto")
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(eventId: String) {
        val manager = alarmManager ?: return
        manager.cancel(reminderPendingIntent(eventId, title = ""))
    }

    // Las extras (título) no forman parte de la igualdad de un PendingIntent
    // (solo componente/acción/datos/categorías) -- por eso cancel() puede
    // pasar un título vacío y sigue matcheando el mismo PendingIntent que se
    // programó con el título real.
    //
    // Hallazgo real de la auditoría de Agenda 2026-09-18 (Media): el
    // requestCode era `eventId.hashCode()` (32 bits) sin `data` propio -- dos
    // eventos distintos cuyo hashCode colisionara compartían el mismo
    // PendingIntent, y FLAG_UPDATE_CURRENT reemplazaba en silencio la alarma
    // del primero por la del segundo. Mismo fix ya aplicado en
    // AgendaReminderReceiver.showNotification() para su propio PendingIntent:
    // `setData()` con una Uri única por eventId vuelve al Intent siempre
    // distinto (data SÍ es parte de la igualdad de un PendingIntent), sin
    // depender de que el requestCode nunca choque.
    private fun reminderPendingIntent(eventId: String, title: String): PendingIntent {
        val intent = Intent(context, AgendaReminderReceiver::class.java).apply {
            data = Uri.parse("docusmart://agenda-reminder/$eventId")
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_EVENT_ID = "agenda_event_id"
        const val EXTRA_EVENT_TITLE = "agenda_event_title"
    }
}
