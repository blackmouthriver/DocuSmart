package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Evento de Agenda (HU-65, backlog UX 2026-09-16 -- feedback real de
 * testers de la prueba cerrada): reunión, entrega o fecha importante con
 * recordatorio opcional. `documentId` sigue el mismo convenio que el resto
 * de la app (Uri/ruta absoluta), null si el evento no está vinculado a
 * ningún documento de la Biblioteca.
 *
 * `reminderMinutesBefore`: null = sin recordatorio; 0 = notificar justo a
 * `dateTimeMillis`; positivo = esa cantidad de minutos antes. El instante
 * real de disparo (`dateTimeMillis - reminderMinutesBefore * 60_000`) lo
 * calcula `ReminderScheduler`, no se guarda por separado para no tener dos
 * fuentes de verdad si el usuario edita la fecha del evento.
 */
@Entity(tableName = "agenda_events", indices = [Index("documentId")])
data class AgendaEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val dateTimeMillis: Long,
    val documentId: String? = null,
    val reminderMinutesBefore: Int? = null,
    val createdAt: Long
)
