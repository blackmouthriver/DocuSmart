package com.docsmart.features.agenda.presentation.components

import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.agendaEventLocalDate
import com.docsmart.features.agenda.domain.calendarLeadingBlanks
import com.docsmart.features.agenda.domain.reminderTriggerMillis
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

// Ronda 17: lógica pura extraída de AgendaCalendarView.kt y
// AgendaEventEditorDialog.kt (sin Compose) para poder testearla en JVM.

internal const val AGENDA_DAYS_IN_WEEK = 7
private const val MIN_MIDNIGHT_DELAY_MS = 1000L

/** Día de la semana que va en la columna [offset] (0-6) de la cabecera, empezando en [weekStart]. */
internal fun weekdayForColumn(
    weekStart: DayOfWeek,
    offset: Int,
): DayOfWeek = DayOfWeek.of(((weekStart.value - 1 + offset) % AGENDA_DAYS_IN_WEEK) + 1)

/**
 * Celdas de la grilla mensual: `null` para los huecos antes del día 1 y
 * después del último día, [LocalDate] para cada día del mes. La cantidad
 * siempre es múltiplo de 7.
 */
internal fun monthGridCells(
    month: YearMonth,
    weekStart: DayOfWeek,
): List<LocalDate?> {
    val leadingBlanks = calendarLeadingBlanks(month, weekStart)
    val daysInMonth = month.lengthOfMonth()
    val rows = (leadingBlanks + daysInMonth + AGENDA_DAYS_IN_WEEK - 1) / AGENDA_DAYS_IN_WEEK
    return List(rows * AGENDA_DAYS_IN_WEEK) { cellIndex ->
        val dayNumber = cellIndex - leadingBlanks + 1
        if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
    }
}

/** Días (en la zona dada) que tienen al menos un evento. */
internal fun eventDates(
    events: List<AgendaEventEntity>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Set<LocalDate> = events.map { agendaEventLocalDate(it.dateTimeMillis, zoneId) }.toSet()

/** Eventos de [date] ordenados por hora. */
internal fun eventsForDate(
    events: List<AgendaEventEntity>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AgendaEventEntity> =
    events.filter { agendaEventLocalDate(it.dateTimeMillis, zoneId) == date }
        .sortedBy { it.dateTimeMillis }

/** Milisegundos hasta la medianoche siguiente a [today], nunca menos de 1 s. */
internal fun millisUntilNextMidnight(
    now: LocalDateTime,
    today: LocalDate,
): Long =
    Duration.between(now, today.plusDays(1).atStartOfDay())
        .toMillis().coerceAtLeast(MIN_MIDNIGHT_DELAY_MS)

/** true si el recordatorio de un evento ya caería en el pasado (schedule() lo descartaría). */
internal fun isReminderTriggerPast(
    dateTimeMillis: Long,
    reminderMinutesBefore: Int?,
    nowMillis: Long,
): Boolean =
    reminderMinutesBefore != null &&
        reminderTriggerMillis(dateTimeMillis, reminderMinutesBefore) <= nowMillis

internal fun Long.toAgendaLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDateTime()

internal fun LocalDateTime.toAgendaEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long {
    return atZone(zoneId).toInstant().toEpochMilli()
}

/** Milisegundos UTC a medianoche del día de [value]: lo que espera DatePickerState como selección inicial. */
internal fun datePickerInitialMillis(value: LocalDateTime): Long =
    value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Combina la fecha elegida en el DatePicker (millis UTC) con la hora de [value]. */
internal fun mergePickedDate(
    value: LocalDateTime,
    pickedUtcMillis: Long,
): LocalDateTime {
    val newDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(newDate, value.toLocalTime())
}

/** Combina la hora elegida en el TimePicker con la fecha de [value]. */
internal fun mergePickedTime(
    value: LocalDateTime,
    hour: Int,
    minute: Int,
): LocalDateTime = value.withHour(hour).withMinute(minute)
