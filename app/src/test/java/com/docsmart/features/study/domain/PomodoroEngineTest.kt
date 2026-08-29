package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RF-STU-10: cubre la lógica pura de un tick del Pomodoro (sin Context, sin
 * tocar StudyStatsStorage ni el servicio en segundo plano) -- ver
 * docs/requirements/study.md §16 para el diseño completo del motor.
 */
class PomodoroEngineTest {

    @Test
    fun `un tick normal solo descuenta un segundo`() {
        val current = PomodoroState(minutes = 10, seconds = 30, isRunning = true)

        val next = tickPomodoro(current)

        assertEquals(10, next.minutes)
        assertEquals(29, next.seconds)
        assertTrue(next.isRunning)
    }

    @Test
    fun `al llegar a 0 segundos con minutos restantes, pasa al minuto siguiente con 59 segundos`() {
        val current = PomodoroState(minutes = 5, seconds = 0, isRunning = true)

        val next = tickPomodoro(current)

        assertEquals(4, next.minutes)
        assertEquals(59, next.seconds)
    }

    @Test
    fun `al terminar un bloque de estudio, cuenta el pomodoro y pasa a descanso pausado`() {
        val current = PomodoroState(
            minutes = 0, seconds = 0, isRunning = true, isBreak = false, pomodoroCount = 2
        )

        val next = tickPomodoro(current)

        assertEquals(3, next.pomodoroCount)
        assertTrue(next.isBreak)
        assertEquals(POMODORO_BREAK_MINUTES, next.minutes)
        assertEquals(0, next.seconds)
        assertFalse(next.isRunning, "debe pausarse solo, no encadenar el descanso automáticamente")
    }

    @Test
    fun `al terminar un descanso, vuelve a estudio pausado sin sumar otro pomodoro`() {
        val current = PomodoroState(
            minutes = 0, seconds = 0, isRunning = true, isBreak = true, pomodoroCount = 3
        )

        val next = tickPomodoro(current)

        assertEquals(3, next.pomodoroCount)
        assertFalse(next.isBreak)
        assertEquals(POMODORO_STUDY_MINUTES, next.minutes)
        assertFalse(next.isRunning)
    }

    @Test
    fun `tickCompletesStudyBlock es true solo cuando el tick que sigue cierra un bloque de estudio`() {
        assertTrue(tickCompletesStudyBlock(PomodoroState(minutes = 0, seconds = 0, isBreak = false)))
        assertFalse(tickCompletesStudyBlock(PomodoroState(minutes = 0, seconds = 0, isBreak = true)))
        assertFalse(tickCompletesStudyBlock(PomodoroState(minutes = 1, seconds = 0, isBreak = false)))
        assertFalse(tickCompletesStudyBlock(PomodoroState(minutes = 0, seconds = 1, isBreak = false)))
    }
}
