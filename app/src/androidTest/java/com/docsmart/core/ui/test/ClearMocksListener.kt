package com.docsmart.core.ui.test

import io.mockk.clearAllMocks
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * Libera el registro de llamadas de MockK al terminar cada prueba.
 *
 * Hallazgo de la ronda 20 (volcado de heap): en Android, MockK deja los mocks
 * retenidos toda la vida del proceso y cada llamada guarda una Invocation con su
 * stack trace (108 mil en 89 pruebas de pdftools). Con ~560 pruebas en un solo
 * proceso el heap se llenaba (OutOfMemoryError) y el proceso moría antes de que
 * JaCoCo escribiera la cobertura. Se registra vía `testInstrumentationRunnerArguments`
 * (clave `listener`) en app/build.gradle.kts.
 */
class ClearMocksListener : RunListener() {
    override fun testFinished(description: Description?) {
        runCatching { clearAllMocks(answers = false) }
    }
}
