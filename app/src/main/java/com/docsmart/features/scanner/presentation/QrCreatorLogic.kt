package com.docsmart.features.scanner.presentation

import com.docsmart.features.scanner.domain.QrContactContent
import com.docsmart.features.scanner.domain.QrEventContent
import com.docsmart.features.scanner.domain.QrWifiContent
import com.docsmart.features.scanner.domain.QrWifiSecurity
import com.docsmart.features.scanner.domain.buildEmailQrPayload
import com.docsmart.features.scanner.domain.buildPhoneQrPayload
import com.docsmart.features.scanner.domain.buildUrlQrPayload
import com.docsmart.features.scanner.domain.toQrPayload

// Ronda 17: validación y armado del payload del Creador de QR, extraídos de
// QrCreatorScreen (sin Compose ni Android) para poder testearlos en JVM.
// `selectedType` es el índice del chip de tipo en la pantalla.

internal const val QR_TYPE_URL = 0
internal const val QR_TYPE_TEXT = 1
internal const val QR_TYPE_EMAIL = 2
internal const val QR_TYPE_PHONE = 3
internal const val QR_TYPE_IMAGE = 4
internal const val QR_TYPE_DOCUMENT = 5
internal const val QR_TYPE_WIFI = 6
internal const val QR_TYPE_CONTACT = 7
internal const val QR_TYPE_EVENT = 8

internal const val QR_MIN_PASSWORD_LENGTH = 4

/** Campos del formulario que intervienen en la validación/armado del QR. */
internal data class QrCreatorInput(
    val selectedType: Int,
    val content: String = "",
    val selectedUri: String? = null,
    val wifi: QrWifiContent = QrWifiContent("", "", QrWifiSecurity.WPA),
    val contact: QrContactContent = QrContactContent("", "", ""),
    val event: QrEventContent? = null,
)

/** Motivo por el que el formulario todavía no tiene contenido suficiente para generar. */
internal enum class QrContentError {
    SELECT_IMAGE,
    SELECT_DOCUMENT,
    WIFI_INCOMPLETE,
    CONTACT_NAME_REQUIRED,
    EVENT_TITLE_REQUIRED,
    EVENT_END_BEFORE_START,
    EMPTY_CONTENT,
}

/**
 * Error de contenido del formulario o null si ya se puede generar. Wi-Fi exige
 * SSID (y contraseña salvo red abierta), Contacto exige el nombre y Evento exige
 * título y que el fin no sea anterior al inicio.
 */
internal fun qrContentError(input: QrCreatorInput): QrContentError? =
    when (input.selectedType) {
        QR_TYPE_IMAGE -> if (input.selectedUri != null) null else QrContentError.SELECT_IMAGE
        QR_TYPE_DOCUMENT -> if (input.selectedUri != null) null else QrContentError.SELECT_DOCUMENT
        QR_TYPE_WIFI ->
            if (input.wifi.ssid.isNotBlank() &&
                (input.wifi.security == QrWifiSecurity.NONE || input.wifi.password.isNotBlank())
            ) {
                null
            } else {
                QrContentError.WIFI_INCOMPLETE
            }
        QR_TYPE_CONTACT -> if (input.contact.name.isNotBlank()) null else QrContentError.CONTACT_NAME_REQUIRED
        QR_TYPE_EVENT -> eventContentError(input.event)
        else -> if (input.content.isNotBlank()) null else QrContentError.EMPTY_CONTENT
    }

private fun eventContentError(event: QrEventContent?): QrContentError? =
    when {
        event == null || event.title.isBlank() -> QrContentError.EVENT_TITLE_REQUIRED
        event.end.isBefore(event.start) -> QrContentError.EVENT_END_BEFORE_START
        else -> null
    }

/**
 * true si la contraseña opcional del QR no es válida: debe tener al menos 4
 * caracteres y no ser solo espacios.
 *
 * Bug real corregido (Media, privacidad): antes solo se exigía `length >= 4`,
 * pero el cifrado se aplica únicamente si `password.isNotBlank()`. Una
 * contraseña de 4 espacios pasaba la validación, el QR se generaba SIN cifrar
 * y la UI seguía presentándolo como protegido con contraseña.
 */
internal fun isQrPasswordInvalid(
    usePassword: Boolean,
    password: String,
): Boolean = usePassword && (password.isBlank() || password.length < QR_MIN_PASSWORD_LENGTH)

/** Contenido crudo (antes de cifrar) que se codifica en el QR según el tipo elegido. */
internal fun buildQrRawContent(input: QrCreatorInput): String =
    when (input.selectedType) {
        QR_TYPE_IMAGE, QR_TYPE_DOCUMENT -> input.selectedUri.toString()
        QR_TYPE_URL -> buildUrlQrPayload(input.content)
        QR_TYPE_EMAIL -> buildEmailQrPayload(input.content)
        QR_TYPE_PHONE -> buildPhoneQrPayload(input.content)
        QR_TYPE_WIFI -> input.wifi.toQrPayload()
        QR_TYPE_CONTACT -> input.contact.toQrPayload()
        QR_TYPE_EVENT -> input.event?.toQrPayload() ?: input.content
        else -> input.content
    }

/** Nombre del tipo de contenido para el Historial y la analítica. */
internal fun qrCreatedTypeName(selectedType: Int): String =
    when (selectedType) {
        QR_TYPE_URL -> QrContentType.URL.name
        QR_TYPE_EMAIL -> QrContentType.EMAIL.name
        QR_TYPE_PHONE -> QrContentType.PHONE.name
        QR_TYPE_IMAGE -> QrContentType.IMAGE.name
        QR_TYPE_DOCUMENT -> QrContentType.DOCUMENT.name
        QR_TYPE_WIFI -> "WIFI"
        QR_TYPE_CONTACT -> "CONTACT"
        QR_TYPE_EVENT -> "EVENT"
        else -> QrContentType.TEXT.name
    }
