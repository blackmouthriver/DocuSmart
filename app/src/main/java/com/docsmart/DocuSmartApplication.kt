package com.docsmart

import android.app.Application
import com.docsmart.core.ads.AdManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DocuSmartApplication : Application() {

    @Inject
    lateinit var adManager: AdManager

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val testDeviceIds = if (BuildConfig.DEBUG) {
            listOf("EB3ECF44CF3E05437B137D30F852213B")
        } else {
            emptyList()
        }
        // AdManager.initialize() carga un interstitial Y un video recompensado
        // de inmediato (hallazgo ya señalado en sentinel_report.json: "Ad
        // loading is triggered immediately upon initialization"). Bajo
        // instrumentación (connectedAndroidTest), eso hace que cualquier
        // prueba de UI dispare una carga de anuncio real e inicialice el
        // decoder de video del emulador — encontrado porque crasheaba el
        // proceso durante ViewerScreenTest.
        // ActivityManager.isRunningInUserTestHarness() NO sirve para esto —
        // Google documenta explícitamente que es solo para Test Harness Mode
        // (Firebase Test Lab), no para connectedAndroidTest normal. Se
        // detecta en cambio si Espresso está en el classpath: solo ocurre
        // cuando el APK de androidTest se mezcla al correr instrumentado.
        if (isRunningUnderInstrumentation()) {
            Timber.d("DocuSmartApplication: corriendo bajo test, se omite inicialización de anuncios")
            return
        }

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
        )
        applicationScope.launch {
            adManager.initialize()
        }
    }

    private fun isRunningUnderInstrumentation(): Boolean =
        try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            // Esperado en producción: Espresso solo está en el classpath
            // cuando el APK de androidTest se mezcla al correr instrumentado.
            false
        }
}