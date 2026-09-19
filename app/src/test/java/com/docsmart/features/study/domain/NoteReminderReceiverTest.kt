package com.docsmart.features.study.domain

import android.content.Context
import android.content.Intent
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Cubre la guarda de `NoteReminderReceiver.onReceive()`. Mismo alcance y
 * mismas limitaciones de entorno que AgendaReminderReceiverTest: el resto
 * del método (`showNotification()`) construye un `Intent`/
 * `NotificationCompat.Builder` reales que revientan sin Robolectric.
 */
class NoteReminderReceiverTest {
    @Test
    fun `onReceive sin el extra de NOTE_ID no toca el Context en absoluto`() {
        val context = mockk<Context>()
        val intent = mockk<Intent>()
        every { intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID) } returns null

        NoteReminderReceiver().onReceive(context, intent)

        verify(exactly = 1) { intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID) }
        confirmVerified(context)
    }
}
