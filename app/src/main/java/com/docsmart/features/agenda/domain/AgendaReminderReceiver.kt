package com.docsmart.features.agenda.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.docsmart.MainActivity
import com.docsmart.R

// HU-65: dispara la notificación local en el instante exacto que programó
// ReminderScheduler. Servicio "tonto" a propósito -- no consulta la base de
// datos ni depende de Hilt, toda la información que necesita (id/título del
// evento) ya viaja en las extras del propio Intent, mismo criterio de
// simplicidad que PomodoroTimerService.
class AgendaReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_TITLE).orEmpty()
        createNotificationChannelIfNeeded(context)
        showNotification(context, eventId, title)
    }

    private fun showNotification(context: Context, eventId: String, title: String) {
        // Hallazgo real (revisión HU-52): dos PendingIntent hacia el mismo
        // componente (MainActivity), FLAG_IMMUTABLE sin FLAG_UPDATE_CURRENT,
        // son "el mismo" para Android si comparten requestCode -- y las
        // extras NO forman parte de esa igualdad. Con dos dominios de
        // recordatorio (Agenda + Notas, #52) usando cada uno `id.hashCode()`
        // como requestCode, una colisión entre un eventId y un noteId podía
        // reabrir el ítem equivocado. `setData()` con una Uri única por
        // dominio+id vuelve al Intent siempre distinto (data SÍ es parte de
        // la igualdad de un PendingIntent), sin depender de que el
        // requestCode nunca choque.
        val openAppIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("docusmart://agenda-reminder/$eventId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_AGENDA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_agenda)
            .setContentTitle(title.ifBlank { context.getString(R.string.agenda_reminder_notification_fallback_title) })
            .setContentText(context.getString(R.string.agenda_reminder_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // `tag` propio (no solo el id numérico): evita que una notificación
        // de Agenda y una de Notas se pisen entre sí si sus hashCode
        // coinciden, mismo criterio que el `data` de arriba.
        manager.notify(NOTIFICATION_TAG, eventId.hashCode(), notification)
    }

    private fun createNotificationChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.agenda_reminder_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "agenda_reminders"
        private const val NOTIFICATION_TAG = "agenda_reminder"
    }
}
