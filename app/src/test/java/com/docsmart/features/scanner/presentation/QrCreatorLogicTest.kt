package com.docsmart.features.scanner.presentation

import com.docsmart.features.scanner.domain.QrContactContent
import com.docsmart.features.scanner.domain.QrEventContent
import com.docsmart.features.scanner.domain.QrWifiContent
import com.docsmart.features.scanner.domain.QrWifiSecurity
import com.docsmart.features.scanner.domain.toQrPayload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class QrCreatorLogicTest {
    private val start = LocalDateTime.of(2026, 9, 19, 10, 0)

    private fun event(
        title: String = "Reunion",
        end: LocalDateTime = start.plusHours(1),
    ) = QrEventContent(title, "Oficina", start, end)

    @Test
    fun `texto vacio o en blanco no tiene contenido`() {
        qrContentError(QrCreatorInput(QR_TYPE_TEXT, content = "")) shouldBe QrContentError.EMPTY_CONTENT
        qrContentError(QrCreatorInput(QR_TYPE_URL, content = "   ")) shouldBe QrContentError.EMPTY_CONTENT
        qrContentError(QrCreatorInput(QR_TYPE_EMAIL, content = "")) shouldBe QrContentError.EMPTY_CONTENT
        qrContentError(QrCreatorInput(QR_TYPE_PHONE, content = "")) shouldBe QrContentError.EMPTY_CONTENT
    }

    @Test
    fun `texto con contenido es valido`() {
        qrContentError(QrCreatorInput(QR_TYPE_TEXT, content = "hola")) shouldBe null
    }

    @Test
    fun `imagen y documento exigen un archivo elegido`() {
        qrContentError(QrCreatorInput(QR_TYPE_IMAGE)) shouldBe QrContentError.SELECT_IMAGE
        qrContentError(QrCreatorInput(QR_TYPE_DOCUMENT)) shouldBe QrContentError.SELECT_DOCUMENT
        qrContentError(QrCreatorInput(QR_TYPE_IMAGE, selectedUri = "content://x")) shouldBe null
        qrContentError(QrCreatorInput(QR_TYPE_DOCUMENT, selectedUri = "content://x")) shouldBe null
    }

    @Test
    fun `wifi exige SSID y contrasena salvo red abierta`() {
        fun wifi(
            ssid: String,
            pass: String,
            security: QrWifiSecurity,
        ) = QrCreatorInput(QR_TYPE_WIFI, wifi = QrWifiContent(ssid, pass, security))

        qrContentError(wifi("", "x", QrWifiSecurity.WPA)) shouldBe QrContentError.WIFI_INCOMPLETE
        qrContentError(wifi("Casa", "", QrWifiSecurity.WPA)) shouldBe QrContentError.WIFI_INCOMPLETE
        qrContentError(wifi("Casa", "  ", QrWifiSecurity.WEP)) shouldBe QrContentError.WIFI_INCOMPLETE
        qrContentError(wifi("Casa", "clave", QrWifiSecurity.WPA)) shouldBe null
        qrContentError(wifi("Casa", "", QrWifiSecurity.NONE)) shouldBe null
        qrContentError(wifi("  ", "", QrWifiSecurity.NONE)) shouldBe QrContentError.WIFI_INCOMPLETE
    }

    @Test
    fun `contacto exige nombre`() {
        val sinNombre = QrCreatorInput(QR_TYPE_CONTACT, contact = QrContactContent("", "300", "a@b.c"))
        val conNombre = QrCreatorInput(QR_TYPE_CONTACT, contact = QrContactContent("Ana", "", ""))

        qrContentError(sinNombre) shouldBe QrContentError.CONTACT_NAME_REQUIRED
        qrContentError(conNombre) shouldBe null
    }

    @Test
    fun `evento exige titulo y fin no anterior al inicio`() {
        qrContentError(QrCreatorInput(QR_TYPE_EVENT, event = event(title = " "))) shouldBe
            QrContentError.EVENT_TITLE_REQUIRED
        qrContentError(QrCreatorInput(QR_TYPE_EVENT, event = event(end = start.minusMinutes(1)))) shouldBe
            QrContentError.EVENT_END_BEFORE_START
        qrContentError(QrCreatorInput(QR_TYPE_EVENT, event = event(end = start))) shouldBe null
        qrContentError(QrCreatorInput(QR_TYPE_EVENT, event = event())) shouldBe null
        qrContentError(QrCreatorInput(QR_TYPE_EVENT, event = null)) shouldBe QrContentError.EVENT_TITLE_REQUIRED
    }

    @Test
    fun `evento con titulo vacio y fin anterior reporta primero el titulo`() {
        val input = QrCreatorInput(QR_TYPE_EVENT, event = event(title = "", end = start.minusHours(1)))

        qrContentError(input) shouldBe QrContentError.EVENT_TITLE_REQUIRED
    }

    @Test
    fun `sin contrasena activada nunca es invalida`() {
        isQrPasswordInvalid(false, "") shouldBe false
        isQrPasswordInvalid(false, "a") shouldBe false
    }

    @Test
    fun `contrasena corta es invalida`() {
        isQrPasswordInvalid(true, "") shouldBe true
        isQrPasswordInvalid(true, "abc") shouldBe true
        isQrPasswordInvalid(true, "abcd") shouldBe false
    }

    @Test
    fun `contrasena de solo espacios es invalida aunque tenga 4 o mas caracteres`() {
        // Regresion: pasaba la validacion pero el QR se generaba sin cifrar.
        isQrPasswordInvalid(true, "    ") shouldBe true
        isQrPasswordInvalid(true, "\t\t\t\t\t") shouldBe true
    }

    @Test
    fun `buildQrRawContent arma el payload segun el tipo`() {
        buildQrRawContent(QrCreatorInput(QR_TYPE_URL, content = "ejemplo.com")) shouldBe "https://ejemplo.com"
        buildQrRawContent(QrCreatorInput(QR_TYPE_URL, content = "HTTP://ejemplo.com")) shouldBe "HTTP://ejemplo.com"
        buildQrRawContent(QrCreatorInput(QR_TYPE_EMAIL, content = "a@b.co")) shouldBe "mailto:a@b.co"
        buildQrRawContent(QrCreatorInput(QR_TYPE_PHONE, content = "+57 (300) 123")) shouldBe "tel:+57300123"
        buildQrRawContent(QrCreatorInput(QR_TYPE_TEXT, content = "hola  mundo")) shouldBe "hola  mundo"
    }

    @Test
    fun `buildQrRawContent de imagen y documento usa la URI`() {
        buildQrRawContent(QrCreatorInput(QR_TYPE_IMAGE, selectedUri = "content://foto")) shouldBe "content://foto"
        buildQrRawContent(QrCreatorInput(QR_TYPE_DOCUMENT, selectedUri = "content://doc")) shouldBe "content://doc"
    }

    @Test
    fun `buildQrRawContent de wifi contacto y evento delega en toQrPayload`() {
        val wifi = QrWifiContent("Casa", "clave", QrWifiSecurity.WPA)
        val contact = QrContactContent("Ana", "300", "a@b.co")
        val ev = event()

        buildQrRawContent(QrCreatorInput(QR_TYPE_WIFI, wifi = wifi)) shouldBe wifi.toQrPayload()
        buildQrRawContent(QrCreatorInput(QR_TYPE_CONTACT, contact = contact)) shouldBe contact.toQrPayload()
        buildQrRawContent(QrCreatorInput(QR_TYPE_EVENT, event = ev)) shouldBe ev.toQrPayload()
    }

    @Test
    fun `qrCreatedTypeName mapea cada indice`() {
        qrCreatedTypeName(QR_TYPE_URL) shouldBe "URL"
        qrCreatedTypeName(QR_TYPE_TEXT) shouldBe "TEXT"
        qrCreatedTypeName(QR_TYPE_EMAIL) shouldBe "EMAIL"
        qrCreatedTypeName(QR_TYPE_PHONE) shouldBe "PHONE"
        qrCreatedTypeName(QR_TYPE_IMAGE) shouldBe "IMAGE"
        qrCreatedTypeName(QR_TYPE_DOCUMENT) shouldBe "DOCUMENT"
        qrCreatedTypeName(QR_TYPE_WIFI) shouldBe "WIFI"
        qrCreatedTypeName(QR_TYPE_CONTACT) shouldBe "CONTACT"
        qrCreatedTypeName(QR_TYPE_EVENT) shouldBe "EVENT"
        qrCreatedTypeName(99) shouldBe "TEXT"
    }
}
