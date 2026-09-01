package com.docsmart.features.onboarding.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.test.forceLocale
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Baja #17 de RF-QA-01 (ver compose-ui-testing.md):
 * Onboarding -- recorrer y completar, marca como visto y navega a Home.
 *
 * `OnboardingScreen` no tiene ViewModel ni Hilt -- todo su estado es
 * `remember`/`PagerState`, y persiste "completado" directo en
 * `SharedPreferences` ("docusmart_onboarding") vía funciones de nivel de
 * paquete (`markOnboardingCompleted`). Se aísla con el mismo patrón
 * `IsolatedPrefsContext` ya usado en `SettingsScreenTest`/`StudyScreenTest`,
 * construido UNA vez fuera de la composición (no con `remember` dentro de
 * `setContent`) para poder leerlo de nuevo después de la interacción y
 * confirmar que `markOnboardingCompleted()` sí escribió.
 */
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedPrefsContext(base: Context) : ContextWrapper(base) {
        private val prefsByName = mutableMapOf<String, SharedPreferences>()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            prefsByName.getOrPut(name ?: "default") { fakeSharedPreferences() }

        private fun fakeSharedPreferences(): SharedPreferences {
            val store  = mutableMapOf<String, Any?>()
            val editor = mockk<SharedPreferences.Editor>()
            every { editor.putBoolean(any(), any()) } answers {
                store[firstArg<String>()] = secondArg<Boolean>()
                editor
            }
            every { editor.apply() } just Runs

            val prefs = mockk<SharedPreferences>()
            every { prefs.edit() } returns editor
            every { prefs.getBoolean(any(), any()) } answers {
                (store[firstArg<String>()] as? Boolean) ?: secondArg()
            }
            return prefs
        }
    }

    private fun buildIsolatedContext(): Context {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        return IsolatedPrefsContext(forceLocale(appContext, "es-ES"))
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recorrerLos4Slides_yCompletar_marcaVistoYNavega() {
        val isolatedContext = buildIsolatedContext()
        assertFalse(hasCompletedOnboarding(isolatedContext))
        var finished = false

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides isolatedContext) {
                OnboardingScreen(onFinished = { finished = true })
            }
        }
        waitForText("Bienvenido a DocuSmart")
        waitForText("Saltar")

        composeRule.onNodeWithText("Siguiente").performClick()
        waitForText("Convierte cualquier formato")

        composeRule.onNodeWithText("Siguiente").performClick()
        waitForText("Protege tus documentos")

        composeRule.onNodeWithText("Siguiente").performClick()
        waitForText("Modo Estudio")
        // En la última página "Saltar" se oculta (reemplazado por un
        // Spacer del mismo ancho) y el botón cambia a "¡Empezar!".
        waitForText("¡Empezar!")

        composeRule.onNodeWithText("¡Empezar!").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) { finished }
        assertTrue(finished)
        assertTrue(hasCompletedOnboarding(isolatedContext))
    }

    @Test
    fun saltarDesdeLaPrimeraSlide_marcaVistoYNavega() {
        val isolatedContext = buildIsolatedContext()
        var finished = false

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides isolatedContext) {
                OnboardingScreen(onFinished = { finished = true })
            }
        }
        waitForText("Saltar")

        composeRule.onNodeWithText("Saltar").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) { finished }
        assertTrue(finished)
        assertTrue(hasCompletedOnboarding(isolatedContext))
    }
}
