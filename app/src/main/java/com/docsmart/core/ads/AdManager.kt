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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
    private var interstitialAd : InterstitialAd? = null
    private var rewardedAd     : RewardedAd?     = null

    private val conversionCount       = AtomicInteger(0)
    private val lastInterstitialTime  = AtomicLong(0L)

    private val _isPremium      = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isInitialized  = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    fun initialize() {
        Timber.d("AdManager: iniciando MobileAds")
        MobileAds.initialize(context) {
            Timber.d("AdManager: MobileAds inicializado ✅")
            _isInitialized.value = true
            loadInterstitial()
            loadRewarded()
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────────
    private fun loadInterstitial() {
        if (_isPremium.value) return
        InterstitialAd.load(
            context,
            AdConstants.INTERSTITIAL_CONVERSION_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Timber.d("AdManager: Interstitial cargado")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.e("AdManager: Interstitial error — ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    fun onConversionCompleted(activity: Activity) {
        if (_isPremium.value) return
        val count        = conversionCount.incrementAndGet()
        val now          = System.currentTimeMillis()
        val timeSinceLast = now - lastInterstitialTime.get()
        val shouldShow   = count >= AdConstants.INTERSTITIAL_MIN_CONVERSIONS &&
                timeSinceLast >= AdConstants.INTERSTITIAL_MIN_INTERVAL_MS

        if (shouldShow && interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
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

    // ── Rewarded Ad ───────────────────────────────────────────────────────────
    private fun loadRewarded() {
        if (_isPremium.value) return
        RewardedAd.load(
            context,
            AdConstants.REWARDED_UNLOCK_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd           = ad
                    _isRewardedReady.value = true
                    Timber.d("AdManager: Rewarded cargado ✅")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.e("AdManager: Rewarded error — ${error.message}")
                    rewardedAd             = null
                    _isRewardedReady.value = false
                }
            }
        )
    }

    /**
     * Muestra un Rewarded Ad.
     * onRewarded: se llama cuando el usuario completa el anuncio → desbloquear acción
     * onFailed: se llama si no hay anuncio disponible o falla
     */
    fun showRewardedAd(
        activity  : Activity,
        onRewarded: () -> Unit,
        onFailed  : () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Timber.w("AdManager: no hay Rewarded disponible")
            onFailed()
            loadRewarded()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd             = null
                _isRewardedReady.value = false
                loadRewarded() // precargar el siguiente
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Timber.e("Rewarded show error: ${error.message}")
                rewardedAd             = null
                _isRewardedReady.value = false
                onFailed()
                loadRewarded()
            }
        }

        ad.show(activity) { _ ->
            // RewardItem — el usuario completó el anuncio
            Timber.d("AdManager: Rewarded completado → otorgando recompensa")
            onRewarded()
        }
    }

    fun getAdRequest(): AdRequest = AdRequest.Builder().build()

    fun setPremium(isPremium: Boolean) {
        _isPremium.value = isPremium
        if (isPremium) {
            interstitialAd         = null
            rewardedAd             = null
            _isRewardedReady.value = false
        } else {
            loadInterstitial()
            loadRewarded()
        }
    }
}