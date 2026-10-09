package com.docsmart.features.premium.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.docsmart.core.ui.util.findActivity

// Centro de suscripciones de Google Play: desde ahí el usuario cancela, cambia de plan o ve la
// próxima fecha de cobro. Se usa el enlace general (no el de un producto concreto con `?sku=`)
// porque hay dos productos (mensual y anual) y la pantalla no sabe cuál tiene activo el usuario.
internal const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * Abre el centro de suscripciones de Play. Devuelve `false` si el dispositivo no tiene ninguna app
 * capaz de abrir el enlace (ni Play Store ni navegador), para que la pantalla avise en vez de fallar.
 */
internal fun openSubscriptionCenter(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS_URL))
    // Desde un contexto que no es una Activity (p. ej. el envuelto por el cambio de idioma)
    // startActivity exige FLAG_ACTIVITY_NEW_TASK.
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
