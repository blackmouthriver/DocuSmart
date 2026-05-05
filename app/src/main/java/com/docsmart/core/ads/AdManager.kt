package com.docsmart.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interstitialAd: InterstitialAd? = null

    // ── Fix Sentinel MEDIUM: AtomicInteger/AtomicLong ─────
    // Variables accesibles desde múltiples hilos (IO + Main)
    // AtomicInteger/AtomicLong garantizan operaciones thread-safe
    // sin necesidad de sincronización manual con synchronized{}
    private val conversionCount = AtomicInteger(0)
    private val lastInterstitialTime = AtomicLong(0L)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun initialize() {
        Timber.d("AdManager: iniciando MobileAds")
        MobileAds.initialize(context) {
            Timber.d("AdManager: MobileAds inicializado ✅")
            _isInitialized.value = true
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (_isPremium.value) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConstants.INTERSTITIAL_CONVERSION_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.e("Interstitial error: ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    fun onConversionCompleted(activity: Activity) {
        if (_isPremium.value) return

        // ── incrementAndGet() es atómico — thread-safe ────
        val count = conversionCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastInterstitialTime.get()

        val shouldShow =
            count >= AdConstants.INTERSTITIAL_MIN_CONVERSIONS &&
                    timeSinceLast >= AdConstants.INTERSTITIAL_MIN_INTERVAL_MS

        if (shouldShow && interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        // ── reset atómico ──────────────────
                        conversionCount.set(0)
                        lastInterstitialTime.set(System.currentTimeMillis())
                        loadInterstitial()
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Timber.e("Interstitial show error: ${error.message}")
                        interstitialAd = null
                        loadInterstitial()
                    }
                }
            interstitialAd?.show(activity)
        }
    }

    fun getAdRequest(): AdRequest = AdRequest.Builder().build()

    fun setPremium(isPremium: Boolean) {
        _isPremium.value = isPremium
        if (isPremium) {
            interstitialAd = null
        } else {
            loadInterstitial()
        }
    }
}