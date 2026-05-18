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
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
        )
        applicationScope.launch {
            adManager.initialize()
        }
    }
}