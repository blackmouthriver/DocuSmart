package com.docsmart.features.agenda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.agendaDayDetailFormatter
import com.docsmart.features.agenda.domain.agendaMonthYearFormatter
import com.docsmart.features.agenda.domain.agendaWeekStart
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Backlog UX 2026-09-16 (seguimiento de HU-65), pedido explícito del
// usuario: vista de calendario mensual (no semanal/por horas tipo Teams
// real -- se descartó a propósito por ser mucho más compleja de operar en
// una pantalla de celular) con un punto en los días que tienen eventos, y
// el detalle del día elegido debajo del grid.
// Ronda 16: el primer día de la semana ya no está fijo en lunes, sale de la
// región del usuario (agendaWeekStart).

@Composable
fun AgendaCalendarView(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<AgendaEventEntity>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEventClick: (AgendaEventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lint real (NonObservableLocale): Locale.getDefault() no es observable
    // por Compose -- si el usuario cambia el idioma del sistema sin recrear
    // la Activity, el mes/día no se recompondrían en el idioma correcto.
    // LocalConfiguration.current sí lo es (mismo fix ya usado en
    // PremiumScreen.kt/SettingsScreen.kt).
    val locale = LocalConfiguration.current.locales[0]
    val dayDetailFormat = remember(locale) { agendaDayDetailFormatter(locale) }
    val weekStart = remember(locale) { agendaWeekStart(locale) }
    val eventDatesInMonth =
        remember(events, month) {
            eventDates(events)
        }
    val eventsForSelectedDay =
        remember(events, selectedDate) {
            eventsForDate(events, selectedDate)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(
            month = month,
            locale = locale,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )
        Spacer(Modifier.height(12.dp))
        WeekdayHeaderRow(locale = locale, weekStart = weekStart)
        Spacer(Modifier.height(4.dp))
        MonthGrid(
            month = month,
            weekStart = weekStart,
            selectedDate = selectedDate,
            eventDatesInMonth = eventDatesInMonth,
            dayDetailFormat = dayDetailFormat,
            onSelectDate = onSelectDate,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text =
                selectedDate.format(dayDetailFormat)
                    .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (eventsForSelectedDay.isEmpty()) {
            Text(
                text = stringResource(R.string.agenda_calendar_no_events_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                eventsForSelectedDay.forEach { event ->
                    AgendaEventCard(event = event, onClick = { onEventClick(event) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    locale: Locale,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val monthYearFormat = remember(locale) { agendaMonthYearFormatter(locale) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.agenda_calendar_previous_month),
            )
        }
        Text(
            text = month.atDay(1).format(monthYearFormat).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.agenda_calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow(
    locale: Locale,
    weekStart: DayOfWeek,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (offset in 0 until DAYS_IN_WEEK) {
            val dayOfWeek = weekdayForColumn(weekStart, offset)
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    weekStart: DayOfWeek,
    selectedDate: LocalDate,
    eventDatesInMonth: Set<LocalDate>,
    dayDetailFormat: DateTimeFormatter,
    onSelectDate: (LocalDate) -> Unit,
) {
    val cells = monthGridCells(month, weekStart)
    val rows = cells.size / DAYS_IN_WEEK
    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Baja-Media -- A4): `remember { LocalDate.now() }` nunca se
    // recalculaba mientras el composable siguiera vivo -- si el usuario
    // dejaba la pestaña Calendario abierta cruzando la medianoche, el
    // círculo de "hoy" seguía marcando el día anterior. Se reprograma
    // solo hasta la medianoche siguiente en vez de sondear cada minuto.
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Hallazgo real de la revisión adversarial de correctitud sobre este
    // mismo fix: `LaunchedEffect(today)` solo se relanza cuando la CLAVE
    // cambia de valor -- si el usuario atrasa el reloj del sistema
    // mientras la pantalla está abierta, `LocalDate.now()` al despertar
    // podía devolver la MISMA fecha que ya estaba vigente, la asignación
    // no producía un cambio observable (LocalDate usa equals()) y el
    // mecanismo de auto-actualización se detenía para siempre. Un
    // `while(true)` dentro de un único `LaunchedEffect(Unit)` no depende
    // de que el valor cambie para volver a programarse.
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMidnight(LocalDateTime.now(), today))
            today = LocalDate.now()
        }
    }

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until DAYS_IN_WEEK) {
                    val date = cells[row * DAYS_IN_WEEK + column]
                    if (date != null) {
                        CalendarDayCell(
                            day = date.dayOfMonth,
                            dateLabel = date.format(dayDetailFormat),
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            hasEvents = date in eventDatesInMonth,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Hallazgo real de la auditoría general 2026-09-17 (B23): celda de día sin
// contentDescription -- TalkBack solo leía el número suelto ("15"), sin mes
// ni año ni indicar si es hoy/está seleccionado/tiene eventos.
//
// Hallazgo real de la auditoría de Agenda 2026-09-18 (Media): además de lo
// anterior, la celda usaba `.semantics{}.clickable()` puro -- sin `role` ni
// `selected`, TalkBack no anunciaba ni el rol de botón ni el estado
// seleccionado (`accessibleLabel` tampoco lo incluía). `.selectable(selected
// = isSelected, ...)` agrega ambas cosas automáticamente al árbol de
// accesibilidad (mismo patrón ya usado en LibraryTabItem/DocuSmartBottomBar),
// así que no hace falta duplicar "seleccionado" en el string. También se
// agrega `sizeIn` para garantizar el objetivo táctil mínimo de 48dp incluso
// en una grilla de 7 columnas angosta.
@Composable
private fun CalendarDayCell(
    day: Int,
    dateLabel: String,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> Color.Transparent
        }
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val accessibleLabel =
        buildString {
            append(dateLabel)
            if (isToday) append(stringResource(R.string.agenda_calendar_day_today))
            if (hasEvents) append(stringResource(R.string.agenda_calendar_day_has_events))
        }

    Column(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .selectable(selected = isSelected, onClick = onClick, role = Role.Button)
                .semantics(mergeDescendants = true) { contentDescription = accessibleLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasEvents) {
                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ),
        )
    }
}

private const val DAYS_IN_WEEK = AGENDA_DAYS_IN_WEEK
