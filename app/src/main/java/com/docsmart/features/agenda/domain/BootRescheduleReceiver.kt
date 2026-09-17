package com.docsmart.features.agenda.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.docsmart.core.data.db.AgendaEventDao
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
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var agendaEventDao: AgendaEventDao
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = agendaEventDao.getAllWithReminder()
                events.forEach { reminderScheduler.schedule(it) }
                Timber.d("BootRescheduleReceiver: ${events.size} recordatorios de Agenda reprogramados")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
