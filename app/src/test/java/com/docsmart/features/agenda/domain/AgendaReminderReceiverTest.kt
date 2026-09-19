package com.docsmart.features.agenda.domain

import android.content.Context
import android.content.Intent
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Cubre la guarda de `AgendaReminderReceiver.onReceive()`. El resto del
 * método (`showNotification()`) arma un `Intent(context, MainActivity::class.java)`
 * real y un `NotificationCompat.Builder` real -- este proyecto no usa
 * Robolectric y ese código revienta bajo el stub de Android en un test JVM
 * puro (mismo criterio ya documentado en DocumentSharingTest/resolveShareUri
 * y en ReminderSchedulerTest), así que queda fuera del alcance de un test
 * unitario. Lo que sí es 100% testeable sin tocar Android real es la guarda:
 * un broadcast sin el extra de id (nunca debería llegar así desde
 * ReminderScheduler, pero un PendingIntent es un token que en teoría
 * cualquiera con el mismo componente podría re-disparar) no debe tocar
 * `Context` en absoluto, ni intentar construir una notificación con un id
 * inexistente.
 */
class AgendaReminderReceiverTest {

    @Test
    fun `onReceive sin el extra de EVENT_ID no toca el Context en absoluto`() {
        val context = mockk<Context>()
        val intent = mockk<Intent>()
        every { intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) } returns null

        AgendaReminderReceiver().onReceive(context, intent)

        verify(exactly = 1) { intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) }
        confirmVerified(context)
    }
}
