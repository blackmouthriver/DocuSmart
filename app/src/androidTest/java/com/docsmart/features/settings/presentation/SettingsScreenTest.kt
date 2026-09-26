package com.docsmart.features.settings.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.core.ui.theme.AccentColor
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.FontScale
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
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
    private inner class IsolatedPrefsContext(
        base: Context,
    ) : ContextWrapper(base) {
        private val prefsByName = mutableMapOf<String, SharedPreferences>()

        override fun getSharedPreferences(
            name: String?,
            mode: Int,
        ): SharedPreferences = prefsByName.getOrPut(name ?: "default") { fakeSharedPreferences() }
    }

    // Bug real corregido 2026-09-09: solo simulaba getString/putString --
    // ThemeManager.loadAnimatedBackgroundEnabled() (fondo animado, agregado
    // 2026-09-06) usa getBoolean/putBoolean sobre este mismo prefs, y al no
    // estar simulado MockK lanzaba MockKException al construir ThemeManager,
    // haciendo fallar el 100% de las veces cambiarTemaIdiomaYAcento_.../
    // restablecerConfiguracion_... tanto en el emulador de CI como en
    // Firebase Test Lab.
    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg<String>()] as? String) ?: secondArg()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            (store[firstArg<String>()] as? Boolean) ?: secondArg()
        }
        return prefs
    }

    private fun buildManagers(): Pair<ThemeManager, LanguageManager> {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedContext = IsolatedPrefsContext(realContext)
        return ThemeManager(isolatedContext) to LanguageManager(isolatedContext)
    }

    // Bug preexistente encontrado 2026-09-14 (no introducido por el
    // rediseño de Ajustes): SettingsViewModel ganó el parámetro
    // `soundEffectPlayer` cuando se agregaron los efectos de sonido
    // (backlog 2026-09-12), pero este helper nunca se actualizó -- dejaba
    // compileDebugAndroidTestKotlin roto para TODO el módulo (confirmado
    // reproduciendo el mismo error sobre `main` sin los cambios de esta
    // sesión). Se corrige acá porque bloquea verificar los tests de este
    // mismo archivo; ConverterScreenTest/LibraryScreenTest/TrashScreenTest/
    // SecurityScreenTest tienen el mismo tipo de rotura por otros
    // parámetros nuevos, fuera del alcance de este cambio.
    private fun buildSettingsViewModel(): SettingsViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        // Mismo bug preexistente que los 3 StateFlow<Boolean> ya documentados
        // arriba, pero con StateFlow<Long?>/StateFlow<Int?> (HU-54 trial):
        // sin stub explícito, un mock relajado devuelve un proxy de MockK en
        // vez de null, y SettingsScreen lanza ClassCastException al leerlos
        // (trialEndsAtMillis!! > System.currentTimeMillis()).
        every { adManager.trialEndsAtMillis } returns MutableStateFlow(null)
        every { adManager.autoTrialDaysRemaining } returns MutableStateFlow(null)

        val soundEffectPlayer = mockk<SoundEffectPlayer>(relaxed = true)
        every { soundEffectPlayer.enabled } returns MutableStateFlow(true)

        val downloadsAccessManager = mockk<DownloadsAccessManager>(relaxed = true)
        every { downloadsAccessManager.linkedFolderUri } returns MutableStateFlow(null)

        val trashRepository = mockk<TrashRepository>(relaxed = true)
        val securityManager = mockk<com.docsmart.core.security.SecurityManager>(relaxed = true)

        return SettingsViewModel(
            adManager = adManager,
            soundEffectPlayer = soundEffectPlayer,
            downloadsAccessManager = downloadsAccessManager,
            trashRepository = trashRepository,
            securityManager = securityManager,
        )
    }

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Tema"/"Restablecer configuración"/etc.
            // vienen de stringResource(), y el emulador de CI arranca en
            // inglés por defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // Bug real corregido 2026-09-09: SettingsScreen usa
            // rememberLauncherForActivityResult() para vincular una carpeta
            // por SAF -- sin reproveer estos dos apuntando a la Activity
            // real (mismo motivo que LibraryScreenTest/ConverterScreenTest/
            // SecurityScreenTest), fallaba con "No ActivityResultRegistryOwner
            // was provided" el 100% de las veces, tanto en el emulador de CI
            // como en Firebase Test Lab -- quedaba oculto detrás del bug de
            // MockKException en buildManagers(), ya corregido arriba.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalResources provides localizedContext.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntilOrDump("CI_HANG_SettingsScreenTest") {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Rediseño de Ajustes 2026-09-14: Tema/Color de acento/Tamaño de letra
    // pasan de diálogo con RadioButton a controles en línea dentro de la
    // tarjeta de Apariencia (segmented buttons + carrusel de círculos) --
    // ya no hay diálogo que abrir para estos tres, se interactúa
    // directamente. Solo Idioma conserva su diálogo (lista larga).
    @Test
    fun cambiarTemaAcentoTamanoDeLetraEIdioma_actualizanElEstado() {
        val (themeManager, languageManager) = buildManagers()
        val viewModel = buildSettingsViewModel()

        setContentWithLocale {
            SettingsScreen(themeManager = themeManager, languageManager = languageManager, viewModel = viewModel)
        }
        waitForText("Tema")

        // ── Tema: Sistema (default) → Oscuro (segmented button) ───────────
        composeRule.onNodeWithText("Oscuro").performClick()
        composeRule.waitForIdle()
        assertEquals(AppTheme.DARK, themeManager.currentTheme.value)

        // ── Color de acento: Azul (default) → Verde (círculo con
        // contentDescription, ya no tiene texto/RadioButton propio) ───────
        composeRule.onNodeWithContentDescription("Verde").performClick()
        composeRule.waitForIdle()
        assertEquals(AccentColor.GREEN, themeManager.accentColor.value)

        // ── Tamaño de letra: Normal (default) → Grande (segmented button) ─
        composeRule.onNodeWithText("Grande").performClick()
        composeRule.waitForIdle()
        assertEquals(FontScale.LARGE, themeManager.fontScale.value)

        // ── Idioma: sigue con diálogo propio (lista larga, no cabe inline).
        // Nombre nativo, no traducido por forceLocale a propósito
        // (AppLanguage.nativeLabel siempre se muestra en su propio idioma)
        composeRule.onNodeWithText("Idioma").performClick()
        waitForText("Seleccionar idioma")
        composeRule.onNodeWithText("Português").performClick()
        composeRule.waitForIdle()
        assertEquals(com.docsmart.core.ui.AppLanguage.PORTUGUESE, languageManager.currentLanguage.value)
    }

    // Ronda 23: `accentColorLabel()` es un `when` exhaustivo de 10 ramas, pero el carrusel
    // (LazyRow) solo compone los círculos visibles en el viewport chico de CI -- Turquesa/
    // Índigo/Rojo/Ámbar/Cian nunca se pintaban (0% de esas 5 ramas). No hace falta scrollear
    // el carrusel para ejercerlas: el subtítulo de la fila ("Color de acento: <label>") llama
    // a `accentColorLabel(currentAccentColor)` con el color YA seleccionado, así que basta con
    // cambiarlo por código (mismo ThemeManager real que usa la UI) para recomponer ese texto.
    @Test
    fun colorDeAcento_muestraLaEtiquetaDeLosColoresQueElCarruselNoLlegaAPintar() {
        val (themeManager, languageManager) = buildManagers()
        val viewModel = buildSettingsViewModel()

        setContentWithLocale {
            SettingsScreen(themeManager = themeManager, languageManager = languageManager, viewModel = viewModel)
        }
        waitForText("Tema")

        listOf(
            AccentColor.TEAL to "Turquesa",
            AccentColor.INDIGO to "Índigo",
            AccentColor.RED to "Rojo",
            AccentColor.AMBER to "Ámbar",
            AccentColor.CYAN to "Cian",
        ).forEach { (color, label) ->
            themeManager.setAccentColor(color)
            composeRule.waitForIdle()
            composeRule.onNodeWithText(label).assertExists()
            assertEquals(color, themeManager.accentColor.value)
        }
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
        // Rediseño de Ajustes 2026-09-14: hasScrollAction() ya no basta para
        // identificar la lista -- el carrusel de colores de acento agregó un
        // segundo nodo con scroll (LazyRow anidado dentro de la LazyColumn),
        // por eso se usa el testTag propio de la lista principal.
        composeRule
            .onNodeWithTag("settings_list")
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
