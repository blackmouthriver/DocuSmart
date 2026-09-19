package com.docsmart.features.agenda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.features.agenda.domain.AgendaEventStatus
import com.docsmart.features.agenda.domain.classifyAgendaEvent
import com.docsmart.features.agenda.domain.formatAgendaDateTime
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Extraída de AgendaScreen.kt (backlog UX 2026-09-16, seguimiento de HU-65)
// para reutilizarla también en el detalle del día seleccionado de
// AgendaCalendarView -- antes era privada y solo la usaba la vista de lista.
// Ronda 16: la fecha/hora ya no usa un patrón fijo "d MMM yyyy · HH:mm" (24h,
// idioma capturado una sola vez al cargar la clase), ver formatAgendaDateTime.
@Composable
fun AgendaEventCard(
    event: AgendaEventEntity,
    onClick: () -> Unit,
) {
    // Hallazgo real de la auditoría de Agenda 2026-09-18 (Media): antes
    // `classifyAgendaEvent()` se evaluaba una sola vez en la composición
    // inicial (System.currentTimeMillis() no es observable por Compose) --
    // una tarjeta dejada en pantalla cruzando la medianoche seguía mostrando
    // el estado viejo (ej. "HOY" en vez de "VENCIDO") hasta la próxima
    // recomposición externa. Mismo patrón de LaunchedEffect con
    // reprogramación hasta la medianoche siguiente que ya usa MonthGrid en
    // AgendaCalendarView.kt para el mismo problema con el círculo de "hoy".
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val delayMs =
                Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
                    .toMillis().coerceAtLeast(1000L)
            delay(delayMs)
            nowMillis = System.currentTimeMillis()
        }
    }
    val status = classifyAgendaEvent(event.dateTimeMillis, nowMillis = nowMillis)
    val accentColor =
        when (status) {
            AgendaEventStatus.OVERDUE -> WarningAmber
            AgendaEventStatus.TODAY -> SuccessGreen
            AgendaEventStatus.UPCOMING -> MaterialTheme.colorScheme.primary
        }
    val statusLabel =
        when (status) {
            AgendaEventStatus.OVERDUE -> stringResource(R.string.agenda_status_overdue)
            AgendaEventStatus.TODAY -> stringResource(R.string.agenda_status_today)
            AgendaEventStatus.UPCOMING -> stringResource(R.string.agenda_status_upcoming)
        }
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val dateTimeLabel =
        remember(event.dateTimeMillis, locale, is24Hour) {
            formatAgendaDateTime(event.dateTimeMillis, locale, is24Hour)
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                // Hallazgo real de la auditoría de Agenda 2026-09-18 (Media):
                // sin `role`, TalkBack no anunciaba la tarjeta como botón.
                .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateTimeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (event.documentId != null) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = stringResource(R.string.agenda_document_linked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
