package com.docsmart.features.study.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Backlog UX #52: mismo mecanismo que ReminderScheduler de Agenda (HU-65) --
// alarma EXACTA (AlarmManager.setExactAndAllowWhileIdle), con degradación a
// inexacta si el usuario no concedió SCHEDULE_EXACT_ALARM (en vez de no
// recordar nada). Clase propia (no reutiliza ReminderScheduler de Agenda
// directamente) para no acoplar los dos dominios -- cada uno programa su
// propio receiver/canal/notificación, ya documentado como decisión
// aceptable en el backlog (ambas HUs comparten el PATRÓN, no la instancia).
@Singleton
class NoteReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    // No programa nada si la fecha de disparo ya pasó (ej. al reprogramar
    // tras un reinicio, el recordatorio venció mientras el dispositivo
    // estaba apagado).
    fun schedule(noteId: String, title: String, triggerAtMillis: Long) {
        val manager = alarmManager
        if (manager == null || triggerAtMillis <= System.currentTimeMillis()) return
        scheduleAlarm(manager, triggerAtMillis, reminderPendingIntent(noteId, title), noteId)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleAlarm(
        manager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent, noteId: String
    ) {
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                Timber.w("Sin permiso de alarma exacta -- recordatorio de nota $noteId degradado a inexacto")
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Carrera posible: el usuario revoca el permiso entre el chequeo
            // de canScheduleExactAlarms() y esta llamada -- no debe crashear
            // la app, solo perder precisión.
            Timber.e(e, "SecurityException programando recordatorio de nota $noteId, reintentando inexacto")
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(noteId: String) {
        val manager = alarmManager ?: return
        manager.cancel(reminderPendingIntent(noteId, title = ""))
    }

    // Las extras (título) no forman parte de la igualdad de un PendingIntent
    // (solo componente/acción/datos/categorías) -- por eso cancel() puede
    // pasar un título vacío y sigue matcheando el mismo PendingIntent que se
    // programó con el título real.
    private fun reminderPendingIntent(noteId: String, title: String): PendingIntent {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_reminder_id"
        const val EXTRA_NOTE_TITLE = "note_reminder_title"
    }
}
