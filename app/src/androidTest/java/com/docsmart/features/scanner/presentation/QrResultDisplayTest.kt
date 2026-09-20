package com.docsmart.features.scanner.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * QrResultDisplay.kt: etiquetas/íconos por tipo detectado y botones de acción
 * de cada tipo de contenido (URL/imagen/documento/email/teléfono/texto/Wi-Fi/
 * contacto/evento). Los botones que lanzarían una app externa real (navegador,
 * marcador, contactos, calendario) NO se tocan: solo se comprueba que existen;
 * los que copian al portapapeles o muestran un aviso local sí se pulsan.
 */
class QrResultDisplayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = forceLocale(targetContext, "es-ES").getString(id)

    private fun render(
        type: QrContentType,
        content: String,
        onCopied: () -> Unit = {},
    ) {
        composeRule.setContentEs { QrContentActionButtons(qrType = type, content = content, onCopied = onCopied) }
        composeRule.waitForIdle()
    }

    private fun assertExists(id: Int) {
        composeRule.onNodeWithText(str(id)).assertExists()
    }

    private fun assertAbsent(id: Int) {
        assertTrue(composeRule.onAllNodesWithText(str(id)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun etiquetasPorTipo_seResuelvenParaLosNueveTipos() {
        val expected =
            listOf(
                R.string.qr_type_url_detected,
                R.string.qr_type_image_detected,
                R.string.qr_type_document_detected,
                R.string.qr_type_email_detected,
                R.string.qr_type_phone_detected,
                R.string.qr_type_text_detected,
                R.string.qr_type_wifi_detected,
                R.string.qr_type_contact_detected,
                R.string.qr_type_event_detected,
            )
        composeRule.setContentEs {
            Column {
                QrContentType.entries.forEach { type ->
                    val (_, _, label) = qrContentTypeVisuals(type)
                    Text(label)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(QrContentType.entries.size, expected.size)
        expected.forEach { assertExists(it) }
    }

    @Test
    fun url_muestraAbrirYCopiar_yCopiarNotifica() {
        var copied = 0
        render(QrContentType.URL, "https://ejemplo.com", onCopied = { copied++ })

        assertExists(R.string.qr_open_browser)
        composeRule.onNodeWithText(str(R.string.qr_copy_url)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun url_conEsquemaNoAbrible_muestraAvisoSinLanzarNada() {
        render(QrContentType.URL, "ftp://ejemplo.com/archivo")

        // openUrl() rechaza el esquema y solo muestra un Toast local.
        composeRule.onNodeWithText(str(R.string.qr_open_browser)).performClick()
        composeRule.waitForIdle()

        assertExists(R.string.qr_open_browser)
    }

    @Test
    fun imagen_conUriPropia_noLanzaNadaYCopiaElEnlace() {
        var copied = 0
        val own = "content://${targetContext.packageName}.fileprovider/secreto.png"
        render(QrContentType.IMAGE, own, onCopied = { copied++ })

        // Un content:// del propio paquete se rechaza con un Toast (no abre chooser).
        composeRule.onNodeWithText(str(R.string.qr_open_image)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_copy_link)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun documento_conUriPropia_noLanzaNadaYCopiaLaRuta() {
        var copied = 0
        val own = "content://${targetContext.packageName}.fileprovider/informe.pdf"
        render(QrContentType.DOCUMENT, own, onCopied = { copied++ })

        composeRule.onNodeWithText(str(R.string.qr_open_document)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_copy_path)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun email_muestraEnviarYCopiar() {
        var copied = 0
        render(QrContentType.EMAIL, "mailto:a@b.com", onCopied = { copied++ })

        assertExists(R.string.qr_send_email)
        composeRule.onNodeWithText(str(R.string.qr_copy_email)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun telefono_muestraLlamarYCopiar() {
        var copied = 0
        render(QrContentType.PHONE, "tel:+5215555555555", onCopied = { copied++ })

        assertExists(R.string.qr_call)
        composeRule.onNodeWithText(str(R.string.qr_copy_number)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun texto_soloOfreceCopiar() {
        var copied = 0
        render(QrContentType.TEXT, "Hola DocuSmart", onCopied = { copied++ })

        composeRule.onNodeWithText(str(R.string.qr_copy_text)).performClick()

        assertEquals(1, copied)
        assertAbsent(R.string.qr_open_browser)
    }

    @Test
    fun wifi_conClave_ofreceCopiarClaveYRed() {
        var copied = 0
        render(QrContentType.WIFI, "WIFI:T:WPA;S:Casa;P:secreto;;", onCopied = { copied++ })

        composeRule.onNodeWithText(str(R.string.qr_copy_wifi_password)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_copy_wifi_ssid)).performClick()

        assertEquals(2, copied)
    }

    @Test
    fun wifi_abierta_noOfreceCopiarClave() {
        render(QrContentType.WIFI, "WIFI:T:nopass;S:Casa;;")

        assertExists(R.string.qr_copy_wifi_ssid)
        assertAbsent(R.string.qr_copy_wifi_password)
    }

    @Test
    fun wifi_ilegible_copiaElContenidoCrudo() {
        var copied = 0
        render(QrContentType.WIFI, "WIFI:basura", onCopied = { copied++ })

        composeRule.onNodeWithText(str(R.string.qr_copy_wifi_ssid)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun contacto_valido_habilitaAgregarYCopia() {
        var copied = 0
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:Ana Perez\nTEL:555\nEND:VCARD"
        render(QrContentType.CONTACT, vcard, onCopied = { copied++ })

        // No se pulsa "Agregar": abriría la app real de contactos.
        composeRule.onNodeWithText(str(R.string.qr_add_contact)).assertIsEnabled()
        composeRule.onNodeWithText(str(R.string.qr_copy_text)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun contacto_ilegible_deshabilitaAgregar() {
        render(QrContentType.CONTACT, "esto no es una vCard")

        composeRule.onNodeWithText(str(R.string.qr_add_contact)).assertIsNotEnabled()
    }

    @Test
    fun evento_valido_habilitaAgregarYCopia() {
        var copied = 0
        val vevent = "BEGIN:VEVENT\nSUMMARY:Reunion\nDTSTART:20260920T100000\nDTEND:20260920T110000\nEND:VEVENT"
        render(QrContentType.EVENT, vevent, onCopied = { copied++ })

        composeRule.onNodeWithText(str(R.string.qr_add_calendar_event)).assertIsEnabled()
        composeRule.onNodeWithText(str(R.string.qr_copy_text)).performClick()

        assertEquals(1, copied)
    }

    @Test
    fun evento_ilegible_deshabilitaAgregar() {
        render(QrContentType.EVENT, "no es un evento")

        composeRule.onNodeWithText(str(R.string.qr_add_calendar_event)).assertIsNotEnabled()
    }
}
