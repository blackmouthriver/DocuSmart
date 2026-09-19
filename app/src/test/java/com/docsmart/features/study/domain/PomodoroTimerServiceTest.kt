package com.docsmart.features.study.domain

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

/**
 * Cubre `flushPendingCompletionEvent()`, extraída de
 * `PomodoroTimerService.onDestroy()` (ver comentario ahí y en el propio
 * `onDestroy()`): hallazgo real de esta ronda (14) -- `PomodoroEngine.tick()`
 * llama `context.stopService()` de forma síncrona apenas un bloque termina,
 * una llamada Binder directa a ActivityManager que puede ganarle la carrera
 * al colector interno de `completionEvents` (lanzado vía
 * `launchIn(serviceScope)` sobre `Dispatchers.Default`, que necesita ser
 * despachado a un hilo del pool antes de correr `postCompletionAlert()`).
 * Si `stopService()` gana esa carrera, `onDestroy()` cancelaba `serviceScope`
 * antes de que el colector alcanzara a avisar que el bloque terminó --
 * reintroduciendo en silencio, por una condición de carrera, el mismo bug de
 * "fin de sesión silenciosa" que este archivo ya había corregido antes. El
 * Service en sí no es testeable sin Robolectric (no lo usa este proyecto),
 * así que se prueba la decisión pura por separado.
 */
class PomodoroTimerServiceTest {
    @Test
    fun `con un evento pendiente en replayCache, postea la alerta y despues consume el evento`() {
        val posted = mutableListOf<Boolean>()
        var consumed = false

        flushPendingCompletionEvent(
            replayCache = listOf(true),
            postAlert = { posted.add(it) },
            consume = { consumed = true },
        )

        verifyPostedThenConsumed(posted, consumed, expectedWasBreak = true)
    }

    @Test
    fun `propaga el valor exacto del evento pendiente (bloque de estudio, no descanso)`() {
        val posted = mutableListOf<Boolean>()
        var consumed = false

        flushPendingCompletionEvent(
            replayCache = listOf(false),
            postAlert = { posted.add(it) },
            consume = { consumed = true },
        )

        verifyPostedThenConsumed(posted, consumed, expectedWasBreak = false)
    }

    @Test
    fun `sin evento pendiente no postea ni consume nada -- el colector normal ya lo hizo a tiempo`() {
        val postAlert = mockk<(Boolean) -> Unit>(relaxed = true)
        val consume = mockk<() -> Unit>(relaxed = true)

        flushPendingCompletionEvent(replayCache = emptyList(), postAlert = postAlert, consume = consume)

        verify(exactly = 0) { postAlert(any()) }
        verify(exactly = 0) { consume() }
    }

    @Test
    fun `llama a postAlert antes que consume -- consumir primero perderia el evento si postAlert fallara`() {
        val postAlert = mockk<(Boolean) -> Unit>(relaxed = true)
        val consume = mockk<() -> Unit>(relaxed = true)

        flushPendingCompletionEvent(replayCache = listOf(true), postAlert = postAlert, consume = consume)

        verifyOrder {
            postAlert(true)
            consume()
        }
    }

    private fun verifyPostedThenConsumed(
        posted: List<Boolean>,
        consumed: Boolean,
        expectedWasBreak: Boolean,
    ) {
        org.junit.jupiter.api.Assertions
            .assertEquals(listOf(expectedWasBreak), posted)
        org.junit.jupiter.api.Assertions
            .assertTrue(consumed)
    }
}
