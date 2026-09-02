package com.docsmart.features.settings.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.theme.AccentColor
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.FontScale
import com.docsmart.core.ui.theme.ThemeManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Alta #15 de RF-QA-01 (ver compose-ui-testing.md):
 * Ajustes -- cambiar tema/idioma/acento, "Restablecer configuración".
 *
 * `ThemeManager`/`LanguageManager` se usan REALES (no se mockean) -- son
 * clases concretas de una sola responsabilidad (StateFlow +
 * SharedPreferences), igual de baratas de usar reales que de mockear, y
 * dan protección de regresión genuina sobre RF-SET-06/07 a nivel de UI,
 * no solo de ViewModel (mismo criterio ya aplicado en `SecurityScreenTest`
 * para `SecurityManager`). Se envuelve el Context real de instrumentación
 * en un `ContextWrapper` propio que aísla cualquier `SharedPreferences`
 * pedida por nombre (`docusmart_theme`/`docusmart_language`) del estado
 * real del dispositivo -- sin esto, la prueba cambiaría el tema/idioma
 * real de la instalación de desarrollo.
 */
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Generaliza el patrón de IsolatedPrefsContext de SecurityScreenTest a
    // cualquier nombre de SharedPreferences -- ThemeManager y LanguageManager
    // usan namespaces distintos ("docusmart_theme"/"docusmart_language"),
    // cada uno necesita su propio backing store aislado, no solo uno.
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

    private fun buildManagers(): Pair<ThemeManager, LanguageManager> {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedContext = IsolatedPrefsContext(realContext)
        return ThemeManager(isolatedContext) to LanguageManager(isolatedContext)
    }

    private fun buildSettingsViewModel(): SettingsViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        return SettingsViewModel(adManager = adManager)
    }

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Tema"/"Restablecer configuración"/etc.
            // vienen de stringResource(), y el emulador de CI arranca en
            // inglés por defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localizedContext) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cambiarTemaIdiomaYAcento_actualizanElSubtituloDeCadaOpcion() {
        val (themeManager, languageManager) = buildManagers()
        val viewModel = buildSettingsViewModel()

        setContentWithLocale {
            SettingsScreen(themeManager = themeManager, languageManager = languageManager, viewModel = viewModel)
        }
        waitForText("Tema")

        // ── Tema: Sistema (default) → Oscuro ──────────────────────────────
        composeRule.onNodeWithText("Tema").performClick()
        waitForText("Seleccionar tema")
        composeRule.onNodeWithText("Oscuro").performClick()
        waitForText("Oscuro")
        assertEquals(AppTheme.DARK, themeManager.currentTheme.value)

        // ── Color de acento: Azul (default) → Verde ───────────────────────
        composeRule.onNodeWithText("Color de acento").performClick()
        waitForText("Seleccionar color de acento")
        composeRule.onNodeWithText("Verde").performClick()
        waitForText("Verde")
        assertEquals(AccentColor.GREEN, themeManager.accentColor.value)

        // ── Idioma: nombre nativo, no traducido por forceLocale a propósito
        // (AppLanguage.nativeLabel siempre se muestra en su propio idioma)
        composeRule.onNodeWithText("Idioma").performClick()
        waitForText("Seleccionar idioma")
        composeRule.onNodeWithText("Português").performClick()
        composeRule.waitForIdle()
        assertEquals(com.docsmart.core.ui.AppLanguage.PORTUGUESE, languageManager.currentLanguage.value)
    }

    @Test
    fun restablecerConfiguracion_vuelveTemaAcentoYTamanoDeLetraADefault() {
        val (themeManager, languageManager) = buildManagers()
        themeManager.setTheme(AppTheme.DARK)
        themeManager.setAccentColor(AccentColor.PINK)
        themeManager.setFontScale(FontScale.LARGE)
        val viewModel = buildSettingsViewModel()

        setContentWithLocale {
            SettingsScreen(themeManager = themeManager, languageManager = languageManager, viewModel = viewModel)
        }
        // "Restablecer configuración" está en la sección Sistema, al final
        // de la LazyColumn -- no se compone hasta hacer scroll hasta ahí
        // (a diferencia de Tema/Color de acento/Idioma, cerca del inicio).
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Restablecer configuración"))

        composeRule.onNodeWithText("Restablecer configuración").performClick()
        // Botón de confirmación del diálogo (settings_reset_confirm =
        // "Restablecer", distinto del ítem "Restablecer configuración" ya
        // tocado -- textos exactos distintos, sin ambigüedad).
        waitForText("Restablecer")

        composeRule.onNodeWithText("Restablecer").performClick()
        composeRule.waitForIdle()

        assertEquals(AppTheme.SYSTEM, themeManager.currentTheme.value)
        assertEquals(AccentColor.BLUE, themeManager.accentColor.value)
        assertEquals(FontScale.NORMAL, themeManager.fontScale.value)
    }
}
