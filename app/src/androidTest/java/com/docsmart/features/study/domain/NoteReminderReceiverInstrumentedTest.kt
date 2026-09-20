package com.docsmart.features.study.domain

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [NoteReminderReceiver] con un Intent real y el contexto real de instrumentación: no debe caerse
 * sin extras y, con extras, publica (si hay permiso de notificaciones) una notificación con su
 * título, canal y acción de apertura. Cada prueba usa un id único y cancela lo que publicó.
 */
class NoteReminderReceiverInstrumentedTest {
    @get:Rule
    val permissionRule = grantNotificationsRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val postedIds = mutableListOf<Int>()

    @After
    fun cancelPosted() {
        postedIds.forEach { manager.cancel(TAG, it) }
    }

    private fun receive(
        noteId: String?,
        title: String?,
    ) {
        val intent = Intent()
        if (noteId != null) intent.putExtra(NoteReminderScheduler.EXTRA_NOTE_ID, noteId)
        if (title != null) intent.putExtra(NoteReminderScheduler.EXTRA_NOTE_TITLE, title)
        if (noteId != null) postedIds += noteId.hashCode()
        NoteReminderReceiver().onReceive(context, intent)
    }

    private fun notificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun titleOf(noteId: String): String? {
        val posted = awaitActiveNotification(manager, TAG, noteId.hashCode())
        assertNotNull(posted)
        return posted!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    @Test
    fun sinIdEnLasExtras_noPublicaNadaYNoFalla() {
        val before = manager.activeNotifications.count { it.tag == TAG }

        receive(noteId = null, title = "Titulo sin id")

        assertEquals(before, manager.activeNotifications.count { it.tag == TAG })
    }

    @Test
    fun conIdYTitulo_publicaLaNotificacionDeRecordatorio() {
        val noteId = "instrumented-note-${System.nanoTime()}"

        receive(noteId, "Repasar el capitulo 3")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertNotNull(manager.getNotificationChannel("study_note_reminders"))
        }
        if (!notificationsEnabled()) return
        assertEquals("Repasar el capitulo 3", titleOf(noteId))
        val notification = awaitActiveNotification(manager, TAG, noteId.hashCode())!!.notification
        assertNotNull(notification.contentIntent)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun conTituloVacio_usaElTituloPorDefecto() {
        val noteId = "instrumented-note-vacio-${System.nanoTime()}"

        receive(noteId, "")

        if (!notificationsEnabled()) return
        assertEquals(context.getString(R.string.note_reminder_notification_fallback_title), titleOf(noteId))
    }

    @Test
    fun sinTituloEnLasExtras_tambienUsaElTituloPorDefecto() {
        val noteId = "instrumented-note-sintitulo-${System.nanoTime()}"

        receive(noteId, title = null)

        if (!notificationsEnabled()) return
        assertEquals(context.getString(R.string.note_reminder_notification_fallback_title), titleOf(noteId))
    }

    private companion object {
        const val TAG = "note_reminder"
    }
}
