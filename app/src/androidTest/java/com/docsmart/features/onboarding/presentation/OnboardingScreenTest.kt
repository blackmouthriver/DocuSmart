package com.docsmart.features.onboarding.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DownloadsAccessManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Baja #17 de RF-QA-01 (ver compose-ui-testing.md):
 * Onboarding -- recorrer y completar, marca como visto y navega a Home.
 *
 * `OnboardingScreen` recibe su `OnboardingViewModel` con valor por defecto
 * `hiltViewModel()`, y usa `rememberLauncherForActivityResult()` para
 * vincular una carpeta por SAF -- mismo motivo que `LibraryScreenTest`/
 * `ConverterScreenTest`/`SecurityScreenTest` para reproveer
 * `LocalActivityResultRegistryOwner`/`LocalOnBackPressedDispatcherOwner`
 * apuntando a la Activity real, y para pasar el ViewModel construido a
 * mano en vez de dejar que caiga en `hiltViewModel()` (que exige una
 * Activity instrumentada con Hilt, y este test usa una ComponentActivity
 * plana). El estado propio de "completado" persiste directo en
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

    private class IsolatedPrefsContext(
        base: Context,
    ) : ContextWrapper(base) {
        private val prefsByName = mutableMapOf<String, SharedPreferences>()

        override fun getSharedPreferences(
            name: String?,
            mode: Int,
        ): SharedPreferences = prefsByName.getOrPut(name ?: "default") { fakeSharedPreferences() }

        private fun fakeSharedPreferences(): SharedPreferences {
            val store = mutableMapOf<String, Any?>()
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

    // Bug real corregido 2026-09-09: OnboardingScreen(onFinished = ...) sin
    // pasarle `viewModel` cae en el valor por defecto hiltViewModel(), que
    // exige una Activity instrumentada con Hilt -- este test usa una
    // ComponentActivity plana (mismo patrón que HomeScreenTest/
    // LibraryScreenTest), así que fallaba el 100% de las veces, tanto en
    // el emulador de CI como en un dispositivo real de Firebase Test Lab.
    // Se construye el ViewModel a mano y se pasa explícito, igual que en
    // esos otros tests.
    private fun buildViewModel(): OnboardingViewModel {
        val downloadsAccessManager = mockk<DownloadsAccessManager>(relaxed = true)
        every { downloadsAccessManager.linkedFolderUri } returns MutableStateFlow(null)
        return OnboardingViewModel(downloadsAccessManager)
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
            CompositionLocalProvider(
                LocalContext provides isolatedContext,
                LocalResources provides isolatedContext.resources,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) {
                OnboardingScreen(onFinished = { finished = true }, viewModel = buildViewModel())
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

        // Bug real corregido 2026-09-09: al agregarse la 5ta slide (fila 22
        // del backlog UX, vincular carpeta por SAF) el test se quedó
        // esperando "¡Empezar!" un click antes de tiempo -- "Modo Estudio"
        // ya no es la última página, "Vincula tus documentos" sí lo es.
        composeRule.onNodeWithText("Siguiente").performClick()
        waitForText("Vincula tus documentos")
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
            CompositionLocalProvider(
                LocalContext provides isolatedContext,
                LocalResources provides isolatedContext.resources,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) {
                OnboardingScreen(onFinished = { finished = true }, viewModel = buildViewModel())
            }
        }
        waitForText("Saltar")

        composeRule.onNodeWithText("Saltar").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) { finished }
        assertTrue(finished)
        assertTrue(hasCompletedOnboarding(isolatedContext))
    }
}
