package com.docsmart.features.agenda.data

import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// HU-65: además de las operaciones de Room, (re)programa o cancela la
// alarma exacta correspondiente en el mismo lugar -- así ningún llamador
// (ViewModel, futuros callers) puede olvidarse de mantener el recordatorio
// sincronizado con el evento real, mismo criterio de "un solo punto de
// verdad" que ya usa DocumentIdentityMaintenance para las tablas por
// documento.
@Singleton
class AgendaRepository @Inject constructor(
    private val agendaEventDao: AgendaEventDao,
    private val reminderScheduler: ReminderScheduler
) {
    fun observeAll(): Flow<List<AgendaEventEntity>> = agendaEventDao.observeAll()

    suspend fun getById(id: String): AgendaEventEntity? = agendaEventDao.getById(id)

    suspend fun createEvent(
        title: String,
        description: String?,
        dateTimeMillis: Long,
        documentId: String?,
        reminderMinutesBefore: Int?
    ): AgendaEventEntity = withContext(Dispatchers.IO) {
        val event = AgendaEventEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            dateTimeMillis = dateTimeMillis,
            documentId = documentId,
            reminderMinutesBefore = reminderMinutesBefore,
            createdAt = System.currentTimeMillis()
        )
        agendaEventDao.insert(event)
        reminderScheduler.schedule(event)
        event
    }

    // AC4: editar un evento con recordatorio ya programado nunca debe dejar
    // dos alarmas activas para el mismo evento -- se cancela la anterior
    // (misma requestCode = event.id.hashCode(), así que en la práctica esto
    // es sobre todo defensivo: reprogramar con el mismo id ya reemplaza la
    // alarma previa vía FLAG_UPDATE_CURRENT) antes de programar la nueva.
    suspend fun updateEvent(event: AgendaEventEntity) = withContext(Dispatchers.IO) {
        reminderScheduler.cancel(event.id)
        agendaEventDao.insert(event)
        reminderScheduler.schedule(event)
    }

    suspend fun deleteEvent(id: String) = withContext(Dispatchers.IO) {
        reminderScheduler.cancel(id)
        agendaEventDao.delete(id)
    }

    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Alta): Android cancela automáticamente TODAS las alarmas exactas ya
    // programadas por la app en cuanto el usuario revoca el permiso
    // "Alarmas y recordatorios" -- antes, la única rutina que volvía a
    // programarlas era BootRescheduleReceiver, atada solo a un reinicio
    // del dispositivo, así que esos recordatorios quedaban mudos para
    // siempre hasta que el usuario editara cada evento a mano. Se
    // reutiliza acá el mismo criterio (ReminderScheduler.schedule() ya
    // degrada con elegancia a una alarma inexacta si el permiso sigue sin
    // concederse) para poder invocarlo también cuando la Agenda detecta
    // el permiso recién revocado, no solo tras un reinicio.
    suspend fun rescheduleAllReminders() = withContext(Dispatchers.IO) {
        agendaEventDao.getAllWithReminder().forEach { reminderScheduler.schedule(it) }
    }
}
