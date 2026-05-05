package com.docsmart

import android.app.Application
import com.docsmart.core.ads.AdManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import timber.log.Timber

@HiltAndroidApp
class DocuSmartApplication : Application() {

    @Inject
    lateinit var adManager: AdManager

    // ── Scope vinculado al ciclo de vida de la app ────
    // SupervisorJob permite que un hijo falle sin cancelar
    // los demás — fix del issue de Sentinel performance
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()

        // ── Inicializar Timber ────────────────────────
        // Fix Sentinel: reemplaza android.util.Log
        // Solo loguea en debug — en release no hace nada
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // ── Configura dispositivos de prueba ──────────
        // Fix Sentinel LOW: mover a BuildConfig en producción
        val testDeviceIds = if (BuildConfig.DEBUG) {
            listOf("EB3ECF44CF3E05437B137D30F852213B")
        } else {
            emptyList()
        }
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        // ── Inicializar AdMob en scope de aplicación ──
        applicationScope.launch {
            adManager.initialize()
        }
    }
}