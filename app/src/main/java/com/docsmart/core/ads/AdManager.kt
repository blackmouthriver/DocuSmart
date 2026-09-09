package com.docsmart.core.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.docsmart.core.premium.PremiumManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val premiumManager: PremiumManager
) {
    private var interstitialAd : InterstitialAd? = null
    private var rewardedAd     : RewardedAd?     = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Google Ads exige que InterstitialAd.load()/RewardedAd.load() se llamen
    // desde el hilo principal. AdManager es un Singleton al que se llega
    // desde contextos muy distintos -- algunos ya en Main (UI), otros en
    // Dispatchers.IO (p.ej. BillingManager.restorePurchases() al conectar
    // con Play Billing) -- y no es responsabilidad de cada llamador saber
    // esto. Crash real reproducido: "IllegalStateException: #008 Must be
    // called on the main UI thread" al entrar a Premium por primera vez,
    // porque BillingManager.restorePurchases() corre en IO y termina
    // desactivando Premium -> loadInterstitial() (hoy disparado por el
    // collector de premiumManager.isPremium, ver el init{} más abajo).
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post(action)
    }

    private val conversionCount       = AtomicInteger(0)
    private val lastInterstitialTime  = AtomicLong(0L)

    // Doble fuente de verdad corregida 2026-09-09: acá vivía un
    // MutableStateFlow propio, sincronizado a mano desde PremiumManager
    // (único que llamaba a setPremium()) cada vez que el estado Premium
    // cambiaba -- dos flags separados que solo coincidían porque nada más
    // escribía en este. Ahora hay un solo StateFlow real (el de
    // PremiumManager, respaldado en SharedPreferences); este es un simple
    // passthrough, así que ya no puede desincronizarse.
    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    private val _isInitialized  = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // El valor inicial de premiumManager.isPremium ya es correcto sin
        // ningún paso de sincronización (se carga de SharedPreferences en
        // el propio constructor de PremiumManager) -- drop(1) evita repetir
        // acá ese primer valor y solo reacciona a cambios reales durante la
        // sesión (compra o restauración de Play Billing).
        scope.launch {
            isPremium.drop(1).collect { premium ->
                if (premium) {
                    interstitialAd         = null
                    rewardedAd             = null
                    _isRewardedReady.value = false
                } else {
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }
    }

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
        if (isPremium.value) return
        runOnMainThread {
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
    }

    fun onConversionCompleted(activity: Activity) {
        if (isPremium.value) return
        val count        = conversionCount.incrementAndGet()
        val now          = System.currentTimeMillis()
        val timeSinceLast = now - lastInterstitialTime.get()
        val shouldShow   = shouldShowInterstitial(count, timeSinceLast)

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
        if (isPremium.value) return
        runOnMainThread {
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
        // Defensivo (2026-09-07): loadInterstitial/loadRewarded/
        // onConversionCompleted ya se niegan a actuar si isPremium es
        // true -- a esta función le faltaba el mismo guard explícito.
        // Hasta ahora quedaba a salvo solo porque el cambio a premium anula
        // `rewardedAd`, pero si algún llamador futuro ofreciera "ver
        // anuncio" sin revisar antes isPremium, un usuario Premium podría
        // llegar a ver un rewarded si quedara alguno cacheado.
        if (isPremium.value) {
            onFailed()
            return
        }
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
}

/**
 * Extraída de `onConversionCompleted()` para poder testearla sin construir
 * `AdManager` (su constructor evalúa `Handler(Looper.getMainLooper())`, que
 * revienta fuera de un runtime Android real -- ver `AdManagerTest`).
 */
internal fun shouldShowInterstitial(conversionCount: Int, timeSinceLastMs: Long): Boolean =
    conversionCount >= AdConstants.INTERSTITIAL_MIN_CONVERSIONS &&
        timeSinceLastMs >= AdConstants.INTERSTITIAL_MIN_INTERVAL_MS