package com.docsmart.features.scanner.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.scanner.domain.QrWifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * QrContentForms.kt: formularios de Wi-Fi, Contacto y Evento del Creador de QR.
 * Se renderizan con estado propio (igual que QrCreatorScreen) y se comprueba
 * que escribir/tocar invoca el callback correcto y que la UI refleja el estado.
 */
class QrContentFormsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(id)

    private fun assertAbsent(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    // ── Wi-Fi ────────────────────────────────────────────────────────────

    @Test
    fun wifi_escribirSsidYClave_actualizaElEstado() {
        var ssid by mutableStateOf("")
        var password by mutableStateOf("")
        composeRule.setContentEs {
            QrWifiForm(
                ssid = ssid,
                onSsidChange = { ssid = it },
                password = password,
                onPasswordChange = { password = it },
                showPassword = true,
                onShowPasswordToggle = {},
                security = QrWifiSecurity.WPA,
                onSecurityChange = {},
            )
        }

        composeRule.onNodeWithText(str(R.string.qr_label_wifi_ssid)).performTextInput("Casa")
        composeRule.onNodeWithText(str(R.string.qr_label_wifi_password)).performTextInput("secreto")
        composeRule.waitForIdle()

        assertEquals("Casa", ssid)
        assertEquals("secreto", password)
    }

    @Test
    fun wifi_cambiarSeguridad_ocultaLaClaveSiEsAbierta() {
        var security by mutableStateOf(QrWifiSecurity.WPA)
        composeRule.setContentEs {
            QrWifiForm(
                ssid = "",
                onSsidChange = {},
                password = "",
                onPasswordChange = {},
                showPassword = false,
                onShowPasswordToggle = {},
                security = security,
                onSecurityChange = { security = it },
            )
        }
        composeRule.onNodeWithText(str(R.string.qr_label_wifi_password)).assertExists()

        composeRule.onNodeWithText(str(R.string.qr_wifi_security_wep)).performClick()
        composeRule.waitForIdle()
        assertEquals(QrWifiSecurity.WEP, security)
        composeRule.onNodeWithText(str(R.string.qr_label_wifi_password)).assertExists()

        composeRule.onNodeWithText(str(R.string.qr_wifi_security_none)).performClick()
        composeRule.waitForIdle()
        assertEquals(QrWifiSecurity.NONE, security)
        assertAbsent(str(R.string.qr_label_wifi_password))
    }

    @Test
    fun wifi_botonMostrarClave_invocaElToggle() {
        var toggled = 0
        composeRule.setContentEs {
            QrWifiForm(
                ssid = "",
                onSsidChange = {},
                password = "abc",
                onPasswordChange = {},
                showPassword = false,
                onShowPasswordToggle = { toggled++ },
                security = QrWifiSecurity.WPA,
                onSecurityChange = {},
            )
        }

        // El ojo de la clave no tiene texto ni descripción: es el último clickable del formulario.
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).onLast().performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun wifi_conClaveVisible_muestraElTextoSinEnmascarar() {
        composeRule.setContentEs {
            QrWifiForm(
                ssid = "",
                onSsidChange = {},
                password = "visible123",
                onPasswordChange = {},
                showPassword = true,
                onShowPasswordToggle = {},
                security = QrWifiSecurity.WPA,
                onSecurityChange = {},
            )
        }

        composeRule.onNodeWithText("visible123").assertExists()
    }

    // ── Contacto ─────────────────────────────────────────────────────────

    @Test
    fun contacto_escribirLosTresCampos_actualizaElEstado() {
        var name by mutableStateOf("")
        var phone by mutableStateOf("")
        var email by mutableStateOf("")
        composeRule.setContentEs {
            QrContactForm(
                name = name,
                onNameChange = { name = it },
                phone = phone,
                onPhoneChange = { phone = it },
                email = email,
                onEmailChange = { email = it },
            )
        }

        composeRule.onNodeWithText(str(R.string.qr_label_contact_name)).performTextInput("Ana")
        composeRule.onNodeWithText(str(R.string.qr_label_contact_phone)).performTextInput("555123")
        composeRule.onNodeWithText(str(R.string.qr_label_contact_email)).performTextInput("ana@ejemplo.com")
        composeRule.waitForIdle()

        assertEquals("Ana", name)
        assertEquals("555123", phone)
        assertEquals("ana@ejemplo.com", email)
    }

    // ── Evento ───────────────────────────────────────────────────────────

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val start = LocalDateTime.of(2026, 9, 20, 10, 0)
    private val end = LocalDateTime.of(2026, 9, 21, 11, 30)

    private class EventState(
        start: LocalDateTime,
        end: LocalDateTime,
    ) {
        var title by mutableStateOf("")
        var location by mutableStateOf("")
        var start by mutableStateOf(start)
        var end by mutableStateOf(end)
    }

    private fun renderEvent(state: EventState) {
        composeRule.setContentEs {
            QrEventForm(
                title = state.title,
                onTitleChange = { state.title = it },
                location = state.location,
                onLocationChange = { state.location = it },
                start = state.start,
                onStartChange = { state.start = it },
                end = state.end,
                onEndChange = { state.end = it },
            )
        }
    }

    @Test
    fun evento_escribirTituloYLugar_yMuestraFechasYHoras() {
        val state = EventState(start, end)
        renderEvent(state)

        composeRule.onNodeWithText(str(R.string.qr_label_event_title)).performTextInput("Reunión")
        composeRule.onNodeWithText(str(R.string.qr_label_event_location)).performTextInput("Oficina")
        composeRule.waitForIdle()

        assertEquals("Reunión", state.title)
        assertEquals("Oficina", state.location)
        composeRule.onNodeWithText(start.format(dateFormat)).assertExists()
        composeRule.onNodeWithText(end.format(dateFormat)).assertExists()
        composeRule.onNodeWithText(start.format(timeFormat)).assertExists()
        composeRule.onNodeWithText(end.format(timeFormat)).assertExists()
    }

    @Test
    fun evento_selectorDeFecha_aceptarConservaLaFechaYCancelarCierra() {
        val state = EventState(start, end)
        renderEvent(state)

        // Cancelar: el diálogo se cierra sin cambiar nada.
        composeRule.onNodeWithText(start.format(dateFormat)).performClick()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.general_accept))
        assertEquals(start, state.start)

        // Aceptar: sin tocar el calendario, la fecha elegida es la inicial.
        composeRule.onNodeWithText(start.format(dateFormat)).performClick()
        composeRule.onNodeWithText(str(R.string.general_accept)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.general_accept))
        assertEquals(start, state.start)
    }

    @Test
    fun evento_selectorDeHora_aceptarConservaLaHoraYCancelarCierra() {
        val state = EventState(start, end)
        renderEvent(state)

        composeRule.onNodeWithText(end.format(timeFormat)).performClick()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.general_accept))
        assertEquals(end, state.end)

        composeRule.onNodeWithText(end.format(timeFormat)).performClick()
        composeRule.onNodeWithText(str(R.string.general_accept)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.general_accept))
        assertEquals(end, state.end)
    }
}
