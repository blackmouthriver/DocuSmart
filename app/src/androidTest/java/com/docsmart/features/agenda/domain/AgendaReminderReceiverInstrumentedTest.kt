package com.docsmart.features.agenda.domain

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.features.study.domain.awaitActiveNotification
import com.docsmart.features.study.domain.grantNotificationsRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [AgendaReminderReceiver] con un Intent real y el contexto real de instrumentación: sin extras no
 * publica ni falla; con extras publica (si hay permiso) la notificación con su título, canal y
 * acción de apertura. Ids únicos y se cancela lo publicado.
 */
class AgendaReminderReceiverInstrumentedTest {
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
        eventId: String?,
        title: String?,
    ) {
        val intent = Intent()
        if (eventId != null) intent.putExtra(ReminderScheduler.EXTRA_EVENT_ID, eventId)
        if (title != null) intent.putExtra(ReminderScheduler.EXTRA_EVENT_TITLE, title)
        if (eventId != null) postedIds += eventId.hashCode()
        AgendaReminderReceiver().onReceive(context, intent)
    }

    private fun notificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun titleOf(eventId: String): String? {
        val posted = awaitActiveNotification(manager, TAG, eventId.hashCode())
        assertNotNull(posted)
        return posted!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    @Test
    fun sinIdEnLasExtras_noPublicaNadaYNoFalla() {
        val before = manager.activeNotifications.count { it.tag == TAG }

        receive(eventId = null, title = "Evento sin id")

        assertEquals(before, manager.activeNotifications.count { it.tag == TAG })
    }

    @Test
    fun conIdYTitulo_publicaLaNotificacionDeAgenda() {
        val eventId = "instrumented-event-${System.nanoTime()}"

        receive(eventId, "Reunion de estudio")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertNotNull(manager.getNotificationChannel("agenda_reminders"))
        }
        if (!notificationsEnabled()) return
        assertEquals("Reunion de estudio", titleOf(eventId))
        val notification = awaitActiveNotification(manager, TAG, eventId.hashCode())!!.notification
        assertNotNull(notification.contentIntent)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun conTituloVacio_usaElTituloPorDefecto() {
        val eventId = "instrumented-event-vacio-${System.nanoTime()}"

        receive(eventId, "")

        if (!notificationsEnabled()) return
        assertEquals(context.getString(R.string.agenda_reminder_notification_fallback_title), titleOf(eventId))
    }

    @Test
    fun sinTituloEnLasExtras_tambienUsaElTituloPorDefecto() {
        val eventId = "instrumented-event-sintitulo-${System.nanoTime()}"

        receive(eventId, title = null)

        if (!notificationsEnabled()) return
        assertEquals(context.getString(R.string.agenda_reminder_notification_fallback_title), titleOf(eventId))
    }

    private companion object {
        const val TAG = "agenda_reminder"
    }
}
