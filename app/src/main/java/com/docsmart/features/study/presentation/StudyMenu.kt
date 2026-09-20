@file:Suppress("MatchingDeclarationName")

package com.docsmart.features.study.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow

/** Pantalla interna de Modo Estudio que se está mostrando. */
internal enum class StudyView { MENU, READING, NOTES, POMODORO }

/** Índice de pestaña interno → vista. `-1` es el menú; un valor fuera de rango cae al menú. */
internal fun studyViewForTab(tab: Int): StudyView =
    when (tab) {
        0 -> StudyView.READING
        1 -> StudyView.NOTES
        2 -> StudyView.POMODORO
        else -> StudyView.MENU
    }

/** Índice inicial al abrir Modo Estudio: una nota concreta fuerza Notas; si no, el pedido (o el menú). */
internal fun initialStudyTab(
    openNoteId: String?,
    requestedTab: Int,
): Int = if (openNoteId != null) STUDY_TAB_NOTES else requestedTab.coerceIn(STUDY_TAB_MENU, STUDY_TAB_POMODORO)

internal const val STUDY_TAB_MENU = -1
internal const val STUDY_TAB_READING = 0
internal const val STUDY_TAB_NOTES = 1
internal const val STUDY_TAB_POMODORO = 2

/**
 * Menú de entrada a Modo Estudio: en vez de pestañas y tres íconos sueltos, una tarjeta
 * grande por cada función (Lectura, Notas, Pomodoro y Agenda y calendario).
 */
@Composable
internal fun StudyMenu(
    onReading: () -> Unit,
    onNotes: () -> Unit,
    onPomodoro: () -> Unit,
    onAgenda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.study_menu_prompt),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StudyMenuCard(
            icon = Icons.Rounded.MenuBook,
            title = stringResource(R.string.study_tab_reading),
            description = stringResource(R.string.study_menu_reading_desc),
            color = SuccessGreen,
            onClick = onReading,
        )
        StudyMenuCard(
            icon = Icons.Rounded.EditNote,
            title = stringResource(R.string.study_tab_notes),
            description = stringResource(R.string.study_menu_notes_desc),
            color = MaterialTheme.colorScheme.primary,
            onClick = onNotes,
        )
        StudyMenuCard(
            icon = Icons.Rounded.Timer,
            title = stringResource(R.string.study_tab_pomodoro),
            description = stringResource(R.string.study_menu_pomodoro_desc),
            color = WarningAmber,
            onClick = onPomodoro,
        )
        StudyMenuCard(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.agenda_title),
            description = stringResource(R.string.study_menu_agenda_desc),
            color = MaterialTheme.colorScheme.tertiary,
            onClick = onAgenda,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StudyMenuCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
