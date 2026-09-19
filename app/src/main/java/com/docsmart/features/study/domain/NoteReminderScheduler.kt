package com.docsmart.features.study.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        scheduleAlarm(manager, triggerAtMillis, reminderPendingIntent(noteId, title))
    }

    // Hallazgo real (ronda 14): mismo problema ya corregido en
    // ReminderScheduler.kt (Agenda) -- CrashlyticsTree reenvía TODO log
    // >=WARN a Firebase Crashlytics (crashlytics.log(mensaje completo), y
    // recordException(t) si trae una excepción real), así que loguear el
    // noteId filtraba un identificador real del usuario a un tercero sin
    // necesidad. Mismo patrón de redacción que PdfPasswordUseCase/
    // DownloadsAccessManager: solo el tipo de excepción, nunca el dato.
    //
    // `internal` (no `private`) a propósito: recibe un PendingIntent ya
    // construido, así que puede testearse con un mock sin necesitar el
    // `Intent(context, ...)` real que arma reminderPendingIntent() -- este
    // proyecto no usa Robolectric y un Intent real revienta bajo su stub de
    // Android en tests JVM puros (mismo criterio ya documentado en
    // DocumentSharingTest/resolveShareUri).
    @Suppress("TooGenericExceptionCaught")
    internal fun scheduleAlarm(manager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                Timber.w("Sin permiso de alarma exacta -- recordatorio de nota degradado a inexacto")
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Carrera posible: el usuario revoca el permiso entre el chequeo
            // de canScheduleExactAlarms() y esta llamada -- no debe crashear
            // la app, solo perder precisión.
            Timber.e(redactedForLog(e), "SecurityException programando recordatorio de nota, reintentando inexacto")
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun redactedForLog(e: SecurityException) =
        SecurityException("NoteReminderScheduler: ${e.javaClass.simpleName}")

    // Hallazgo real (ronda 14): mismo problema ya corregido en
    // ReminderScheduler.kt -- manager.cancel(pendingIntent) solo quita la
    // alarma de AlarmManager, pero el PendingIntent en sí (con su Uri
    // implícita en NoteReminderReceiver, ver reminderPendingIntent abajo)
    // seguía vivo en la tabla interna del sistema. pendingIntent.cancel()
    // libera también el token.
    fun cancel(noteId: String) {
        val manager = alarmManager ?: return
        releaseAlarm(manager, reminderPendingIntent(noteId, title = ""))
    }

    // Separado de cancel() para poder testear la liberación en sí (el fix de
    // esta ronda) con un PendingIntent mockeado, sin pasar por el
    // `Intent(context, ...)` real de reminderPendingIntent() -- mismo
    // criterio que ReminderScheduler.releaseAlarm() (Agenda).
    internal fun releaseAlarm(manager: AlarmManager, pendingIntent: PendingIntent) {
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    // Las extras (título) no forman parte de la igualdad de un PendingIntent
    // (solo componente/acción/datos/categorías) -- por eso cancel() puede
    // pasar un título vacío y sigue matcheando el mismo PendingIntent que se
    // programó con el título real.
    //
    // Hallazgo real (ronda 14): a diferencia de
    // ReminderScheduler.reminderPendingIntent() (Agenda, ya corregido en la
    // auditoría 2026-09-18 -- Media), este PendingIntent NO tenía `data`
    // propio, solo `noteId.hashCode()` como requestCode -- dos notas
    // distintas cuyo hashCode colisionara (32 bits, con miles de notas no es
    // improbable) compartían el mismo PendingIntent, y FLAG_UPDATE_CURRENT
    // reemplazaba en silencio la alarma de la primera nota por la de la
    // segunda, perdiendo su recordatorio sin ningún aviso. Mismo fix que el
    // de Agenda: `setData()` con una Uri única por noteId vuelve al Intent
    // siempre distinto (data SÍ es parte de la igualdad de un PendingIntent).
    private fun reminderPendingIntent(noteId: String, title: String): PendingIntent {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            data = Uri.parse("docusmart://note-reminder/$noteId")
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
