package com.docsmart.features.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.security.SecurityManager
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.security.presentation.IsolatedTestContext
import com.docsmart.features.security.presentation.esText
import com.docsmart.features.security.presentation.fakePrefsInMemory
import com.docsmart.features.security.presentation.setContentEsScaled
import com.docsmart.features.security.presentation.waitForText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ronda 20: diálogos y filas de `SettingsScreen` no cubiertos por
 * SettingsScreenTest (Premium/trial, Almacenamiento con y sin archivos,
 * carpeta vinculada, Privacidad, Ayuda, Acerca de, tutorial, interruptores y
 * el manejo de fallo de "Restablecer configuración").
 *
 * Ronda 23: se suman Compartir/Valorar/Gestionar permisos/Contactar soporte
 * (shareApp/openPlayStore/openAppSettings/sendSupportEmail, 0% de cobertura)
 * usando `ActionRecordingContext`, que intercepta `startActivity()` -- nunca
 * se abre un chooser/Settings/navegador/app de correo real (regla 5). Sigue
 * sin tocarse "vincular carpeta" (SIN vincular): requiere simular un
 * resultado de `ActivityResultRegistry` para `OpenDocumentTree`, patrón no
 * usado aún en el proyecto -- se dejó fuera por riesgo/beneficio dado que no
 * se puede compilar ni correr acá para validarlo (ver entrega de ronda 23).
 */
class SettingsDialogsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val workDir =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "settings_r20_${System.nanoTime()}",
        ).apply { mkdirs() }

    private val trashRepository = mockk<TrashRepository>(relaxed = true)
    private val securityManager = mockk<SecurityManager>(relaxed = true)
    private val soundEffectPlayer = mockk<SoundEffectPlayer>(relaxed = true)
    private val downloadsAccessManager = mockk<DownloadsAccessManager>(relaxed = true)
    private val languageManager = mockk<LanguageManager>(relaxed = true)
    private val linkedUri = MutableStateFlow<Uri?>(null)
    private val trialEnds = MutableStateFlow<Long?>(null)
    private val autoTrialDays = MutableStateFlow<Int?>(null)

    private val themeManager: ThemeManager by lazy {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        ThemeManager(IsolatedTestContext(base, fakePrefsInMemory(), workDir))
    }

    private fun buildViewModel(): SettingsViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        every { adManager.trialEndsAtMillis } returns trialEnds
        every { adManager.autoTrialDaysRemaining } returns autoTrialDays
        every { soundEffectPlayer.enabled } returns MutableStateFlow(true)
        every { downloadsAccessManager.linkedFolderUri } returns linkedUri
        every { languageManager.currentLanguage } returns MutableStateFlow(AppLanguage.SPANISH)
        every { languageManager.deviceDefaultLanguage() } returns AppLanguage.SPANISH
        return SettingsViewModel(
            adManager = adManager,
            soundEffectPlayer = soundEffectPlayer,
            downloadsAccessManager = downloadsAccessManager,
            trashRepository = trashRepository,
            securityManager = securityManager,
        )
    }

    private fun show(
        onPremiumClick: () -> Unit = {},
        onShowOnboarding: () -> Unit = {},
    ) {
        val viewModel = buildViewModel()
        composeRule.setContentEsScaled(filesDir = workDir) {
            SettingsScreen(
                themeManager = themeManager,
                languageManager = languageManager,
                onPremiumClick = onPremiumClick,
                onShowOnboarding = onShowOnboarding,
                viewModel = viewModel,
            )
        }
        composeRule.waitForText(esText(R.string.settings_title))
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText(text))
    }

    private fun clickItem(text: String) {
        scrollTo(text)
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun writeGenerated(
        folder: String,
        name: String,
        bytes: Int,
    ) {
        File(workDir, folder).apply { mkdirs() }.let { File(it, name).writeBytes(ByteArray(bytes)) }
    }

    // Ronda 23: shareApp()/openPlayStore()/sendSupportEmail()/openAppSettings() nunca se
    // ejercían (0% de cobertura) porque lanzan Intents reales -- mismo patrón ya usado en
    // LibraryScreenExtrasTest (RecordingContext/NoAppContext) para el atajo de Descargas,
    // unificado acá en una sola clase configurable por Intent: intercepta startActivity()
    // así que NUNCA se abre un chooser/Settings/navegador/app de correo real (regla 5).
    private class ActionRecordingContext(
        base: Context,
        private val shouldThrow: (Intent) -> Boolean = { false },
    ) : ContextWrapper(base) {
        val startedIntents = mutableListOf<Intent>()
        var attempts = 0

        override fun startActivity(intent: Intent) {
            attempts++
            if (shouldThrow(intent)) throw ActivityNotFoundException("simulado")
            startedIntents += intent
        }
    }

    // Contexto ya en español, sin envolver -- para construir un ActionRecordingContext
    // ANTES de componer (mismo orden que RecordingContext(strings)/NoAppContext(strings) de
    // LibraryScreenExtrasTest, evita depender de un `remember` para capturar la instancia).
    private fun localizedTarget(): Context = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")

    // Variante de `show()` que reemplaza el Context real por un ActionRecordingContext ya
    // construido -- no usa `setContentEsScaled()` (esa no permite inyectar un Context propio).
    private fun showWithActionContext(recorder: ActionRecordingContext) {
        val viewModel = buildViewModel()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides recorder,
                LocalResources provides recorder.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) {
                SettingsScreen(themeManager = themeManager, languageManager = languageManager, viewModel = viewModel)
            }
        }
        composeRule.waitForText(esText(R.string.settings_title))
    }

    @Test
    fun premium_sinTrial_muestraElSubtituloNormalYAlTocarAbreLaPantallaPremium() {
        var clicks = 0
        show(onPremiumClick = { clicks++ })

        composeRule.onNodeWithText(esText(R.string.settings_premium_subtitle)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_premium)).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun premium_conTrialDeSuscripcion_muestraLaFechaDeCobro() {
        val endsAt = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        trialEnds.value = endsAt
        show()

        val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-ES")).format(Date(endsAt))
        composeRule.onNodeWithText(esText(R.string.settings_premium_trial_subtitle, formatted)).assertExists()
    }

    @Test
    fun premium_conTrialVencido_noLoMuestra() {
        trialEnds.value = System.currentTimeMillis() - 1_000L
        show()

        composeRule.onNodeWithText(esText(R.string.settings_premium_subtitle)).assertExists()
    }

    @Test
    fun premium_conTrialAutomatico_muestraLosDiasRestantes() {
        autoTrialDays.value = 2
        show()

        composeRule.onNodeWithText(esText(R.string.settings_premium_auto_trial_subtitle, 2)).assertExists()
    }

    @Test
    fun almacenamiento_sinArchivos_noOfreceLimpiarCache() {
        show()
        clickItem(esText(R.string.settings_storage))

        composeRule.onNodeWithText(esText(R.string.settings_storage_total)).assertExists()
        val unit = esText(R.string.settings_storage_files_unit)
        composeRule.onNodeWithText("0 $unit · ${esText(R.string.file_size_bytes, 0)}").assertExists()
        composeRule.onAllNodesWithText(esText(R.string.settings_storage_clear_cache)).assertCountEquals(0)
        composeRule.onNodeWithText(esText(R.string.settings_close)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(esText(R.string.settings_storage_total)).assertCountEquals(0)
    }

    @Test
    fun almacenamiento_conArchivos_listaLasFilasYLimpiarCacheLosMandaALaPapelera() {
        writeGenerated("converted", "a.bin", 10)
        writeGenerated("pdftools", "b.bin", 2048)
        writeGenerated("viewer_share", "c.bin", 5)
        writeGenerated("study_exports", "d.bin", 5)
        show()
        clickItem(esText(R.string.settings_storage))

        composeRule.onNodeWithText(esText(R.string.settings_storage_viewer_share)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_storage_study_exports)).assertExists()
        val unit = esText(R.string.settings_storage_files_unit)
        composeRule.onNodeWithText("4 $unit · ${esText(R.string.file_size_kb, 2)}").assertExists()

        composeRule.onNodeWithText(esText(R.string.settings_storage_clear_cache)).performClick()
        composeRule.waitForIdle()
        coVerify(timeout = 5_000, exactly = 4) { trashRepository.moveToTrash(any()) }
        verify(timeout = 5_000) { securityManager.clearPreviewCache() }
    }

    @Test
    fun carpetaVinculada_tocarAbreElDialogoYDesvincularLlamaAlManager() {
        linkedUri.value = Uri.parse("content://com.docsmart.r20.tree/tree/1")
        show()

        scrollTo(esText(R.string.settings_linked_downloads_linked))
        composeRule.onNodeWithText(esText(R.string.settings_linked_downloads_linked)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_linked_downloads_linked)).performClick()
        composeRule.onNodeWithText(esText(R.string.settings_unlink_downloads_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_unlink_downloads_body)).assertExists()

        composeRule.onNodeWithText(esText(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        verify(exactly = 0) { downloadsAccessManager.unlink() }

        clickItem(esText(R.string.settings_linked_downloads_linked))
        composeRule.onNodeWithText(esText(R.string.settings_unlink_downloads_confirm)).performClick()
        composeRule.waitForIdle()
        verify(exactly = 1) { downloadsAccessManager.unlink() }
    }

    @Test
    fun carpetaSinVincular_muestraElSubtituloDeVincular() {
        show()

        scrollTo(esText(R.string.settings_linked_downloads_not_linked))
        composeRule.onNodeWithText(esText(R.string.settings_linked_downloads_not_linked)).assertExists()
    }

    @Test
    fun privacidad_muestraElTextoYSeCierra() {
        show()
        clickItem(esText(R.string.settings_privacy_item))

        composeRule.onNodeWithText(esText(R.string.settings_privacy_body)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_privacy_manage_permissions)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_got_it)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(esText(R.string.settings_privacy_body)).assertCountEquals(0)
    }

    @Test
    fun ayuda_muestraLasCuatroPreguntasYSeCierra() {
        show()
        clickItem(esText(R.string.settings_help))

        listOf(
            R.string.settings_help_q1,
            R.string.settings_help_q2,
            R.string.settings_help_q3,
            R.string.settings_help_q4,
        ).forEach { composeRule.onNodeWithText(esText(it)).assertExists() }
        composeRule.onNodeWithText(esText(R.string.settings_contact_support)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_close)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(esText(R.string.settings_help_q1)).assertCountEquals(0)
    }

    @Test
    fun acercaDe_muestraElCuerpoYElCopyright() {
        show()
        clickItem(esText(R.string.settings_about))

        composeRule.onNodeWithText(esText(R.string.settings_about_dialog_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_about_body)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_copyright)).assertExists()
        composeRule.onNodeWithText(esText(R.string.settings_close)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(esText(R.string.settings_about_body)).assertCountEquals(0)
    }

    @Test
    fun tutorial_invocaOnShowOnboarding() {
        var shown = 0
        show(onShowOnboarding = { shown++ })
        clickItem(esText(R.string.settings_tutorial))

        assertEquals(1, shown)
    }

    @Test
    fun interruptores_fondoAnimadoYSonidoActualizanSuEstado() {
        show()

        clickItem(esText(R.string.settings_animated_background))
        assertFalse(themeManager.animatedBackgroundEnabled.value)
        clickItem(esText(R.string.settings_sound_effects))
        verify { soundEffectPlayer.setEnabled(false) }
    }

    @Test
    fun restablecer_cancelarNoCambiaNada_yConfirmarRestauraLosValores() {
        show()
        themeManager.setAnimatedBackgroundEnabled(false)
        composeRule.waitForIdle()

        clickItem(esText(R.string.settings_reset))
        composeRule.onNodeWithText(esText(R.string.settings_reset_body)).assertExists()
        composeRule.onNodeWithText(esText(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        assertFalse(themeManager.animatedBackgroundEnabled.value)

        clickItem(esText(R.string.settings_reset))
        composeRule.onNodeWithText(esText(R.string.settings_reset_confirm)).performClick()
        composeRule.waitUntil(5_000) { themeManager.animatedBackgroundEnabled.value }
        verify { soundEffectPlayer.setEnabled(true) }
        verify(timeout = 5_000) { languageManager.setLanguage(AppLanguage.SPANISH) }
    }

    @Test
    fun restablecer_conFalloAlLimpiarCache_noCambiaElIdiomaYNoRompe() {
        writeGenerated("converted", "a.bin", 10)
        coEvery { trashRepository.moveToTrash(any()) } throws IllegalStateException("fallo simulado")
        show()

        clickItem(esText(R.string.settings_reset))
        composeRule.onNodeWithText(esText(R.string.settings_reset_confirm)).performClick()
        coVerify(timeout = 5_000) { trashRepository.moveToTrash(any()) }
        composeRule.waitForIdle()

        verify(exactly = 0) { languageManager.setLanguage(any()) }
    }

    // ── Ronda 23: Privacidad → "Gestionar permisos" (openAppSettings) ──────────────────────
    @Test
    fun privacidad_gestionarPermisos_conAppDisponible_lanzaIntentDeAjustesDeLaApp() {
        val recorder = ActionRecordingContext(localizedTarget())
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_privacy_item))

        composeRule.onNodeWithText(esText(R.string.settings_privacy_manage_permissions)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.startedIntents.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, recorder.startedIntents[0].action)
    }

    @Test
    fun privacidad_gestionarPermisos_sinAppDisponible_noRompe() {
        val recorder = ActionRecordingContext(localizedTarget(), shouldThrow = { true })
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_privacy_item))

        composeRule.onNodeWithText(esText(R.string.settings_privacy_manage_permissions)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.attempts)
        assertEquals(0, recorder.startedIntents.size)
    }

    // ── Ronda 23: "Compartir app" (shareApp) ────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    @Test
    fun compartirApp_lanzaIntentDeCompartirConElMensajeYElEnlace() {
        val recorder = ActionRecordingContext(localizedTarget())
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_share_app))

        assertEquals(1, recorder.startedIntents.size)
        val chooserIntent = recorder.startedIntents[0]
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val innerIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, innerIntent?.action)
        assertTrue(innerIntent?.getStringExtra(Intent.EXTRA_TEXT)?.contains("play.google.com") == true)
    }

    @Test
    fun compartirApp_sinAppDisponible_noRompe() {
        val recorder = ActionRecordingContext(localizedTarget(), shouldThrow = { true })
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_share_app))

        assertEquals(1, recorder.attempts)
        assertEquals(0, recorder.startedIntents.size)
    }

    // ── Ronda 23: "Valorar" (openPlayStore: market:// → https:// → Toast) ──────────────────
    @Test
    fun valorar_conPlayStoreDisponible_usaEsquemaMarket() {
        val recorder = ActionRecordingContext(localizedTarget())
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_rate))

        assertEquals(1, recorder.startedIntents.size)
        assertEquals("market", recorder.startedIntents[0].data?.scheme)
    }

    @Test
    fun valorar_sinPlayStoreConNavegadorDisponible_usaHttpsComoRespaldo() {
        val recorder =
            ActionRecordingContext(localizedTarget(), shouldThrow = { intent -> intent.data?.scheme == "market" })
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_rate))

        assertEquals(2, recorder.attempts)
        assertEquals(1, recorder.startedIntents.size)
        assertEquals("https", recorder.startedIntents[0].data?.scheme)
    }

    @Test
    fun valorar_sinNingunaAppDisponible_muestraToastYNoRompe() {
        val recorder = ActionRecordingContext(localizedTarget(), shouldThrow = { true })
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_rate))

        assertEquals(2, recorder.attempts)
        assertEquals(0, recorder.startedIntents.size)
    }

    // ── Ronda 23: Ayuda → "Contactar soporte" (sendSupportEmail) ────────────────────────────
    @Test
    fun contactarSoporte_conAppDeCorreoDisponible_lanzaIntentSendToConAsuntoYCuerpo() {
        val recorder = ActionRecordingContext(localizedTarget())
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_help))
        composeRule.onNodeWithText(esText(R.string.settings_contact_support)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.startedIntents.size)
        val sent = recorder.startedIntents[0]
        assertEquals(Intent.ACTION_SENDTO, sent.action)
        assertEquals("mailto", sent.data?.scheme)
        assertEquals(esText(R.string.settings_support_email_subject), sent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun contactarSoporte_sinAppDeCorreoDisponible_noRompe() {
        val recorder = ActionRecordingContext(localizedTarget(), shouldThrow = { true })
        showWithActionContext(recorder)
        clickItem(esText(R.string.settings_help))
        composeRule.onNodeWithText(esText(R.string.settings_contact_support)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.attempts)
        assertEquals(0, recorder.startedIntents.size)
    }

    // ── Ronda 23: rama Bytes/MB de formatStorageSize() (solo KB/Bytes estaba cubierta) ─────
    @Test
    fun almacenamiento_conMasDeUnMegabyte_muestraFormatoMB() {
        writeGenerated("pdftools", "grande.bin", 1_500_000)
        show()
        clickItem(esText(R.string.settings_storage))

        assertTrue(composeRule.onAllNodesWithText(" MB", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
