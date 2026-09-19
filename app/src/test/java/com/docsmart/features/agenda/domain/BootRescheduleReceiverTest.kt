package com.docsmart.features.agenda.domain

import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.features.study.domain.NoteReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cubre `rescheduleAllReminders()` (extraída de
 * `BootRescheduleReceiver.onReceive()` -- ver comentario en esa clase: el
 * propio `onReceive()` no es testeable sin Robolectric porque llama a
 * `goAsync()`, un stub real de Android en el classpath de test JVM puro que
 * usa este proyecto). Esta es la lógica que reprograma Agenda + Notas tras
 * un reinicio del dispositivo (HU-65 / backlog UX #52).
 */
class BootRescheduleReceiverTest {
    private lateinit var agendaEventDao: AgendaEventDao
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var noteDao: NoteDao
    private lateinit var noteReminderScheduler: NoteReminderScheduler

    @BeforeEach
    fun setUp() {
        agendaEventDao = mockk()
        reminderScheduler = mockk(relaxed = true)
        noteDao = mockk()
        noteReminderScheduler = mockk(relaxed = true)
    }

    private fun agendaEvent(id: String) =
        AgendaEventEntity(
            id = id,
            title = "Evento $id",
            dateTimeMillis = System.currentTimeMillis() + 3_600_000,
            reminderMinutesBefore = 10,
            createdAt = System.currentTimeMillis(),
        )

    private fun note(
        id: String,
        reminderAt: Long?,
    ) = NoteEntity(
        id = id,
        title = "Nota $id",
        text = "contenido",
        createdAt = System.currentTimeMillis(),
        reminderAt = reminderAt,
    )

    @Test
    fun `reprograma cada evento de Agenda devuelto por el DAO`() =
        runTest {
            val event1 = agendaEvent("evt-1")
            val event2 = agendaEvent("evt-2")
            coEvery { agendaEventDao.getAllWithReminder() } returns listOf(event1, event2)
            coEvery { noteDao.getAllWithReminder() } returns emptyList()

            rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)

            coVerify(exactly = 1) { reminderScheduler.schedule(event1) }
            coVerify(exactly = 1) { reminderScheduler.schedule(event2) }
        }

    @Test
    fun `reprograma cada nota con recordatorio devuelta por el DAO`() =
        runTest {
            coEvery { agendaEventDao.getAllWithReminder() } returns emptyList()
            val reminderAt = System.currentTimeMillis() + 60_000
            coEvery { noteDao.getAllWithReminder() } returns listOf(note("note-1", reminderAt))

            rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)

            coVerify(exactly = 1) { noteReminderScheduler.schedule("note-1", "Nota note-1", reminderAt) }
        }

    // Defensivo: el DAO ya filtra por `reminderAt IS NOT NULL`, pero si
    // alguna vez devolviera una fila con reminderAt nulo (ej. un cambio de
    // query futuro que se le escape a alguien), el `?.let` de
    // rescheduleAllReminders no debe llamar a noteReminderScheduler.schedule()
    // con un triggerAtMillis inventado.
    @Test
    fun `no programa una nota sin reminderAt aunque el DAO la incluya por error`() =
        runTest {
            coEvery { agendaEventDao.getAllWithReminder() } returns emptyList()
            coEvery { noteDao.getAllWithReminder() } returns listOf(note("note-sin-recordatorio", reminderAt = null))

            rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)

            coVerify(exactly = 0) { noteReminderScheduler.schedule(any(), any(), any()) }
        }

    @Test
    fun `sin eventos ni notas pendientes no llama a ningun scheduler`() =
        runTest {
            coEvery { agendaEventDao.getAllWithReminder() } returns emptyList()
            coEvery { noteDao.getAllWithReminder() } returns emptyList()

            rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)

            coVerify(exactly = 0) { reminderScheduler.schedule(any()) }
            coVerify(exactly = 0) { noteReminderScheduler.schedule(any(), any(), any()) }
        }

    // Ronda 16: antes un solo schedule() que lanzara abortaba el forEach y los
    // recordatorios siguientes (y todas las notas) quedaban sin reprogramar
    // tras el reinicio.
    @Test
    fun `un evento que falla al reprogramarse no impide reprogramar los demas ni las notas`() =
        runTest {
            val failing = agendaEvent("evt-falla")
            val healthy = agendaEvent("evt-sano")
            coEvery { agendaEventDao.getAllWithReminder() } returns listOf(failing, healthy)
            val reminderAt = System.currentTimeMillis() + 60_000
            coEvery { noteDao.getAllWithReminder() } returns listOf(note("note-1", reminderAt))
            every { reminderScheduler.schedule(failing) } throws IllegalStateException("AlarmManager rechazo la alarma")

            rescheduleAllReminders(agendaEventDao, reminderScheduler, noteDao, noteReminderScheduler)

            coVerify(exactly = 1) { reminderScheduler.schedule(healthy) }
            coVerify(exactly = 1) { noteReminderScheduler.schedule("note-1", "Nota note-1", reminderAt) }
        }
}
