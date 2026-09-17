package com.docsmart.features.agenda.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.NoteDao
import com.docsmart.features.study.domain.NoteReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = agendaEventDao.getAllWithReminder()
                events.forEach { reminderScheduler.schedule(it) }
                val notes = noteDao.getAllWithReminder()
                notes.forEach { note ->
                    note.reminderAt?.let { noteReminderScheduler.schedule(note.id, note.title, it) }
                }
                Timber.d(
                    "BootRescheduleReceiver: ${events.size} recordatorios de Agenda + " +
                        "${notes.size} de Notas reprogramados"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
