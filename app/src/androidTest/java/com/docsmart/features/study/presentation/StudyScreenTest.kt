package com.docsmart.features.study.presentation

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.study.domain.PomodoroEngine
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Media #14 de RF-QA-01 (ver compose-ui-testing.md):
 * Estudio -- guardar/eliminar nota, Pomodoro inicia/pausa. No cubre "orden
 * de la lista" (las notas siempre se insertan al inicio, sin control de
 * orden por el usuario -- no hay nada que ejercitar ahí más allá de lo que
 * ya prueba guardar dos veces, fuera de alcance de un golden path).
 *
 * `StudyViewModel` solo expone `adManager` (todo el resto del estado de
 * `StudyScreen` es `remember`/objetos singleton) -- se mockea igual que en
 * `QrCreatorScreenTest`/`SettingsScreenTest`.
 *
 * `StudyNotesStorage` persiste en `SharedPreferences` reales por nombre
 * (`study_notes`) -- se aísla con el mismo patrón `IsolatedPrefsContext` ya
 * usado en `SettingsScreenTest`, envolviendo el `LocalContext` de la
 * composición para no tocar las notas reales del dispositivo de pruebas.
 *
 * `PomodoroEngine` es un objeto singleton real con su propio
 * `CoroutineScope` y un servicio en primer plano real (`PomodoroTimerService`)
 * -- no es mockeable ni aislable por instancia. Se resetea explícitamente
 * antes y después de la prueba (`PomodoroEngine.reset()`) para no dejar el
 * servicio corriendo ni filtrar estado "en progreso" a otras pruebas que
 * compartan el mismo proceso de instrumentación.
 *
 * **Hallazgo real**: entrar directo a la pestaña Pomodoro dispara la
 * solicitud real del permiso `POST_NOTIFICATIONS` (Android 13+, ver
 * `LaunchedEffect(selectedTab)` en `StudyScreen`) -- el diálogo real del
 * sistema tapa la Activity y `getAllSemanticsNodes()` nunca vuelve a
 * encontrar nada (`IllegalStateException: No compose hierarchies found`),
 * ya que nada en la prueba puede tocar ese diálogo (mismo hallazgo ya
 * documentado para diálogos de permisos del sistema en este dispositivo).
 * Se concede el permiso de antemano con `GrantPermissionRule`, mismo
 * patrón ya usado en `LibraryScreenTest` para el permiso de almacenamiento.
 */
class StudyScreenTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyArray()
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private inner class IsolatedPrefsContext(base: Context) : ContextWrapper(base) {
        private val prefsByName = mutableMapOf<String, SharedPreferences>()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            prefsByName.getOrPut(name ?: "default") { fakeSharedPreferences() }
    }

    private fun fakeSharedPreferences(): SharedPreferences {
        val store  = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg<String>()] as? String) ?: secondArg()
        }
        return prefs
    }

    private fun buildViewModel(): StudyViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        return StudyViewModel(adManager = adManager)
    }

    // Fuerza español -- el emulador de CI arranca en inglés por defecto
    // (ver com.docsmart.core.ui.test.forceLocale). StudyScreen usa
    // rememberLauncherForActivityResult() para elegir documento/dictado por
    // voz/permiso de notificaciones -- mismo motivo que
    // ConverterScreenTest/QrCreatorScreenTest para reproveer estos dos
    // apuntando a la Activity real.
    private fun setContentIsolated(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val isolatedContext = remember(baseContext) {
                IsolatedPrefsContext(forceLocale(baseContext, "es-ES"))
            }
            CompositionLocalProvider(
                LocalContext provides isolatedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun guardarNota_apareceEnLaLista_eliminarlaVacíaLaLista() {
        setContentIsolated {
            StudyScreen(viewModel = buildViewModel())
        }
        waitForText("Notas")

        composeRule.onNodeWithText("Notas").performClick()
        waitForText("Título de la nota")

        composeRule.onNodeWithText("Título de la nota").performTextInput("Mi nota de prueba")
        composeRule.onNodeWithText("Escribe o dicta tu nota…").performTextInput("Contenido de la nota")
        composeRule.onNodeWithText("Guardar nota").performClick()

        waitForText("Mi nota de prueba")
        composeRule.onNodeWithText("Mi nota de prueba").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Eliminar nota").performClick()
        waitForText("Aún no tienes notas guardadas")
    }

    @Test
    fun pomodoro_iniciarYPausar_cambianElEstadoDelBoton() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        PomodoroEngine.reset(appContext)

        setContentIsolated {
            StudyScreen(initialTab = 2, viewModel = buildViewModel())
        }
        waitForText("Iniciar")

        composeRule.onNodeWithText("Iniciar").performClick()
        waitForText("Pausar")
        waitForText("en progreso")

        composeRule.onNodeWithText("Pausar").performClick()
        waitForText("Iniciar")
        waitForText("pausado")

        composeRule.onNodeWithText("Reiniciar").performClick()
        PomodoroEngine.reset(appContext)
    }
}
