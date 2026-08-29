package com.docsmart.features.study.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PomodoroState(
    val minutes: Int = POMODORO_STUDY_MINUTES,
    val seconds: Int = 0,
    val isRunning: Boolean = false,
    val isBreak: Boolean = false,
    val pomodoroCount: Int = 0
)

internal const val POMODORO_STUDY_MINUTES = 25
internal const val POMODORO_BREAK_MINUTES = 5

/**
 * Un tick del Pomodoro, sin ningún efecto secundario (sin `Context`, sin
 * tocar `StudyStatsStorage`) -- extraído de [PomodoroEngine] puramente para
 * poder testearlo con estados fijos, mismo patrón ya usado en el proyecto
 * para `isTrashEntryExpired`/`mergeHistoryWithDocuments`. Mismo
 * comportamiento que la versión anterior basada en `LaunchedEffect`: al
 * completarse un bloque de estudio o descanso, el resultado ya trae
 * `isRunning = false` -- el llamador debe volver a iniciar el siguiente
 * bloque explícitamente, no se encadena solo.
 */
internal fun tickPomodoro(current: PomodoroState): PomodoroState = when {
    current.seconds > 0 -> current.copy(seconds = current.seconds - 1)
    current.minutes > 0 -> current.copy(minutes = current.minutes - 1, seconds = 59)
    !current.isBreak -> current.copy(
        isRunning = false,
        pomodoroCount = current.pomodoroCount + 1,
        isBreak = true,
        minutes = POMODORO_BREAK_MINUTES,
        seconds = 0
    )
    else -> current.copy(
        isRunning = false,
        isBreak = false,
        minutes = POMODORO_STUDY_MINUTES,
        seconds = 0
    )
}

// true si este tick cierra un bloque de ESTUDIO (no de descanso) -- el
// momento exacto en el que cuenta como "un pomodoro completado" (RF-STU-09).
internal fun tickCompletesStudyBlock(current: PomodoroState): Boolean =
    current.seconds == 0 && current.minutes == 0 && !current.isBreak

/**
 * RF-STU-10: motor del Pomodoro, vivo fuera de la composición de
 * `StudyScreen`. Antes el conteo era un `LaunchedEffect(isRunning)` dentro
 * del propio Composable -- salir de Modo Estudio destruía esa composición y
 * cancelaba el conteo en seco, sin importar si el usuario solo quería ver
 * otra pantalla un momento. Al vivir en un objeto singleton con su propio
 * `CoroutineScope` (dura mientras el proceso esté vivo, no atado a ninguna
 * pantalla), el conteo sigue corriendo al navegar a otras pantallas.
 * `PomodoroTimerService` además lo mantiene vivo con una notificación en
 * primer plano si la app pasa completamente a segundo plano.
 *
 * Mismo comportamiento de fin de ciclo que la versión anterior (no es un
 * cambio de conducta, solo de dónde vive el estado): al completarse un
 * bloque de estudio o descanso, el timer se detiene solo (`isRunning =
 * false`) en vez de encadenar automáticamente el siguiente bloque -- el
 * usuario debe tocar "Iniciar" de nuevo.
 */
object PomodoroEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state

    fun toggle(context: Context) {
        if (_state.value.isRunning) pause(context) else start(context)
    }

    fun reset(context: Context) {
        _state.value = PomodoroState()
        stopService(context)
    }

    private fun start(context: Context) {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(isRunning = true)
        startService(context)
        tickerJob = scope.launch {
            while (_state.value.isRunning) {
                delay(1000)
                if (!_state.value.isRunning) break
                tick(context)
            }
        }
    }

    private fun pause(context: Context) {
        _state.value = _state.value.copy(isRunning = false)
        stopService(context)
    }

    private fun tick(context: Context) {
        val current = _state.value
        if (tickCompletesStudyBlock(current)) {
            StudyStatsStorage.recordPomodoroCompletion(context)
        }
        val next = tickPomodoro(current)
        _state.value = next
        if (!next.isRunning) stopService(context)
    }

    private fun startService(context: Context) {
        val intent = Intent(context, PomodoroTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, PomodoroTimerService::class.java))
    }
}
