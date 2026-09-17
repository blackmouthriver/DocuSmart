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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.features.agenda.domain.AgendaEventStatus
import com.docsmart.features.agenda.domain.classifyAgendaEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Extraída de AgendaScreen.kt (backlog UX 2026-09-16, seguimiento de HU-65)
// para reutilizarla también en el detalle del día seleccionado de
// AgendaCalendarView -- antes era privada y solo la usaba la vista de lista.
private val AGENDA_CARD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

@Composable
fun AgendaEventCard(event: AgendaEventEntity, onClick: () -> Unit) {
    val status = classifyAgendaEvent(event.dateTimeMillis)
    val accentColor = when (status) {
        AgendaEventStatus.OVERDUE -> WarningAmber
        AgendaEventStatus.TODAY -> SuccessGreen
        AgendaEventStatus.UPCOMING -> MaterialTheme.colorScheme.primary
    }
    val statusLabel = when (status) {
        AgendaEventStatus.OVERDUE -> stringResource(R.string.agenda_status_overdue)
        AgendaEventStatus.TODAY -> stringResource(R.string.agenda_status_today)
        AgendaEventStatus.UPCOMING -> stringResource(R.string.agenda_status_upcoming)
    }
    val dateTime = remember(event.dateTimeMillis) {
        Instant.ofEpochMilli(event.dateTimeMillis).atZone(ZoneId.systemDefault())
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateTime.format(AGENDA_CARD_DATE_FORMAT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.documentId != null) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = stringResource(R.string.agenda_document_linked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
