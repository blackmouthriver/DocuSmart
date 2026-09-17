package com.docsmart.features.agenda.presentation

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.util.ReloadOnScreenResume
import com.docsmart.features.agenda.presentation.components.AgendaCalendarView
import com.docsmart.features.agenda.presentation.components.AgendaEventCard
import com.docsmart.features.agenda.presentation.components.AgendaEventEditorDialog
import com.docsmart.features.agenda.presentation.components.AgendaLinkDocumentDialog

// HU-65 (backlog UX 2026-09-16, feedback real de testers de la prueba
// cerrada): pantalla propia (no una pestaña más de Modo Estudio, decisión
// explícita del usuario) alcanzable desde una tarjeta de entrada dentro de
// la superficie de Modo Estudio -- ver StudyScreen.kt.
// Seguimiento (mismo backlog): banner de anuncios + banner azul con título
// (mismo criterio que el resto de las pantallas, ver DocuSmartScreenHeader/
// DocuSmartTopBanner) y una vista de calendario mensual con puntos en los
// días con eventos, alternable con la lista vía TabRow.
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    openEventId: String? = null,
    viewModel: AgendaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* No-op: la alarma se programa igual sin el permiso, solo no se ve
          la notificación cuando llegue la hora del recordatorio. */ }

    LaunchedEffect(openEventId) {
        openEventId?.let { viewModel.openEventFromNotification(it) }
    }

    // Sin este pedido, un usuario que entra a Agenda sin haber pasado nunca
    // por la pestaña Pomodoro de Modo Estudio (único lugar que ya lo pedía)
    // nunca ve el diálogo de permiso en Android 13+, y sus recordatorios se
    // programan pero jamás se muestran (AlarmManager.notify() no falla, solo
    // no hace nada sin POST_NOTIFICATIONS concedido).
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Bug real encontrado en prueba de dispositivo real 2026-09-17: el
    // permiso especial SCHEDULE_EXACT_ALARM (RNF explícito del usuario --
    // "que el recordatorio suene justo a la hora") puede estar denegado sin
    // que la app avise nunca -- ReminderScheduler ya degrada con elegancia
    // a una alarma inexacta (RF5, "funciona igual sin el permiso"), pero
    // eso significa que el recordatorio puede sonar varios minutos tarde
    // (confirmado en el Motorola Edge 30 Neo: ~4-5 min de retraso) sin que
    // el usuario tenga forma de enterarse de por qué ni de corregirlo. No
    // se pide con un diálogo del sistema (a diferencia de POST_NOTIFICATIONS)
    // porque este permiso especial solo se concede desde una pantalla propia
    // de Ajustes -- se refresca al volver de esa pantalla vía
    // ReloadOnScreenResume, mismo patrón que LibraryScreen para permisos.
    var canScheduleExactAlarms by remember { mutableStateOf(canScheduleExactAlarmsNow(context)) }
    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Alta): Android cancela automáticamente TODAS las alarmas exactas ya
    // programadas al revocar este permiso -- antes solo se refrescaba el
    // booleano para mostrar/ocultar el banner, sin reprogramar los
    // recordatorios ya afectados. Al detectar la transición de
    // concedido→revocado se reprograman todos (ReminderScheduler ya
    // degrada con elegancia a alarma inexacta).
    ReloadOnScreenResume {
        val nowGranted = canScheduleExactAlarmsNow(context)
        if (canScheduleExactAlarms && !nowGranted) viewModel.rescheduleAllReminders()
        canScheduleExactAlarms = nowGranted
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startCreating() }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.agenda_new_event))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Reutiliza el ID de anuncio de Modo Estudio -- Agenda todavía
                // no tiene un placement propio creado en la consola de AdMob
                // (no se puede generar uno desde acá); si el usuario quiere
                // métricas separadas por pantalla, hay que crear el ad unit en
                // apps.admob.com y actualizar AdConstants.BANNER_AGENDA_ID.
                // Sin padding horizontal propio: DocuSmartScreenHeader ya
                // aplica el margen de 16dp estándar de todas las pantallas.
                DocuSmartScreenHeader(
                    adUnitId = AdConstants.BANNER_STUDY_ID,
                    adManager = viewModel.adManager
                ) {
                    DocuSmartTopBanner(
                        screenTitle = stringResource(R.string.agenda_title),
                        screenSubtitle = stringResource(R.string.agenda_subtitle),
                        onBack = onBack
                    )
                }
            }

            if (!canScheduleExactAlarms) {
                item {
                    ExactAlarmPermissionBanner(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onEnableClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                }
            }

            item {
                val tabs = listOf(
                    stringResource(R.string.agenda_view_list) to AgendaViewMode.LIST,
                    stringResource(R.string.agenda_view_calendar) to AgendaViewMode.CALENDAR
                )
                TabRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    selectedTabIndex = tabs.indexOfFirst { it.second == uiState.viewMode },
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEach { (label, mode) ->
                        Tab(
                            selected = uiState.viewMode == mode,
                            onClick = { viewModel.setViewMode(mode) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (uiState.viewMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            when (uiState.viewMode) {
                AgendaViewMode.LIST -> agendaListContent(uiState = uiState, viewModel = viewModel)
                AgendaViewMode.CALENDAR -> item {
                    AgendaCalendarView(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        month = uiState.calendarMonth,
                        selectedDate = uiState.selectedDate,
                        events = uiState.events,
                        onPreviousMonth = { viewModel.goToPreviousMonth() },
                        onNextMonth = { viewModel.goToNextMonth() },
                        onSelectDate = { viewModel.selectDate(it) },
                        onEventClick = { viewModel.startEditing(it) }
                    )
                }
            }
        }
    }

    uiState.draft?.let { draft ->
        AgendaEventEditorDialog(
            draft = draft,
            onTitleChange = { viewModel.updateDraft { d -> d.copy(title = it) } },
            onDescriptionChange = { viewModel.updateDraft { d -> d.copy(description = it) } },
            onDateTimeChange = { viewModel.updateDraft { d -> d.copy(dateTimeMillis = it) } },
            onReminderChange = { viewModel.updateDraft { d -> d.copy(reminderMinutesBefore = it) } },
            onLinkDocumentClick = { viewModel.showLinkDocumentDialog() },
            onUnlinkDocument = { viewModel.unlinkDocument() },
            onSave = { viewModel.saveDraft() },
            onDelete = { draft.id?.let { viewModel.deleteEvent(it) } },
            onDismiss = { viewModel.dismissEditor() }
        )

        if (uiState.showLinkDocumentDialog) {
            AgendaLinkDocumentDialog(
                currentDocumentId = draft.documentId,
                onDismiss = { viewModel.dismissLinkDocumentDialog() },
                onSelect = { viewModel.linkDocument(it) },
                onUnlink = { viewModel.unlinkDocument() }
            )
        }
    }
}

private fun canScheduleExactAlarmsNow(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(AlarmManager::class.java)
    return manager?.canScheduleExactAlarms() ?: true
}

@Composable
private fun ExactAlarmPermissionBanner(onEnableClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AlarmOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.agenda_exact_alarm_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.agenda_exact_alarm_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = onEnableClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.agenda_exact_alarm_banner_action))
                }
            }
        }
    }
}

private fun LazyListScope.agendaListContent(
    uiState: AgendaUiState,
    viewModel: AgendaViewModel
) {
    if (uiState.events.isEmpty()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(R.string.agenda_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    } else {
        items(uiState.events, key = { it.id }) { event ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                AgendaEventCard(event = event, onClick = { viewModel.startEditing(event) })
            }
        }
    }
}
