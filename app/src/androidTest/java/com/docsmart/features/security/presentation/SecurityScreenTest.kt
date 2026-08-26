package com.docsmart.features.security.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.security.SecurityManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.security.domain.PdfPasswordUseCase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Flujo crítico #3 de Compose UI Testing (junto con ViewerScreenTest y
 * ConverterScreenTest): desbloqueo con PIN de la Carpeta Segura.
 *
 * SecurityManager NO se mockea -- es una clase concreta de una sola
 * responsabilidad (hash SHA-256 + SharedPreferences), igual de barata de
 * usar real que de mockear, y usarla real da protección de regresión
 * genuina sobre RF-SEC-01/02 (configurar/verificar PIN) a nivel de UI, no
 * solo a nivel de ViewModel. Se envuelve el Context real de instrumentación
 * en un `ContextWrapper` propio que solo redefine `getSharedPreferences()`/
 * `getFilesDir()`, para aislar el estado del test del `docusmart_security`
 * real del dispositivo (mismo prefs/carpeta que usa la app instalada) --
 * todo lo demás (`BiometricManager.from(context)` dentro de
 * `isBiometricAvailable()`) sigue delegando al Context real por herencia de
 * `ContextWrapper`, sin necesidad de mockearlo. (Probado primero con
 * `spyk(realContext)` de MockK -- falla con `MockKException: Can't
 * instantiate proxy for class android.app.ContextImpl`, porque es una clase
 * final del framework que el agente inline de MockK no puede interceptar;
 * `ContextWrapper` es la vía estándar de Android para este caso.)
 */
class SecurityScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedPrefsContext(
        base: Context,
        private val fakePrefs: SharedPreferences,
        private val testFilesDir: File
    ) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
        override fun getFilesDir(): File = testFilesDir
    }

    private fun buildViewModel(): SecurityViewModel {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = IsolatedPrefsContext(
            base         = realContext,
            fakePrefs    = fakeSharedPreferences(),
            testFilesDir = File(realContext.cacheDir, "security_test_${System.currentTimeMillis()}")
        )

        return SecurityViewModel(
            securityManager    = SecurityManager(context), // real, no mock
            pdfPasswordUseCase = mockk<PdfPasswordUseCase>(relaxed = true)
        )
    }

    @Test
    fun ingresarPinCorrecto_desbloqueaYMuestraCarpetaSegura() {
        val viewModel = buildViewModel()
        composeRule.runOnUiThread { viewModel.setupPin("1234") }
        composeRule.waitForIdle()
        // setupPin() ya desbloquea -- se vuelve a bloquear para probar el
        // desbloqueo real con PIN por la UI, en vez de solo por el ViewModel.
        composeRule.runOnUiThread { viewModel.goToLocked() }

        composeRule.setContent {
            // Fuerza español -- "Carpeta Segura"/"PIN incorrecto" vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // LocalContext ya no encadena de vuelta a la Activity real
            // (createConfigurationContext() no es un ContextWrapper) --
            // SecureFolderContent usa rememberLauncherForActivityResult()
            // para el picker de archivos (visible tras desbloquear), que se
            // resuelve vía LocalActivityResultRegistryOwner/
            // LocalOnBackPressedDispatcherOwner, no encadenando desde
            // LocalContext. Sin re-proveerlos apuntando a la Activity real,
            // se rompe con "No ActivityResultRegistryOwner was provided" al
            // componer la pantalla ya desbloqueada.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) {
                SecurityScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4").performClick()

        // "Carpeta Segura" (security_secure_folder) solo se muestra en
        // SecurityScreenState.UNLOCKED, tras unlockAndLoadFiles() -- que
        // corre en Dispatchers.IO, no en el hilo de Compose.
        // waitForIdle() no espera esa corrutina; en el emulador de CI
        // (más lento que el dispositivo real usado en desarrollo) la
        // aserción llegaba antes de que terminara, y fallaba con "is not
        // displayed" en vez de fallar por un PIN mal verificado. Se espera
        // explícitamente a que el texto exista.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Carpeta Segura")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Carpeta Segura").assertIsDisplayed()
    }

    @Test
    fun ingresarPinIncorrecto_muestraMensajeDeError() {
        val viewModel = buildViewModel()
        composeRule.runOnUiThread { viewModel.setupPin("1234") }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { viewModel.goToLocked() }

        composeRule.setContent {
            // Fuerza español -- "Carpeta Segura"/"PIN incorrecto" vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // LocalContext ya no encadena de vuelta a la Activity real
            // (createConfigurationContext() no es un ContextWrapper) --
            // SecureFolderContent usa rememberLauncherForActivityResult()
            // para el picker de archivos (visible tras desbloquear), que se
            // resuelve vía LocalActivityResultRegistryOwner/
            // LocalOnBackPressedDispatcherOwner, no encadenando desde
            // LocalContext. Sin re-proveerlos apuntando a la Activity real,
            // se rompe con "No ActivityResultRegistryOwner was provided" al
            // componer la pantalla ya desbloqueada.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) {
                SecurityScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("9").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("9").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("PIN incorrecto")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("PIN incorrecto").assertIsDisplayed()
    }

    // ── Fake mínimo de SharedPreferences con semántica real de lectura/escritura
    // (mismo patrón que SecurityManagerTest.kt a nivel unitario) ──────────────
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
        every { editor.remove(any()) } answers {
            store.remove(firstArg<String>())
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
}
