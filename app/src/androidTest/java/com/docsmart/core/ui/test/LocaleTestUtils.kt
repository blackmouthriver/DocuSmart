package com.docsmart.core.ui.test

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Fuerza un locale sobre un [Context], devolviendo uno nuevo con esa
 * configuración -- para que `stringResource()` dentro de una prueba de
 * Compose UI resuelva siempre al mismo idioma sin importar el locale real
 * del dispositivo/emulador donde corra. Necesario porque el emulador de CI
 * arranca en inglés por defecto (ver docs/requirements/deployment.md §3):
 * cambiar el locale del sistema operativo entero por `adb` resultó frágil
 * (`settings put system system_locales` no toma efecto sin un broadcast
 * privilegiado que `shell` no puede enviar; `cmd locale set-app-locales`
 * sí funciona, pero AGP reinstala/desinstala la app entre corridas de
 * `connectedDebugAndroidTest`, borrando el override). Fijarlo en el propio
 * proceso de la app bajo prueba es inmune a todo eso.
 */
fun forceLocale(context: Context, languageTag: String): Context {
    val locale = Locale.forLanguageTag(languageTag)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}
