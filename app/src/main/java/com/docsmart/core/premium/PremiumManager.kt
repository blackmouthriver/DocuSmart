package com.docsmart.core.premium

import android.content.Context
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
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_premium", Context.MODE_PRIVATE
    )

    private val _isPremium = MutableStateFlow(loadPremiumStatus())
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun isFeatureAvailable(feature: PremiumFeature): Boolean {
        return feature.isAvailableFree || _isPremium.value
    }

    // Gate genérico para operaciones con límite diario (Convertidor,
    // Herramientas PDF, escaneos guardados): estaba repetido como
    // `!adManager.isPremium.value && !dailyLimitManager.canX()` (o su
    // inverso) en 3 ViewModels distintos, cada uno leyendo el estado
    // Premium directo de AdManager en vez de a través de este manager.
    fun canPerform(dailyCheck: () -> Boolean): Boolean {
        return _isPremium.value || dailyCheck()
    }

    fun activatePremium() {
        _isPremium.value = true
        savePremiumStatus(true)
        Timber.d("PremiumManager: premium activado")
    }

    fun deactivatePremium() {
        _isPremium.value = false
        savePremiumStatus(false)
        Timber.d("PremiumManager: premium desactivado")
    }

    private fun loadPremiumStatus(): Boolean {
        return prefs.getBoolean("is_premium", false)
    }

    private fun savePremiumStatus(isPremium: Boolean) {
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }
}
