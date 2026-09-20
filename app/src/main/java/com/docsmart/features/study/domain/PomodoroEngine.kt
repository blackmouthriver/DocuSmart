package com.docsmart.features.study.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.docsmart.core.analytics.DocuSmartAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PomodoroState(
    val minutes: Int = POMODORO_STUDY_MINUTES,
    val seconds: Int = 0,
    val isRunning: Boolean = false,
    val isBreak: Boolean = false,
    val pomodoroCount: Int = 0,
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
internal fun tickPomodoro(current: PomodoroState): PomodoroState =
    when {
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
                seconds = 0,
            )
        }
        else ->
            current.copy(
                isRunning = false,
                isBreak = false,
                minutes = POMODORO_STUDY_MINUTES,
                seconds = 0,
            )
    }

// true si este tick cierra un bloque de ESTUDIO (no de descanso) -- el
// momento exacto en el que cuenta como "un pomodoro completado" (RF-STU-09).
internal fun tickCompletesStudyBlock(current: PomodoroState): Boolean = current.seconds == 0 && current.minutes == 0 && !current.isBreak

// Hallazgo real de la auditoría general 2026-09-18 (Alta -- fin de sesión
// silencioso): análoga a tickCompletesStudyBlock pero para el cierre de un
// bloque de DESCANSO, usada para disparar completionEvents desde tick().
internal fun tickCompletesBreakBlock(current: PomodoroState): Boolean = current.seconds == 0 && current.minutes == 0 && current.isBreak

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

    // Hallazgo real de la auditoría general 2026-09-18 (Alta -- fin de
    // sesión silencioso): evento de un solo disparo para que
    // PomodoroTimerService pueda avisar (sonido/vibración) cuando un bloque
    // TERMINA, sin acoplar ese aviso al StateFlow de progreso (`_state`),
    // que ya se usa para actualizar la notificación en curso en cada tick.
    // El Boolean indica si el bloque que terminó era un descanso.
    //
    // Hallazgo real de la revisión adversarial de correctitud de este mismo
    // lote: con replay=0, un suscriptor que se registra DESPUÉS del emit
    // nunca lo ve. PomodoroTimerService recién se suscribe en su onCreate(),
    // lanzado de forma asíncrona vía startForegroundService() -- pausar y
    // reanudar con pocos segundos restantes puede completar el bloque antes
    // de que el servicio recién recreado termine de suscribirse, perdiendo
    // la alerta en silencio (justo lo que este fix quería resolver).
    // replay=1 + consumeCompletionEvent() (llamado por el único suscriptor
    // real tras procesar el evento) lo convierte en un buzón de una sola
    // casilla: si el suscriptor llega tarde, igual recibe el último evento
    // pendiente; una vez consumido, no se re-emite a suscriptores futuros.
    private val _completionEvents = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Boolean> = _completionEvents

    fun consumeCompletionEvent() {
        _completionEvents.resetReplayCache()
    }

    // Hallazgo real de la auditoría general 2026-09-18 (Alta -- Pomodoro sin
    // ancla de tiempo real): delay(1000) en el bucle de start() asume que
    // cada suspensión corresponde a exactamente 1 segundo real, pero el CPU
    // puede entrar en sleep con la pantalla apagada (no hay ningún
    // WAKE_LOCK en el proyecto) y "recuperar" de golpe al despertar,
    // desincronizando el cronómetro visible del tiempo real transcurrido.
    // Se ancla el bucle contra SystemClock.elapsedRealtime() (a diferencia
    // de uptimeMillis(), sigue avanzando durante el sleep del CPU, así que
    // refleja tiempo real transcurrido) y se hace catch-up llamando a
    // tick() tantas veces como segundos reales hayan pasado de verdad.
    private var lastTickElapsedRealtime: Long = 0L

    // Hallazgo #58 (revisión general 2026-09-16), afinado tras la revisión
    // de correctitud adversarial de este mismo lote: un chequeo basado en
    // el VALOR del estado (minutes==25 && seconds==0) no distingue "recién
    // empezado" de "pausado antes de que corriera el primer segundo" --
    // pausar y reanudar dentro de ese primer segundo volvía a loguear el
    // mismo bloque de estudio como si fuera nuevo. Se rastrea
    // explícitamente si ESTE bloque de estudio ya logueó su inicio, en vez
    // de inferirlo del valor exacto del cronómetro.
    private var studySessionLogged = false

    // Hallazgo real de la auditoría general 2026-09-17 (quinta pasada):
    // `pomodoroCount` vivía solo en este objeto en memoria, nunca se
    // inicializaba desde StudyStatsStorage (que sí persiste el historial
    // real) -- pausar y que el proceso muera (memoria baja o cierre
    // manual) reiniciaba el contador a 0 en la próxima apertura, así que
    // el "descanso largo cada 4 pomodoros" (ya prometido en
    // study_pomodoros_hint) no se disparaba en el momento correcto pese a
    // que el número mostrado en pantalla (pomodoroCountThisWeek) sí
    // reflejaba los pomodoros reales de hoy. Se siembra una sola vez por
    // proceso, en el primer start()/reset(), para no pisar un reset manual
    // del usuario ni resembrar en cada toggle().
    private var seededPomodoroCount = false

    fun toggle(context: Context) {
        if (_state.value.isRunning) pause() else start(context)
    }

    fun reset() {
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
        // Hallazgo real de la revisión adversarial de correctitud sobre
        // este mismo fix: marcar `seededPomodoroCount = true` acá sin
        // condición bloqueaba la siembra real si el usuario tocaba
        // "Reiniciar" ANTES que "Iniciar" alguna vez en este proceso (el
        // botón está siempre habilitado) -- el próximo start() ya no
        // sembraba desde StudyStatsStorage, reintroduciendo el bug que E3
        // corrige. No se toca la bandera acá: si ya estaba sembrada, sigue
        // sembrada (reset manual real, sin reabrir la siembra); si nunca
        // se sembró, el próximo start() lo hace desde datos reales.
        //
        // El servicio se detiene solo: su colector de `state` llama stopSelf()
        // apenas ve isRunning=false (ver PomodoroTimerService.onCreate).
        // Antes esto llamaba context.stopService() -- ver el comentario en pause().
    }

    private fun start(context: Context) {
        if (_state.value.isRunning) return
        if (!seededPomodoroCount) {
            seededPomodoroCount = true
            val completedToday =
                pomodoroCountToday(
                    StudyStatsStorage.loadStats(context).pomodoroTimestamps,
                    System.currentTimeMillis(),
                )
            if (completedToday > _state.value.pomodoroCount) {
                _state.value = _state.value.copy(pomodoroCount = completedToday)
            }
        }
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
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        tickerJob =
            scope.launch {
                while (_state.value.isRunning) {
                    delay(1000)
                    if (!_state.value.isRunning) break
                    val now = SystemClock.elapsedRealtime()
                    val elapsedSeconds = ((now - lastTickElapsedRealtime) / 1000L).toInt().coerceAtLeast(1)
                    lastTickElapsedRealtime += elapsedSeconds * 1000L
                    repeat(elapsedSeconds) {
                        if (_state.value.isRunning) tick(context)
                    }
                }
            }
    }

    // Hallazgo real (ronda 20, confirmado en un emulador): iniciar -> pausar ->
    // iniciar en menos de un segundo (doble toque) mataba la app con
    // ForegroundServiceDidNotStartInTimeException. start() lanza el servicio con
    // startForegroundService(); si pause() llamaba context.stopService() antes de
    // que el servicio alcanzara startForeground() ("Bringing down service while
    // still waiting for start foreground"), el siguiente start() dejaba un
    // ServiceRecord sin startForeground y Android tumbaba el proceso. Ahora el
    // motor NUNCA detiene el servicio: el propio servicio hace startForeground()
    // en onCreate() y su colector de `state` llama stopSelf() cuando isRunning
    // pasa a false, así que no hay carrera posible.
    private fun pause() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false)
    }

    // Hallazgo real de la auditoría general 2026-09-17 (M13): tick() corre en
    // el bucle de start() sobre Dispatchers.Default, mientras que
    // pause()/reset()/start() se llaman desde el hilo principal.
    // tickerJob.cancel() es cooperativo -- si un tick ya pasó su único punto
    // de suspensión (el delay(1000) de arriba) y está ejecutando este cuerpo
    // síncrono, cancelar el Job no lo detiene a mitad de camino. Antes, un
    // pause()/reset() concurrente en ese instante podía perderse: este tick
    // terminaba escribiendo `next` (derivado de un `current` ya obsoleto,
    // capturado ANTES del pause/reset) encima del isRunning=false recién
    // puesto, resucitando el cronómetro. compareAndSet solo aplica el
    // resultado si `_state` sigue siendo exactamente el `current` que este
    // tick leyó -- si cambió mientras tanto (pause/reset ganó la carrera),
    // este tick se descarta entero, sin duplicar tampoco sus side effects
    // (StudyStatsStorage/DocuSmartAnalytics).
    private fun tick(context: Context) {
        val current = _state.value
        if (!current.isRunning) return
        val next = tickPomodoro(current)
        if (!_state.compareAndSet(current, next)) return
        if (tickCompletesStudyBlock(current)) {
            StudyStatsStorage.recordPomodoroCompletion(context)
            DocuSmartAnalytics.logPomodoroCompleted(next.pomodoroCount)
            _completionEvents.tryEmit(false)
        } else if (tickCompletesBreakBlock(current)) {
            _completionEvents.tryEmit(true)
        }
        // El descanso terminó y arrancó un bloque de estudio nuevo (pausado,
        // esperando "Iniciar") -- ese bloque todavía no logueó su propio
        // inicio.
        if (current.isBreak && !next.isBreak) studySessionLogged = false
    }

    private fun startService(context: Context) {
        val intent = Intent(context, PomodoroTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
