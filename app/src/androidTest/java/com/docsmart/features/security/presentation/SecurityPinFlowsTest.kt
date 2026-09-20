package com.docsmart.features.security.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.docsmart.R
import com.docsmart.core.security.SecurityManager
import com.docsmart.core.util.AppLifecycleTracker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 20: flujos de PIN de `SecurityScreen` (desbloquear, configurar con
 * confirmación, errores, bloqueo por intentos, restablecer) con un
 * `SecurityManager` mockeado y un `SecurityViewModel` armado a mano.
 */
class SecurityPinFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val manager = mockk<SecurityManager>(relaxed = true)

    private fun buildViewModel(tracker: AppLifecycleTracker = mockk(relaxed = true)) =
        SecurityViewModel(
            securityManager = manager,
            pdfPasswordUseCase = mockk(relaxed = true),
            mediaDeletePermission = mockk(relaxed = true),
            documentIdentityMaintenance = mockk(relaxed = true),
            appLifecycleTracker = tracker,
        )

    private fun stubPin(
        hasPin: Boolean,
        biometric: Boolean = false,
    ) {
        every { manager.hasPin() } returns hasPin
        every { manager.isBiometricAvailable() } returns biometric
        every { manager.isBiometricEnabled() } returns biometric
        every { manager.pinLockoutRemainingMillis() } returns 0L
        every { manager.getSecureFiles() } returns emptyList()
    }

    private fun show(
        viewModel: SecurityViewModel,
        onBack: () -> Unit = {},
    ) {
        composeRule.setContentEsScaled { SecurityScreen(onBack = onBack, viewModel = viewModel) }
        composeRule.waitForIdle()
    }

    private fun tap(vararg digits: String) {
        digits.forEach { digit ->
            composeRule.onNodeWithText(digit).performScrollTo().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun sinPin_ofreceConfigurarYElAsistenteExigeConfirmacionIgual() {
        stubPin(hasPin = false)
        every { manager.setPin(any()) } returns true
        show(buildViewModel())

        composeRule.onNodeWithText(esText(R.string.security_setup_pin_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_setup_pin_desc)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_configure_pin_button)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(esText(R.string.security_create_pin)).assertExists()
        tap("1", "2", "3", "4")
        composeRule.onNodeWithText(esText(R.string.security_confirm_pin)).assertExists()

        // Confirmación distinta: error y se vuelve a pedir la confirmación.
        tap("5", "6", "7", "8")
        composeRule.onNodeWithText(esText(R.string.security_pins_dont_match)).assertExists()
        verify(exactly = 0) { manager.setPin(any()) }

        // Borrar limpia el error; luego la confirmación correcta guarda el PIN y desbloquea.
        composeRule.onNodeWithContentDescription(esText(R.string.security_delete_desc)).performScrollTo().performClick()
        composeRule.onAllNodesWithText(esText(R.string.security_pins_dont_match)).assertCountEquals(0)
        tap("1", "2", "3", "4")
        composeRule.waitForText(esText(R.string.security_no_protected_files))
        verify { manager.setPin("1234") }
    }

    @Test
    fun setPinFalla_muestraElErrorYPermiteReintentar() {
        stubPin(hasPin = false)
        every { manager.setPin(any()) } returns false
        show(buildViewModel())

        composeRule.onNodeWithText(esText(R.string.security_configure_pin_button)).performScrollTo().performClick()
        tap("2", "2", "2", "2", "2", "2", "2", "2")

        composeRule.waitForText(esText(R.string.security_setup_pin_error))
        composeRule.onNodeWithText(esText(R.string.security_setup_pin_error)).assertExists()
        // Sigue en la pantalla de confirmación, sin haber desbloqueado.
        composeRule.onNodeWithText(esText(R.string.security_confirm_pin)).assertExists()
    }

    @Test
    fun atrasEnElAsistente_vuelveALaPantallaBloqueada() {
        stubPin(hasPin = false)
        show(buildViewModel())

        composeRule.onNodeWithText(esText(R.string.security_configure_pin_button)).performScrollTo().performClick()
        composeRule.onNodeWithText(esText(R.string.security_create_pin)).assertExists()
        composeRule.onNodeWithContentDescription(esText(R.string.general_back)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(esText(R.string.security_setup_pin_title)).assertExists()
    }

    @Test
    fun pinCorrecto_desbloqueaYPinIncorrectoMuestraError() {
        stubPin(hasPin = true)
        every { manager.verifyPin("1111") } returns true
        every { manager.verifyPin("9999") } returns false
        show(buildViewModel())

        composeRule.onNodeWithText(esText(R.string.security_enter_pin)).assertExists()
        tap("9", "9", "9", "9")
        composeRule.waitForText(esText(R.string.security_pin_incorrect))

        tap("1", "1", "1", "1")
        composeRule.waitForText(esText(R.string.security_no_protected_files))
        composeRule.onNodeWithText(esText(R.string.security_secure_folder)).assertExists()
    }

    @Test
    fun borrarUnDigito_noCompletaElPin() {
        stubPin(hasPin = true)
        show(buildViewModel())

        tap("1", "2", "3")
        composeRule.onNodeWithContentDescription(esText(R.string.security_delete_desc)).performScrollTo().performClick()
        composeRule.waitForIdle()
        verify(exactly = 0) { manager.verifyPin(any()) }
    }

    @Test
    fun bloqueoPorIntentosFallidos_muestraLosSegundosRestantes() {
        stubPin(hasPin = true)
        every { manager.pinLockoutRemainingMillis() } returns 45_000L
        show(buildViewModel())

        tap("1", "2", "3", "4")
        composeRule.waitForText(esText(R.string.security_pin_locked_out, 45))
        // Durante el bloqueo no se llega a verificar el PIN.
        verify(exactly = 0) { manager.verifyPin(any()) }
    }

    @Test
    fun olvideMiPin_pideConfirmacionYRestablecerLlevaAConfigurar() {
        stubPin(hasPin = true)
        show(buildViewModel())

        composeRule.onNodeWithText(esText(R.string.security_forgot_pin)).performScrollTo().performClick()
        composeRule.onNodeWithText(esText(R.string.security_reset_pin_dialog_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_reset_pin_dialog_body)).assertExists()

        // Cancelar no borra nada.
        composeRule.onNodeWithText(esText(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        verify(exactly = 0) { manager.resetPinAndWipeFiles() }

        composeRule.onNodeWithText(esText(R.string.security_forgot_pin)).performScrollTo().performClick()
        composeRule.onNodeWithText(esText(R.string.security_reset_pin_confirm)).performClick()
        composeRule.waitForText(esText(R.string.security_create_pin))
        verify(exactly = 1) { manager.resetPinAndWipeFiles() }
    }

    @Test
    fun conBiometriaActiva_muestraElBotonYSuToqueNoRompeSinFragmentActivity() {
        stubPin(hasPin = true, biometric = true)
        show(buildViewModel())

        // La Activity de prueba es un ComponentActivity: la pantalla registra el error y no lanza el prompt.
        composeRule.onNodeWithText(esText(R.string.security_use_biometric)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(esText(R.string.security_enter_pin)).assertExists()
    }

    @Test
    fun sinBiometria_noHayBotonDeBiometria() {
        stubPin(hasPin = true, biometric = false)
        show(buildViewModel())

        composeRule.onAllNodesWithText(esText(R.string.security_use_biometric)).assertCountEquals(0)
    }

    @Test
    fun flechaAtrasDeLaPantallaBloqueada_invocaOnBack() {
        stubPin(hasPin = true)
        var backs = 0
        show(buildViewModel(), onBack = { backs++ })

        composeRule.onNodeWithContentDescription(esText(R.string.general_back)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun alPasarASegundoPlano_laCarpetaDesbloqueadaSeVuelveABloquear() {
        stubPin(hasPin = true)
        every { manager.verifyPin("1234") } returns true
        val observer = slot<LifecycleEventObserver>()
        val tracker = mockk<AppLifecycleTracker>(relaxed = true)
        every { tracker.addObserver(capture(observer)) } just Runs
        show(buildViewModel(tracker))

        tap("1", "2", "3", "4")
        composeRule.waitForText(esText(R.string.security_no_protected_files))

        val owner = mockk<LifecycleOwner>(relaxed = true)
        composeRule.runOnUiThread { observer.captured.onStateChanged(owner, Lifecycle.Event.ON_STOP) }
        composeRule.waitForText(esText(R.string.security_enter_pin))
        verify { manager.clearPreviewCache() }
    }
}
