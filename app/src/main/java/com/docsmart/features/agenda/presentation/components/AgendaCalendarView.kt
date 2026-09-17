package com.docsmart.features.agenda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.domain.agendaEventLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Backlog UX 2026-09-16 (seguimiento de HU-65), pedido explícito del
// usuario: vista de calendario mensual (no semanal/por horas tipo Teams
// real -- se descartó a propósito por ser mucho más compleja de operar en
// una pantalla de celular) con un punto en los días que tienen eventos, y
// el detalle del día elegido debajo del grid.
private val WEEK_START = DayOfWeek.MONDAY

@Composable
fun AgendaCalendarView(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<AgendaEventEntity>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEventClick: (AgendaEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Lint real (NonObservableLocale): Locale.getDefault() no es observable
    // por Compose -- si el usuario cambia el idioma del sistema sin recrear
    // la Activity, el mes/día no se recompondrían en el idioma correcto.
    // LocalConfiguration.current sí lo es (mismo fix ya usado en
    // PremiumScreen.kt/SettingsScreen.kt).
    val locale = LocalConfiguration.current.locales[0]
    val dayDetailFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale) }
    val eventDatesInMonth = remember(events, month) {
        events.map { agendaEventLocalDate(it.dateTimeMillis) }.toSet()
    }
    val eventsForSelectedDay = remember(events, selectedDate) {
        events.filter { agendaEventLocalDate(it.dateTimeMillis) == selectedDate }
            .sortedBy { it.dateTimeMillis }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(
            month = month,
            locale = locale,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )
        Spacer(Modifier.height(12.dp))
        WeekdayHeaderRow(locale = locale)
        Spacer(Modifier.height(4.dp))
        MonthGrid(
            month = month,
            selectedDate = selectedDate,
            eventDatesInMonth = eventDatesInMonth,
            onSelectDate = onSelectDate
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = selectedDate.format(dayDetailFormat)
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (eventsForSelectedDay.isEmpty()) {
            Text(
                text = stringResource(R.string.agenda_calendar_no_events_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onNextMonth: () -> Unit
) {
    val monthYearFormat = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.agenda_calendar_previous_month)
            )
        }
        Text(
            text = month.atDay(1).format(monthYearFormat).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.agenda_calendar_next_month)
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow(locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (offset in 0 until DAYS_IN_WEEK) {
            val dayOfWeek = DayOfWeek.of(((WEEK_START.value - 1 + offset) % DAYS_IN_WEEK) + 1)
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    eventDatesInMonth: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstDayOfMonth = month.atDay(1)
    val leadingBlanks = (firstDayOfMonth.dayOfWeek.value - WEEK_START.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    val today = remember { LocalDate.now() }

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until DAYS_IN_WEEK) {
                    val cellIndex = row * DAYS_IN_WEEK + column
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        CalendarDayCell(
                            day = dayNumber,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            hasEvents = date in eventDatesInMonth,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (hasEvents) {
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                )
        )
    }
}

private const val DAYS_IN_WEEK = 7
