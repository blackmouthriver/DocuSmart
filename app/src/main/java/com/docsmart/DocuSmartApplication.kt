package com.docsmart

import android.app.Application
import com.docsmart.core.analytics.CrashlyticsTree
import com.docsmart.core.remoteconfig.RemoteConfigManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DocuSmartApplication : Application() {

    @Inject lateinit var remoteConfigManager: RemoteConfigManager

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // En debug los Timber.e/Timber.w ya se ven en Logcat -- en
            // release, sin ningún árbol plantado, se perdían por completo
            // (bug real corregido 2026-09-03, encontrado al arreglar la
            // integración de Firebase: ningún error de producción llegaba
            // a ningún lado antes de esto).
            Timber.plant(CrashlyticsTree())
        }

        remoteConfigManager.refresh()
    }
}
