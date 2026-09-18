package com.docsmart.features.agenda.data

import com.docsmart.core.data.db.AgendaEventDao
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    // Hallazgo real de la auditoría de Agenda 2026-09-18 (Alta): sin
    // serializar por eventId, updateEvent() y deleteEvent() lanzados desde
    // corutinas IO independientes para el MISMO evento (ej. el usuario edita
    // y borra casi al mismo tiempo) podían completarse fuera de orden -- si
    // deleteEvent() corría primero y updateEvent() terminaba después,
    // `agendaEventDao.insert()` (Room OnConflictStrategy.REPLACE)
    // reinsertaba la fila ya borrada y `reminderScheduler.schedule()` armaba
    // una alarma para un evento que la UI ya mostraba como eliminado. Un
    // Mutex por eventId serializa las escrituras del mismo evento sin
    // bloquear eventos distintos entre sí. El mapa puede crecer mientras
    // vive la app, pero la cantidad de eventIds distintos que un usuario
    // toca en una sesión es chica -- se acepta esa acumulación acotada en
    // vez de arriesgar una limpieza que borre un Mutex todavía en uso por
    // otra corutina.
    // Revisión adversarial de correctitud (ronda 12): `getOrPut` de
    // kotlin.collections.* (el import implícito) hace un get()+put() NO
    // atómico, incluso sobre un ConcurrentHashMap -- dos corrutinas
    // llamando a withEventLock() para el MISMO eventId por primera vez
    // (antes de que su entrada exista en el mapa) podían crear dos
    // Mutex() distintos y bloquear cada una sobre el suyo, sin exclusión
    // mutua real entre ellas -- exactamente la carrera que este mecanismo
    // debía prevenir. `computeIfAbsent` es el método atómico nativo de
    // ConcurrentHashMap para este propósito.
    private val eventMutexes = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> withEventLock(eventId: String, block: suspend () -> T): T =
        eventMutexes.computeIfAbsent(eventId) { Mutex() }.withLock { block() }

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
        withEventLock(event.id) {
            reminderScheduler.cancel(event.id)
            agendaEventDao.insert(event)
            reminderScheduler.schedule(event)
        }
    }

    suspend fun deleteEvent(id: String) = withContext(Dispatchers.IO) {
        withEventLock(id) {
            reminderScheduler.cancel(id)
            agendaEventDao.delete(id)
        }
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
