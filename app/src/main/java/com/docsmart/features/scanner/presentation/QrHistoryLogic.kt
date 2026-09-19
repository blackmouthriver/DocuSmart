@file:Suppress("MatchingDeclarationName")

package com.docsmart.features.scanner.presentation

import com.docsmart.features.scanner.domain.parseVCardPayload
import com.docsmart.features.scanner.domain.parseWifiPayload

// Ronda 17: lógica pura del Historial de QR extraída de QrHistoryScreen.kt.
//
// Bug real corregido (privacidad, Media): extractWifiSsid() usaba un regex
// sin anclar (`S:...;`) que la ronda 16 ya había corregido en el parser de
// dominio (parseWifiPayload). Con un payload `WIFI:T:WPA;P:claveS:secreto;S:CASA;;`
// el regex tomaba `secreto` (parte de la CONTRASEÑA) como SSID y lo mostraba en
// texto plano en la fila del Historial. Ahora se reutiliza el parser anclado.
// Igual con `FN:` en vCard (tomaba cualquier `FN:` en medio de otra línea y no
// desplegaba líneas plegadas ni usaba el nombre estructurado N: como respaldo).

/** Qué mostrar en la fila del Historial para una entrada, sin revelar datos sensibles. */
internal sealed interface HistoryPreview {
    /** QR cifrado con contraseña: no se muestra nada del contenido. */
    data object Protected : HistoryPreview

    /** Wi-Fi: solo el SSID (null si no se pudo extraer). Nunca la contraseña. */
    data class Wifi(val ssid: String?) : HistoryPreview

    /** Contacto: solo el nombre (null si no se pudo extraer). Nunca teléfono/email. */
    data class Contact(val name: String?) : HistoryPreview

    /** Cualquier otro contenido se muestra tal cual. */
    data class Plain(val content: String) : HistoryPreview
}

internal fun historyPreviewOf(
    typeName: String,
    content: String,
): HistoryPreview =
    when {
        typeName == "PROTECTED" -> HistoryPreview.Protected
        content.startsWith("WIFI:", ignoreCase = true) -> HistoryPreview.Wifi(extractWifiSsid(content))
        content.startsWith("BEGIN:VCARD", ignoreCase = true) -> HistoryPreview.Contact(extractContactName(content))
        else -> HistoryPreview.Plain(content)
    }

internal fun extractWifiSsid(content: String): String? = parseWifiPayload(content)?.ssid?.takeIf { it.isNotBlank() }

internal fun extractContactName(content: String): String? = parseVCardPayload(content)?.name?.takeIf { it.isNotBlank() }
