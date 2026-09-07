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

    // Bug real corregido 2026-09-07: AdManager.isPremium (lo que leen el
    // banner y las 7 pantallas, y lo que bloquea intersticial/rewarded)
    // arrancaba siempre en `false` y solo se ponía al día cuando el
    // usuario abría la pantalla Premium (único lugar que construye este
    // PremiumManager) -- un suscriptor real que no visitaba esa pantalla
    // seguía viendo anuncios toda la sesión pese a tener el estado
    // correcto ya guardado en SharedPreferences. Se sincroniza acá mismo,
    // al construirse, y DocuSmartApplication fuerza esta construcción
    // desde el arranque de la app (ver DocuSmartApplication.kt).
    init {
        adManager.setPremium(_isPremium.value)
    }

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

    private fun loadPremiumStatus(): Boolean {
        return prefs.getBoolean("is_premium", false)
    }

    private fun savePremiumStatus(isPremium: Boolean) {
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }
}