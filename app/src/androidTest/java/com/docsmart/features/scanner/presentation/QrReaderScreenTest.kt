package com.docsmart.features.scanner.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.scanner.domain.QrCrypto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * QrReaderScreen (QrScreen.kt, ronda 20): la parte SIN cámara ni ML Kit. El permiso
 * de cámara se fija en "denegado" con `cameraPermissionChecker` y el registro de
 * resultados es falso, así que nunca se pide un permiso real ni se abre la cámara;
 * el resultado de un QR ya leído y el diálogo de QR protegido se muestran con
 * `initialResult`/`initialProtectedContent`.
 */
class QrReaderScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int): String = forceLocale(targetContext, "es-ES").getString(id)

    private class Shown(
        val registry: FakeResultRegistryOwner = FakeResultRegistryOwner(),
    ) {
        var recording: RecordingContext? = null
        val back = AtomicInteger()
        val history = AtomicInteger()
    }

    private fun buildViewModel(): QrViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        return QrViewModel(adManager = adManager)
    }

    private fun show(
        result: String? = null,
        protectedContent: String? = null,
    ): Shown {
        val shown = Shown()
        val viewModel = buildViewModel()
        composeRule.setContentEsFit(
            overrideContext = { RecordingContext(MemoryPrefsContext(it)).also { ctx -> shown.recording = ctx } },
            registryOwner = shown.registry,
        ) {
            QrReaderScreen(
                onBack = { shown.back.incrementAndGet() },
                onHistoryClick = { shown.history.incrementAndGet() },
                viewModel = viewModel,
                initialResult = result,
                initialProtectedContent = protectedContent,
                cameraPermissionChecker = { false },
            )
        }
        composeRule.waitForIdle()
        return shown
    }

    private fun waitForText(text: String) {
        composeRule.waitUntilOrDump("R20_QrReader", 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Sin permiso de cámara ────────────────────────────────────────────────────

    @Test
    fun sinPermiso_muestraElAvisoYVuelveAPedirloConElRegistroFalso() {
        val shown = show()

        composeRule.onNodeWithText(str(R.string.qr_reader_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_camera_permission_needed)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_allow_camera_access)).performClick()
        composeRule.waitForIdle()

        // Una vez al abrir la pantalla y otra al tocar el botón; ambas se resuelven denegadas.
        assertEquals(listOf("RequestPermission", "RequestPermission"), shown.registry.launched.toList())
        composeRule.onNodeWithText(str(R.string.qr_camera_permission_needed)).assertExists()
    }

    @Test
    fun banner_volverEHistorialInvocanSusCallbacks() {
        val shown = show()

        composeRule.onNodeWithContentDescription(str(R.string.qr_history_title)).performClick()
        composeRule.onNodeWithText(str(R.string.general_back)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, shown.history.get())
        assertEquals(1, shown.back.get())
    }

    // Hallazgo de cobertura (ronda 23): ningún test existente pasaba `onHome`
    // -- la rama "Inicio" de BannerNavRow (DocuSmartTopBanner) quedaba en 0%
    // para esta pantalla.
    @Test
    fun banner_conOnHome_muestraElBotonInicioYLoInvoca() {
        var homeCount = 0
        val viewModel = buildViewModel()
        composeRule.setContentEsFit(registryOwner = FakeResultRegistryOwner()) {
            QrReaderScreen(
                viewModel = viewModel,
                onHome = { homeCount++ },
                cameraPermissionChecker = { false },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.nav_home)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, homeCount)
    }

    // ── Resultado de un QR ya leído ──────────────────────────────────────────────

    @Test
    fun cadaTipoDeResultado_muestraSuEtiquetaYSusAcciones() {
        data class Case(
            val value: String,
            val label: Int,
            val action: Int,
        )

        val cases =
            listOf(
                Case("https://docsmart.app/ayuda", R.string.qr_type_url_detected, R.string.qr_open_browser),
                Case("https://docsmart.app/foto.png", R.string.qr_type_image_detected, R.string.qr_open_image),
                Case(
                    "content://com.otra.app/docs/informe.pdf",
                    R.string.qr_type_document_detected,
                    R.string.qr_open_document,
                ),
                Case("mailto:ana@ejemplo.com", R.string.qr_type_email_detected, R.string.qr_send_email),
                Case("tel:+573001234567", R.string.qr_type_phone_detected, R.string.qr_call),
                Case("Hola DocuSmart", R.string.qr_type_text_detected, R.string.qr_copy_text),
                Case("WIFI:T:WPA;S:MiRed;P:clave1234;;", R.string.qr_type_wifi_detected, R.string.qr_copy_wifi_ssid),
                Case(
                    "BEGIN:VCARD\nVERSION:3.0\nFN:Ana Pérez\nTEL:3001234567\nEND:VCARD",
                    R.string.qr_type_contact_detected,
                    R.string.qr_add_contact,
                ),
                Case(
                    "BEGIN:VEVENT\nSUMMARY:Reunión\nDTSTART:20300101T100000\nDTEND:20300101T110000\nEND:VEVENT",
                    R.string.qr_type_event_detected,
                    R.string.qr_add_calendar_event,
                ),
            )
        var current by mutableStateOf(cases[0].value)
        val viewModel = buildViewModel()
        composeRule.setContentEsFit(registryOwner = FakeResultRegistryOwner()) {
            // key: cada valor arranca una pantalla nueva (el resultado vive en rememberSaveable).
            key(current) {
                QrReaderScreen(
                    viewModel = viewModel,
                    initialResult = current,
                    cameraPermissionChecker = { false },
                )
            }
        }

        cases.forEach { case ->
            composeRule.runOnUiThread { current = case.value }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(str(R.string.qr_detected_title)).assertExists()
            composeRule.onNodeWithText(str(case.label)).assertExists()
            composeRule.onNodeWithText(str(case.action)).performScrollTo().assertExists()
            composeRule.onNodeWithText(str(R.string.qr_scan_another)).performScrollTo().assertExists()
        }
    }

    @Test
    fun resultadoDeImagen_pideConfirmacionAntesDeCargarDeInternet() {
        show(result = "https://docsmart.app/foto.png")

        composeRule.onNodeWithText(str(R.string.qr_image_privacy_warning)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_load_image)).assertExists()
        composeRule.onAllNodesWithText(str(R.string.qr_loading_image)).assertCountEquals(0)
    }

    @Test
    fun resultadoDeUrl_abrirGrabaElIntentYCopiarMuestraLaConfirmacion() {
        val shown = show(result = "https://docsmart.app/ayuda")

        composeRule.onNodeWithText(str(R.string.qr_open_browser)).performScrollTo().performClick()
        composeRule.waitForIdle()
        val view = shown.recording!!.started.first()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://docsmart.app/ayuda", view.data.toString())

        composeRule.onNodeWithText(str(R.string.qr_copy_url)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.qr_copied_clipboard)).assertExists()
    }

    @Test
    fun escanearOtroCodigo_descartaElResultadoYVuelveAlModoDeCaptura() {
        show(result = "Hola DocuSmart")

        composeRule.onNodeWithText("Hola DocuSmart").assertExists()
        composeRule.onNodeWithText(str(R.string.qr_scan_another)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(str(R.string.qr_detected_title)).assertCountEquals(0)
        // Sin permiso concedido la captura muestra el aviso, nunca la cámara real.
        composeRule.onNodeWithText(str(R.string.qr_camera_permission_needed)).assertExists()
    }

    // ── QR protegido con contraseña ──────────────────────────────────────────────

    private fun protectedPayload(): String = QrCrypto.encrypt("https://docsmart.app/secreto", "1234")

    @Test
    fun qrProtegido_claveIncorrectaMuestraErrorYLaCorrectaRevelaElContenido() {
        show(protectedContent = protectedPayload())

        waitForText(str(R.string.qr_protected_title))
        composeRule.onNodeWithText(str(R.string.qr_protected_desc)).assertExists()

        composeRule.onNodeWithText(str(R.string.qr_password_label)).performTextInput("9999")
        composeRule.onNodeWithText(str(R.string.qr_unlock)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.pdf_pw_wrong_password)).assertExists()

        composeRule.onNodeWithContentDescription(str(R.string.password_show)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(str(R.string.password_hide)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_password_label)).performTextReplacement("1234")
        composeRule.onNodeWithText(str(R.string.qr_unlock)).performClick()

        waitForText(str(R.string.qr_detected_title))
        composeRule.onNodeWithText("https://docsmart.app/secreto").assertExists()
        composeRule.onNodeWithText(str(R.string.qr_type_url_detected)).assertExists()
        composeRule.onAllNodesWithText(str(R.string.qr_protected_title)).assertCountEquals(0)
    }

    @Test
    fun qrProtegido_cancelarCierraElDialogoYVuelveALaCaptura() {
        show(protectedContent = protectedPayload())

        waitForText(str(R.string.qr_protected_title))
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(str(R.string.qr_protected_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(str(R.string.qr_detected_title)).assertCountEquals(0)
        val permissionNodes = composeRule.onAllNodesWithText(str(R.string.qr_camera_permission_needed))
        assertTrue(permissionNodes.fetchSemanticsNodes().isNotEmpty())
    }
}
