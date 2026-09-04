package com.docsmart

import android.app.Application
import com.docsmart.core.analytics.CrashlyticsTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DocuSmartApplication : Application() {

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
    }
}
