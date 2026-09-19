package com.docsmart.features.study.domain

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

// Backlog UX #52: dispara la notificación local en el instante exacto que
// programó NoteReminderScheduler. Receiver "tonto" a propósito -- no
// consulta la base de datos ni depende de Hilt, toda la información que
// necesita (id/título de la nota) ya viaja en las extras del propio
// Intent, mismo criterio que AgendaReminderReceiver (HU-65).
class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val noteId = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_TITLE).orEmpty()
        createNotificationChannelIfNeeded(context)
        showNotification(context, noteId, title)
    }

    private fun showNotification(
        context: Context,
        noteId: String,
        title: String,
    ) {
        // Hallazgo real (revisión de esta misma HU): un PendingIntent hacia
        // MainActivity con FLAG_IMMUTABLE sin FLAG_UPDATE_CURRENT es "el
        // mismo" para Android si comparte requestCode con el que ya arma
        // AgendaReminderReceiver.kt (mismo componente destino, extras no
        // cuentan para la igualdad) -- una colisión de hashCode entre un
        // noteId y un eventId podía reabrir el ítem equivocado. `setData()`
        // con una Uri única por dominio+id vuelve al Intent siempre
        // distinto (data SÍ es parte de la igualdad de un PendingIntent).
        val openAppIntent =
            PendingIntent.getActivity(
                context,
                noteId.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    data = Uri.parse("docusmart://note-reminder/$noteId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
                },
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_note)
                .setContentTitle(title.ifBlank { context.getString(R.string.note_reminder_notification_fallback_title) })
                .setContentText(context.getString(R.string.note_reminder_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent)
                .build()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // `tag` propio: evita que esta notificación se pise con una de
        // Agenda si sus hashCode coinciden, mismo criterio que el `data`.
        manager.notify(NOTIFICATION_TAG, noteId.hashCode(), notification)
    }

    private fun createNotificationChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.note_reminder_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "study_note_reminders"
        private const val NOTIFICATION_TAG = "note_reminder"
    }
}
