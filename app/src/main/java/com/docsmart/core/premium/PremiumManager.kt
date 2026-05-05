package com.docsmart.core.premium

import android.content.Context
import com.docsmart.core.ads.AdManager
import com.docsmart.features.premium.domain.model.PremiumFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adManager: AdManager
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_premium", Context.MODE_PRIVATE
    )

    private val _isPremium = MutableStateFlow(loadPremiumStatus())
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun isFeatureAvailable(feature: PremiumFeature): Boolean {
        return feature.isAvailableFree || _isPremium.value
    }

    fun activatePremium() {
        _isPremium.value = true
        savePremiumStatus(true)
        adManager.setPremium(true)
        Timber.d("PremiumManager: premium activado")
    }

    fun deactivatePremium() {
        _isPremium.value = false
        savePremiumStatus(false)
        adManager.setPremium(false)
        Timber.d("PremiumManager: premium desactivado")
    }

    fun simulatePurchase(planId: String): Boolean {
        return try {
            Timber.d("PremiumManager: simulando compra — planId=$planId")
            activatePremium()
            Timber.d("PremiumManager: compra simulada exitosa")
            true
        } catch (e: Exception) {
            // ── Fix Sentinel MEDIUM: exception sin logging ─────
            // Antes: el catch silenciaba el error completamente
            // Ahora: Timber registra el error para debugging
            Timber.e(e, "PremiumManager: error en simulatePurchase — ${e.message}")
            false
        }
    }

    private fun loadPremiumStatus(): Boolean {
        return prefs.getBoolean("is_premium", false)
    }

    private fun savePremiumStatus(isPremium: Boolean) {
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }
}