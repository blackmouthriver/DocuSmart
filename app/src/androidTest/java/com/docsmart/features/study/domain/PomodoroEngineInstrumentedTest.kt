package com.docsmart.features.study.domain

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PomodoroEngine] real con el contexto de instrumentación: iniciar arranca el bucle de 1 s y el
 * servicio en primer plano ([PomodoroTimerService], que refleja el estado en su notificación),
 * pausar/reiniciar los detienen. Espera con `first {}` (sin dormir) y siempre deja el motor en reposo.
 */
class PomodoroEngineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetBefore() = PomodoroEngine.reset()

    @After
    fun resetAfter() = PomodoroEngine.reset()

    private fun awaitFirstTick(): PomodoroState =
        runBlocking {
            withTimeout(6_000) { PomodoroEngine.state.first { it.minutes < POMODORO_STUDY_MINUTES } }
        }

    @Test
    fun iniciar_avanzaElReloj_pausar_y_reiniciar_lo_dejanEnReposo() {
        val initial = PomodoroEngine.state.value
        assertFalse(initial.isRunning)
        assertEquals(POMODORO_STUDY_MINUTES, initial.minutes)

        PomodoroEngine.toggle(context)
        assertTrue(PomodoroEngine.state.value.isRunning)

        // Tras ~1 s el bloque de estudio pasa de 25:00 a 24:59.
        val ticked = awaitFirstTick()
        assertEquals(POMODORO_STUDY_MINUTES - 1, ticked.minutes)
        assertEquals(59, ticked.seconds)

        PomodoroEngine.toggle(context)
        assertFalse(PomodoroEngine.state.value.isRunning)
        assertEquals(POMODORO_STUDY_MINUTES - 1, PomodoroEngine.state.value.minutes)

        PomodoroEngine.reset()
        val reset = PomodoroEngine.state.value
        assertFalse(reset.isRunning)
        assertFalse(reset.isBreak)
        assertEquals(POMODORO_STUDY_MINUTES, reset.minutes)
        assertEquals(0, reset.seconds)
    }

    @Test
    fun pausarYReanudarRapido_nuncaDuplicaElBucle() {
        PomodoroEngine.toggle(context)
        PomodoroEngine.toggle(context)
        PomodoroEngine.toggle(context)
        assertTrue(PomodoroEngine.state.value.isRunning)

        // Un solo bucle: en ~1 s el reloj baja exactamente un segundo (no dos).
        val ticked = awaitFirstTick()
        assertEquals(59, ticked.seconds)
    }
}
