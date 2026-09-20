package com.docsmart.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Ronda 18: `CrashlyticsTree` filtra por prioridad (DEBUG/VERBOSE nunca salen)
 * y solo registra como no fatal WARN/ERROR con una excepción real. Se ejerce
 * por la API pública de Timber (`i`/`w`/`e`), que pasa por `isLoggable`.
 */
class CrashlyticsTreeTest {
    private lateinit var crashlytics: FirebaseCrashlytics
    private val tree = CrashlyticsTree()

    @BeforeEach
    fun setUp() {
        crashlytics = mockk(relaxed = true)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `un mensaje DEBUG no se envia a Crashlytics`() {
        tree.d("detalle de depuracion")

        verify(exactly = 0) { crashlytics.log(any()) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `un mensaje INFO queda como breadcrumb sin registrar excepcion`() {
        tree.i("hola")

        verify(exactly = 1) { crashlytics.log(": hola") }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `un WARN sin excepcion solo deja breadcrumb`() {
        tree.w("advertencia")

        verify(exactly = 1) { crashlytics.log(": advertencia") }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `un INFO con excepcion no la registra como no fatal`() {
        tree.i(IllegalStateException("x"), "info")

        verify(exactly = 1) { crashlytics.log(match { it.startsWith(": info") }) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `un WARN con excepcion la registra como no fatal`() {
        val error = IllegalArgumentException("x")

        tree.w(error, "advertencia")

        verify(exactly = 1) { crashlytics.log(match { it.startsWith(": advertencia") }) }
        verify(exactly = 1) { crashlytics.recordException(error) }
    }

    @Test
    fun `un ERROR con excepcion la registra como no fatal`() {
        val error = IllegalStateException("x")

        tree.e(error, "fallo")

        verify(exactly = 1) { crashlytics.recordException(error) }
    }

    @Test
    fun `un ERROR sin excepcion no registra no fatal`() {
        tree.e("fallo sin excepcion")

        verify(exactly = 1) { crashlytics.log(": fallo sin excepcion") }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }
}
