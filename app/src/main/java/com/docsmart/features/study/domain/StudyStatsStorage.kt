package com.docsmart.features.study.domain

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
            // Solo el tipo: CrashlyticsTree reenvía el Throwable completo a Firebase.
            Timber.e("Error cargando historial de pomodoros (${e.javaClass.simpleName})")
            emptyList()
        }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Cuántos pomodoros caen en cada día de la semana calendario actual
 * (índice 0 = primer día de la semana según la región, ver
 * `Calendar.firstDayOfWeek`), para dibujar una barra por día. Función pura --
 * recibe `now`, `timeZone` y `locale` en vez de leerlos del entorno para
 * poder testear semanas fijas sin depender del reloj ni de la región reales.
 *
 * Ronda 16: antes el índice del día salía de `(ts - inicioSemana) / 24h` y el
 * fin de la semana era `inicio + 7 * 24h`. En la semana de un cambio de hora
 * (días de 23 o 25 horas) eso ponía un pomodoro del viernes por la noche en
 * la barra del sábado, y descartaba de la semana la última hora del sábado.
 * Ahora se calculan los 8 límites de día reales con `Calendar.add(DAY)`.
 */
internal fun pomodoroCountsByWeekday(
    timestamps: List<Long>,
    now: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): IntArray {
    val calendar = Calendar.getInstance(timeZone, locale)
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    val boundaries =
        LongArray(DAYS_IN_WEEK + 1) {
            val boundary = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            boundary
        }

    val counts = IntArray(DAYS_IN_WEEK)
    timestamps.forEach { ts ->
        if (ts >= boundaries.first() && ts < boundaries.last()) {
            val dayIndex = (0 until DAYS_IN_WEEK).first { ts < boundaries[it + 1] }
            counts[dayIndex]++
        }
    }
    return counts
}

private const val DAYS_IN_WEEK = 7

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
 * pomodoros completados hoy). El fin del día se calcula con
 * `Calendar.add(DAY)` (no `+ 24h`) para respetar días de 23/25 horas.
 */
internal fun pomodoroCountToday(
    timestamps: List<Long>,
    now: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Int {
    val calendar = Calendar.getInstance(timeZone)
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    val endOfDay = calendar.timeInMillis
    return timestamps.count { it in startOfDay until endOfDay }
}

/** Milisegundos -> (horas, minutos), para mostrar "Xh Ymin" sin decimales. */
internal fun millisToHoursAndMinutes(millis: Long): Pair<Int, Int> {
    val totalMinutes = millis / 60_000
    return (totalMinutes / 60).toInt() to (totalMinutes % 60).toInt()
}
