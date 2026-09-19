package com.docsmart.features.agenda.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

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

// Ronda 16 (i18n): los formatos de fecha/hora de Agenda y de los
// recordatorios de Notas estaban fijos en español/24h -- "EEEE d 'de' MMMM"
// mostraba "Monday 15 de September" en inglés (y en los otros 10 idiomas), y
// "HH:mm"/"d MMM yyyy" ignoraban tanto el idioma como la preferencia de 12/24h
// del sistema. Se centralizan acá, como funciones puras que reciben el Locale,
// para poder testearlos sin Compose.

// Fecha completa localizada ("lunes, 15 de septiembre de 2026" / "Monday,
// September 15, 2026"), el orden y los conectores los pone el idioma.
fun agendaDayDetailFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
}

// `LLLL` (forma independiente) y no `MMMM`: en idiomas con declinación (ruso,
// catalán) `MMMM` da la forma de genitivo, incorrecta como título suelto.
fun agendaMonthYearFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

fun agendaDateFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
}

fun agendaTimeFormatter(
    locale: Locale,
    is24Hour: Boolean,
): DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

fun formatAgendaDateTime(
    millis: Long,
    locale: Locale,
    is24Hour: Boolean,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(zoneId)
    val date = dateTime.format(agendaDateFormatter(locale))
    val time = dateTime.format(agendaTimeFormatter(locale, is24Hour))
    return "$date · $time"
}

// Primer día de la semana según la región (domingo en EE.UU./Japón, lunes en
// casi toda Europa/Latinoamérica), en vez del lunes fijo de antes.
fun agendaWeekStart(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

// Celdas vacías antes del día 1 en la grilla mensual.
fun calendarLeadingBlanks(
    month: YearMonth,
    weekStart: DayOfWeek,
): Int = (month.atDay(1).dayOfWeek.value - weekStart.value + DAYS_PER_WEEK) % DAYS_PER_WEEK

private const val DAYS_PER_WEEK = 7
