package com.docsmart.features.agenda.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.NoteDao
import com.docsmart.features.study.domain.NoteReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// HU-65, RNF2: AlarmManager pierde TODAS las alarmas programadas cuando el
// dispositivo se apaga (a diferencia de una notificación ya mostrada, que
// sí sobrevive) -- sin esto, un recordatorio programado antes de un
// reinicio simplemente nunca sonaría. `goAsync()` porque `onReceive()` debe
// devolver el control rápido, pero reprogramar N alarmas es async (consulta
// Room) -- sin extender el ciclo de vida del receiver, el proceso podría
// morir a mitad de la consulta.
//
// Backlog UX #52: también reprograma los recordatorios de Notas (mismo
// vacío de infraestructura documentado en el backlog para ambas HUs) --
// un solo BOOT_COMPLETED para las dos, en vez de un segundo receiver que
// solo duplicaría el mismo ciclo de vida `goAsync()`.
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var agendaEventDao: AgendaEventDao

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var noteDao: NoteDao

    @Inject lateinit var noteReminderScheduler: NoteReminderScheduler

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Solo el tipo: CrashlyticsTree reenvía a Firebase el mensaje y el
                // Throwable completos, y un error de Room puede incluir datos reales.
                Timber.e("BootRescheduleReceiver: error reprogramando tras reinicio (${e.javaClass.simpleName})")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

// Extraído de onReceive() para poder testearlo sin Robolectric: goAsync() es
// un stub del SDK de Android que no puede invocarse desde un test JVM puro
// como los que usa este proyecto (sin runtime real de Android), así que
// onReceive() en sí queda fuera del alcance de un test unitario -- pero la
// lógica de negocio real (qué se reprograma y con qué datos, que es donde
// vivían los bugs de esta clase de componente en rondas anteriores) sí es
// una función suspend común, testeable con DAOs/schedulers mockeados. Mismo
// criterio ya usado en PomodoroEngine para separar tickPomodoro() (lógica
// pura) del código atado a la plataforma.
internal suspend fun rescheduleAllReminders(
    agendaEventDao: AgendaEventDao,
    reminderScheduler: ReminderScheduler,
    noteDao: NoteDao,
    noteReminderScheduler: NoteReminderScheduler,
) {
    val events = agendaEventDao.getAllWithReminder()
    events.forEach { event -> rescheduleGuarded { reminderScheduler.schedule(event) } }
    val notes = noteDao.getAllWithReminder()
    notes.forEach { note ->
        note.reminderAt?.let { rescheduleGuarded { noteReminderScheduler.schedule(note.id, note.title, it) } }
    }
    Timber.d(
        "BootRescheduleReceiver: ${events.size} recordatorios de Agenda + " +
            "${notes.size} de Notas reprogramados",
    )
}

// Ronda 16: antes un solo schedule() que lanzara (ej. AlarmManager rechazando
// una alarma) abortaba el forEach entero -- todos los recordatorios que
// quedaban después de ese en la lista no se reprogramaban tras el reinicio y
// el usuario los perdía en silencio. Se aísla cada uno; solo se registra el
// tipo de excepción (CrashlyticsTree reenvía el resto a Firebase).
@Suppress("TooGenericExceptionCaught")
private inline fun rescheduleGuarded(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e("BootRescheduleReceiver: un recordatorio no se pudo reprogramar (${e.javaClass.simpleName})")
    }
}
