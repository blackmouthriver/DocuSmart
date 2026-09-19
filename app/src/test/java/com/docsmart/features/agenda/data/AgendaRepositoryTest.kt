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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
}
