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
        scheduleAlarm(manager, triggerAt, pendingIntent)
    }

    // Hallazgo real (ronda 14): CrashlyticsTree reenvía TODO log >=WARN a
    // Firebase Crashlytics vía `crashlytics.log(mensaje completo)`, y en el
    // caso de una excepción real (>=WARN con Throwable) también llama a
    // `recordException(t)` con la excepción tal cual se le pasó -- loguear
    // el eventId (identificador de un evento real del usuario) filtraba ese
    // dato a un tercero sin necesidad, algo que ya se corrigió antes con el
    // mismo patrón en PdfPasswordUseCase/DownloadsAccessManager: solo el
    // tipo de excepción, nunca el mensaje/dato real.
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
                Timber.w("Sin permiso de alarma exacta -- recordatorio degradado a inexacto")
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Carrera posible: el usuario revoca el permiso entre el chequeo
            // de canScheduleExactAlarms() y esta llamada -- no debe crashear
            // la app, solo perder precisión.
            Timber.e(redactedForLog(e), "SecurityException programando recordatorio, reintentando inexacto")
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun redactedForLog(e: SecurityException) = SecurityException("ReminderScheduler: ${e.javaClass.simpleName}")

    // Hallazgo real (ronda 14): manager.cancel(pendingIntent) solo quita la
    // alarma registrada en AlarmManager, pero el PendingIntent en sí (con su
    // Uri de datos única por eventId, ver comentario de reminderPendingIntent
    // más abajo) queda vivo en la tabla interna de PendingIntent del sistema
    // hasta que el proceso muere -- con un evento creado/cancelado por cada
    // recordatorio que el usuario borra o reprograma, esos tokens se
    // acumulan indefinidamente. `pendingIntent.cancel()` libera también el
    // token en sí, no solo su registro en AlarmManager.
    fun cancel(eventId: String) {
        val manager = alarmManager ?: return
        releaseAlarm(manager, reminderPendingIntent(eventId, title = ""))
    }

    // Separado de cancel() para poder testear la liberación en sí (el fix de
    // esta ronda) con un PendingIntent mockeado, sin pasar por el
    // `Intent(context, ...)` real de reminderPendingIntent() -- ver el mismo
    // criterio documentado arriba en scheduleAlarm().
    internal fun releaseAlarm(manager: AlarmManager, pendingIntent: PendingIntent) {
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
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
