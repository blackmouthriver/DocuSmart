package com.docsmart.features.study.domain

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import timber.log.Timber
import java.util.Calendar

data class StudyStats(
    val totalReadingMillis: Long,
    val pomodoroTimestamps: List<Long>,
)

/**
 * RF-STU-09: estadísticas de estudio (tiempo total leído en voz alta,
 * pomodoros completados por semana). Mismo patrón de persistencia que
 * `StudyNotesStorage` (SharedPreferences + JSON real, sin Room) -- este
 * módulo no tiene ViewModel (ver RNF-STU-03) y el volumen de datos es
 * mínimo, no justifica una tabla relacional nueva.
 */
object StudyStatsStorage {
    private const val PREFS_NAME = "study_stats"
    private const val KEY_TOTAL_READING_MILLIS = "total_reading_millis"
    private const val KEY_POMODORO_TIMESTAMPS = "pomodoro_timestamps"

    // Timestamps más viejos que esto se descartan al registrar uno nuevo --
    // solo importan para "esta semana"/"últimos días", no tiene sentido
    // acumular para siempre un historial que nadie consulta.
    internal const val POMODORO_HISTORY_RETENTION_DAYS = 90L

    fun addReadingTime(
        context: Context,
        millis: Long,
    ) {
        if (millis <= 0) return
        val prefs = prefs(context)
        val current = prefs.getLong(KEY_TOTAL_READING_MILLIS, 0L)
        prefs.edit().putLong(KEY_TOTAL_READING_MILLIS, current + millis).apply()
    }

    fun recordPomodoroCompletion(
        context: Context,
        at: Long = System.currentTimeMillis(),
    ) {
        val prefs = prefs(context)
        val updated = trimOldTimestamps(loadPomodoroTimestamps(prefs) + at, at)
        prefs.edit().putString(KEY_POMODORO_TIMESTAMPS, JSONArray(updated).toString()).apply()
    }

    fun loadStats(context: Context): StudyStats {
        val prefs = prefs(context)
        return StudyStats(
            totalReadingMillis = prefs.getLong(KEY_TOTAL_READING_MILLIS, 0L),
            pomodoroTimestamps = loadPomodoroTimestamps(prefs),
        )
    }

    internal fun trimOldTimestamps(
        timestamps: List<Long>,
        now: Long,
    ): List<Long> {
        val cutoff = now - POMODORO_HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        return timestamps.filter { it >= cutoff }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadPomodoroTimestamps(prefs: SharedPreferences): List<Long> =
        try {
            val json = prefs.getString(KEY_POMODORO_TIMESTAMPS, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { array.getLong(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error cargando historial de pomodoros")
            emptyList()
        }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Cuántos pomodoros caen en cada día de la semana calendario actual
 * (domingo=índice 0 .. sábado=índice 6, orden de `Calendar.DAY_OF_WEEK`),
 * para dibujar una barra por día. Función pura -- recibe `now` en vez de
 * usar `System.currentTimeMillis()` internamente para poder testear semanas
 * fijas sin depender del reloj real.
 */
internal fun pomodoroCountsByWeekday(
    timestamps: List<Long>,
    now: Long,
): IntArray {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    val startOfWeek = calendar.timeInMillis
    val endOfWeek = startOfWeek + 7L * 24 * 60 * 60 * 1000

    val counts = IntArray(7)
    timestamps.forEach { ts ->
        if (ts in startOfWeek until endOfWeek) {
            val dayIndex = ((ts - startOfWeek) / (24L * 60 * 60 * 1000)).toInt().coerceIn(0, 6)
            counts[dayIndex]++
        }
    }
    return counts
}

/** Total de pomodoros dentro de la semana calendario actual. */
internal fun pomodoroCountThisWeek(
    timestamps: List<Long>,
    now: Long,
): Int = pomodoroCountsByWeekday(timestamps, now).sum()

/**
 * Total de pomodoros completados en el día calendario de `now` -- usado
 * para reconstruir el contador de "descanso largo cada 4 pomodoros" de
 * [com.docsmart.features.study.domain.PomodoroEngine] al arrancar un
 * proceso nuevo (hallazgo real de la auditoría general 2026-09-17, quinta
 * pasada: ese contador vivía solo en memoria y se reiniciaba a 0 si el
 * proceso moría entre bloques, aunque el historial real ya llevara varios
 * pomodoros completados hoy).
 */
internal fun pomodoroCountToday(
    timestamps: List<Long>,
    now: Long,
): Int {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis
    val endOfDay = startOfDay + 24L * 60 * 60 * 1000
    return timestamps.count { it in startOfDay until endOfDay }
}

/** Milisegundos -> (horas, minutos), para mostrar "Xh Ymin" sin decimales. */
internal fun millisToHoursAndMinutes(millis: Long): Pair<Int, Int> {
    val totalMinutes = millis / 60_000
    return (totalMinutes / 60).toInt() to (totalMinutes % 60).toInt()
}
