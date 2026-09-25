package com.docsmart.features.agenda.data

import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.ReminderScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * HU-65: verifica que el repositorio nunca deje la base de datos y el
 * scheduler de recordatorios desincronizados -- cada operación de
 * escritura (crear/editar/borrar) tiene que ir acompañada de la llamada
 * correspondiente a `ReminderScheduler`.
 */
class AgendaRepositoryTest {
    private val dao = mockk<AgendaEventDao>()
    private val scheduler = mockk<ReminderScheduler>()
    private val repository = AgendaRepository(dao, scheduler)

    @BeforeEach
    fun setUp() {
        coEvery { dao.insert(any()) } just Runs
        every { scheduler.schedule(any()) } just Runs
        every { scheduler.cancel(any()) } just Runs
    }

    @Test
    fun `createEvent guarda el evento y programa el recordatorio`() =
        runTest {
            val saved = slot<AgendaEventEntity>()
            coEvery { dao.insert(capture(saved)) } just Runs

            val result =
                repository.createEvent(
                    title = "Reunión",
                    description = "Con el equipo",
                    dateTimeMillis = 5_000L,
                    documentId = "doc-1",
                    reminderMinutesBefore = 60,
                )

            assertEquals("Reunión", saved.captured.title)
            assertEquals(5_000L, saved.captured.dateTimeMillis)
            assertEquals(result.id, saved.captured.id)
            coVerify { dao.insert(any()) }
            coVerify { scheduler.schedule(match { it.id == result.id }) }
        }

    @Test
    fun `updateEvent cancela el recordatorio viejo antes de programar el nuevo`() =
        runTest {
            val event =
                AgendaEventEntity(
                    id = "e1",
                    title = "Editado",
                    description = null,
                    dateTimeMillis = 9_000L,
                    documentId = null,
                    reminderMinutesBefore = 15,
                    createdAt = 1_000L,
                )

            repository.updateEvent(event)

            coVerifyOrder {
                scheduler.cancel("e1")
                dao.insert(event)
                scheduler.schedule(event)
            }
        }

    @Test
    fun `deleteEvent cancela el recordatorio y borra la fila`() =
        runTest {
            coEvery { dao.delete(any()) } just Runs

            repository.deleteEvent("e1")

            coVerifyOrder {
                scheduler.cancel("e1")
                dao.delete("e1")
            }
        }

    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda, Alta):
    // ver el comentario de rescheduleAllReminders() en AgendaRepository --
    // se invoca al detectar que Android revocó el permiso de alarmas exactas,
    // no solo tras un reinicio (eso lo cubre BootRescheduleReceiverTest).
    @Test
    fun `rescheduleAllReminders reprograma cada evento con recordatorio pendiente`() =
        runTest {
            val event1 =
                AgendaEventEntity(
                    id = "e1",
                    title = "Uno",
                    dateTimeMillis = 1_000L,
                    reminderMinutesBefore = 10,
                    createdAt = 0L,
                )
            val event2 = event1.copy(id = "e2", title = "Dos")
            coEvery { dao.getAllWithReminder() } returns listOf(event1, event2)

            repository.rescheduleAllReminders()

            coVerify(exactly = 1) { scheduler.schedule(event1) }
            coVerify(exactly = 1) { scheduler.schedule(event2) }
        }

    // Mismo criterio que BootRescheduleReceiver.rescheduleGuarded(): un evento
    // que falle al reprogramarse (ej. AlarmManager rechaza la alarma) no debe
    // impedir que se reprogramen los demás.
    @Test
    fun `rescheduleAllReminders no aborta los demas eventos si uno falla al reprogramarse`() =
        runTest {
            val failing =
                AgendaEventEntity(
                    id = "e-falla",
                    title = "Falla",
                    dateTimeMillis = 1_000L,
                    reminderMinutesBefore = 10,
                    createdAt = 0L,
                )
            val healthy = failing.copy(id = "e-sano", title = "Sano")
            coEvery { dao.getAllWithReminder() } returns listOf(failing, healthy)
            every { scheduler.schedule(failing) } throws IllegalStateException("AlarmManager rechazo la alarma")

            repository.rescheduleAllReminders()

            coVerify(exactly = 1) { scheduler.schedule(healthy) }
        }

    @Test
    fun `rescheduleAllReminders propaga una cancelacion en vez de tratarla como un fallo mas`() =
        runTest {
            val event =
                AgendaEventEntity(
                    id = "e1",
                    title = "Uno",
                    dateTimeMillis = 1_000L,
                    reminderMinutesBefore = 10,
                    createdAt = 0L,
                )
            coEvery { dao.getAllWithReminder() } returns listOf(event)
            every { scheduler.schedule(event) } throws CancellationException("cancelado")

            var cancelled = false
            try {
                repository.rescheduleAllReminders()
            } catch (e: CancellationException) {
                cancelled = true
                assertEquals("cancelado", e.message)
            }

            assertTrue(cancelled, "la CancellationException debia propagarse")
        }

    // Ningún otro test llama a observeAll()/getById() -- son simples
    // delegados a AgendaEventDao, pero quedaban sin ejercer.
    @Test
    fun `observeAll delega en el dao`() {
        val flow = mockk<kotlinx.coroutines.flow.Flow<List<AgendaEventEntity>>>()
        every { dao.observeAll() } returns flow

        assertEquals(flow, repository.observeAll())
    }

    @Test
    fun `getById delega en el dao`() =
        runTest {
            val event =
                AgendaEventEntity(
                    id = "e1",
                    title = "Uno",
                    dateTimeMillis = 1_000L,
                    reminderMinutesBefore = null,
                    createdAt = 0L,
                )
            coEvery { dao.getById("e1") } returns event

            assertEquals(event, repository.getById("e1"))
        }
}
