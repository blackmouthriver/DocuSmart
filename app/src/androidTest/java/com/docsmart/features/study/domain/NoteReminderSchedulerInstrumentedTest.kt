package com.docsmart.features.study.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [NoteReminderScheduler] contra el AlarmManager real: programar una alarma futura crea su
 * PendingIntent, cancelarla lo libera, y una fecha pasada no programa nada. Ids únicos y se
 * cancela todo al terminar para no dejar alarmas en el dispositivo.
 */
class NoteReminderSchedulerInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler by lazy { NoteReminderScheduler(context) }
    private val scheduledIds = mutableListOf<String>()

    @After
    fun cancelAll() {
        scheduledIds.forEach { scheduler.cancel(it) }
    }

    // Mismo Intent que arma el scheduler (las extras no cuentan para la igualdad de un PendingIntent).
    private fun existingPendingIntent(noteId: String): PendingIntent? {
        val intent =
            Intent(context, NoteReminderReceiver::class.java).apply {
                data = Uri.parse("docusmart://note-reminder/$noteId")
            }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun newId(): String = "instrumented-scheduler-${System.nanoTime()}".also { scheduledIds += it }

    @Test
    fun schedule_conFechaFutura_creaLaAlarmaYCancelLaLibera() {
        val noteId = newId()

        scheduler.schedule(noteId, "Repasar", System.currentTimeMillis() + 60 * 60 * 1000L)
        assertNotNull(existingPendingIntent(noteId))

        scheduler.cancel(noteId)
        assertNull(existingPendingIntent(noteId))
    }

    @Test
    fun schedule_conFechaPasada_noProgramaNada() {
        val noteId = newId()

        scheduler.schedule(noteId, "Vencida", System.currentTimeMillis() - 1_000L)

        assertNull(existingPendingIntent(noteId))
    }

    @Test
    fun canScheduleExactAlarms_coincideConElSistema() {
        val expected =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                true
            } else {
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            }

        assertEquals(expected, scheduler.canScheduleExactAlarms())
    }
}
