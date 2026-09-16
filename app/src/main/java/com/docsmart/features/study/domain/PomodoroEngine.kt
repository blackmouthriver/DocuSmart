package com.docsmart.features.study.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import com.docsmart.core.analytics.DocuSmartAnalytics
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
// Hallazgo #56 (revisión general 2026-09-16): study_pomodoros_hint ya
// prometía "Cada 4 pomodoros = descanso largo" desde el texto, pero esa
// lógica nunca existió -- el descanso era siempre de 5 minutos. Valores de
// la técnica Pomodoro clásica (Cirillo).
internal const val POMODORO_LONG_BREAK_MINUTES = 15
internal const val POMODORO_LONG_BREAK_INTERVAL = 4

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
    !current.isBreak -> {
        val newCount = current.pomodoroCount + 1
        val isLongBreak = newCount % POMODORO_LONG_BREAK_INTERVAL == 0
        current.copy(
            isRunning = false,
            pomodoroCount = newCount,
            isBreak = true,
            minutes = if (isLongBreak) POMODORO_LONG_BREAK_MINUTES else POMODORO_BREAK_MINUTES,
            seconds = 0
        )
    }
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

    // Hallazgo #58 (revisión general 2026-09-16), afinado tras la revisión
    // de correctitud adversarial de este mismo lote: un chequeo basado en
    // el VALOR del estado (minutes==25 && seconds==0) no distingue "recién
    // empezado" de "pausado antes de que corriera el primer segundo" --
    // pausar y reanudar dentro de ese primer segundo volvía a loguear el
    // mismo bloque de estudio como si fuera nuevo. Se rastrea
    // explícitamente si ESTE bloque de estudio ya logueó su inicio, en vez
    // de inferirlo del valor exacto del cronómetro.
    private var studySessionLogged = false

    fun toggle(context: Context) {
        if (_state.value.isRunning) pause(context) else start(context)
    }

    fun reset(context: Context) {
        // Bug real encontrado 2026-09-14 (repaso general, confirmado en
        // vivo: iniciar→pausar→reanudar rápido corría el cronómetro al
        // doble de velocidad): reset()/pause() solo cambiaban isRunning,
        // nunca cancelaban tickerJob. El bucle viejo sigue vivo esperando
        // en delay(1000) y, si start() se llama de nuevo antes de que ese
        // delay termine, el bucle viejo ve isRunning=true otra vez (puesto
        // por el nuevo start()) y sigue tickeando en paralelo al nuevo --
        // dos corrutinas decrementando el mismo StateFlow, duplicando
        // también recordPomodoroCompletion()/logPomodoroCompleted().
        tickerJob?.cancel()
        tickerJob = null
        _state.value = PomodoroState()
        studySessionLogged = false
        stopService(context)
    }

    private fun start(context: Context) {
        if (_state.value.isRunning) return
        // Hallazgo #58 (revisión general 2026-09-16): antes se disparaba en
        // cada reanudación (pausar → reanudar un bloque de estudio ya
        // empezado también entra por acá), no solo al iniciar sesión --
        // solo cuenta como "inicio" la primera vez que arranca ESTE bloque
        // de estudio, sin importar cuántas veces se pause/reanude después.
        if (!_state.value.isBreak && !studySessionLogged) {
            DocuSmartAnalytics.logStudySessionStarted()
            studySessionLogged = true
        }
        _state.value = _state.value.copy(isRunning = true)
        startService(context)
        // Cancela cualquier bucle viejo antes de lanzar uno nuevo -- ver
        // el comentario en reset()/pause() sobre la condición de carrera.
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (_state.value.isRunning) {
                delay(1000)
                if (!_state.value.isRunning) break
                tick(context)
            }
        }
    }

    private fun pause(context: Context) {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false)
        stopService(context)
    }

    private fun tick(context: Context) {
        val current = _state.value
        val next = tickPomodoro(current)
        if (tickCompletesStudyBlock(current)) {
            StudyStatsStorage.recordPomodoroCompletion(context)
            DocuSmartAnalytics.logPomodoroCompleted(next.pomodoroCount)
        }
        // El descanso terminó y arrancó un bloque de estudio nuevo (pausado,
        // esperando "Iniciar") -- ese bloque todavía no logueó su propio
        // inicio.
        if (current.isBreak && !next.isBreak) studySessionLogged = false
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
