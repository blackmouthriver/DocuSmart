package com.docsmart.core.remoteconfig

import com.docsmart.BuildConfig
import com.docsmart.R
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper de Firebase Remote Config para poder ajustar el paywall de
 * Premium sin publicar una actualización (RF pedido por el usuario
 * 2026-09-04, "configuración de monetización"). Los valores por defecto en
 * `res/xml/remote_config_defaults.xml` coinciden con el comportamiento que
 * la app ya tenía antes de esto -- nada cambia hasta que alguien configure
 * parámetros distintos en la consola de Firebase.
 */
@Singleton
class RemoteConfigManager @Inject constructor() {
    private val remoteConfig = Firebase.remoteConfig.apply {
        setConfigSettingsAsync(
            remoteConfigSettings {
                // En debug, fetch inmediato en cada refresh() para poder probar
                // valores nuevos sin esperar la hora de caché de producción.
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
            }
        )
        setDefaultsAsync(R.xml.remote_config_defaults)
    }

    /** Llamar al iniciar la app -- best-effort, nunca bloquea ni falla la app si no hay red. */
    fun refresh() {
        remoteConfig.fetchAndActivate()
            .addOnFailureListener { e -> Timber.w(e, "RemoteConfigManager: fetchAndActivate falló") }
    }

    fun isAnnualPlanHighlighted(): Boolean = remoteConfig.getBoolean(KEY_ANNUAL_HIGHLIGHTED)

    fun showSavingsBadge(): Boolean = remoteConfig.getBoolean(KEY_SHOW_SAVINGS_BADGE)

    companion object {
        private const val KEY_ANNUAL_HIGHLIGHTED = "premium_annual_highlighted"
        private const val KEY_SHOW_SAVINGS_BADGE = "premium_show_savings_badge"
    }
}
