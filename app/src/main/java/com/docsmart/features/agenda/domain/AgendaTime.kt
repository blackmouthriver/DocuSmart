package com.docsmart.features.agenda.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// HU-65, RF4: clasificación puramente por día calendario (no por si la hora
// exacta ya pasó) -- un evento de "hoy a las 9am" sigue mostrándose como
// "hoy" a las 11am, no salta a "vencido" hasta que cambia el día. Mismo
// criterio que usa cualquier agenda/calendario convencional.
enum class AgendaEventStatus { OVERDUE, TODAY, UPCOMING }

fun classifyAgendaEvent(
    dateTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): AgendaEventStatus {
    val eventDay = Instant.ofEpochMilli(dateTimeMillis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when {
        eventDay.isBefore(today) -> AgendaEventStatus.OVERDUE
        eventDay.isEqual(today) -> AgendaEventStatus.TODAY
        else -> AgendaEventStatus.UPCOMING
    }
}

fun reminderTriggerMillis(
    dateTimeMillis: Long,
    reminderMinutesBefore: Int,
): Long = dateTimeMillis - reminderMinutesBefore * 60_000L

// Vista de calendario (backlog UX 2026-09-16, seguimiento de HU-65): día
// calendario del evento en la zona horaria local, para agrupar eventos por
// celda del grid mensual -- mismo criterio de "día calendario" que
// classifyAgendaEvent (no depende de la hora exacta).
fun agendaEventLocalDate(
    dateTimeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate = Instant.ofEpochMilli(dateTimeMillis).atZone(zoneId).toLocalDate()

// Opciones fijas de antelación (RF5) -- null = "sin recordatorio", 0 = justo
// a la hora del evento. Números crudos en el dominio; las etiquetas
// traducidas viven en la capa de presentación (stringResource).
object ReminderPreset {
    const val AT_TIME = 0
    const val MINUTES_15 = 15
    const val HOUR_1 = 60
    const val DAY_1 = 1_440
    val ALL = listOf(null, AT_TIME, MINUTES_15, HOUR_1, DAY_1)
}
