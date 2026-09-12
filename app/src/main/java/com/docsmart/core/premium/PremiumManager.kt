package com.docsmart.core.premium

import android.content.Context
import android.content.pm.PackageManager
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

    // Trial automático sin tarjeta (pedido explícito del usuario
    // 2026-09-12): todo el que instala la app tiene AUTO_TRIAL_DAYS de
    // Premium completo gratis, sin suscripción ni tarjeta -- se mide desde
    // firstInstallTime, un dato del propio sistema operativo que "Borrar
    // datos" de la app NO reinicia (solo una desinstalación + reinstalación
    // real lo hace). Es deliberadamente más simple que a prueba de fraude:
    // el objetivo es dar un empujón de conversión, no una barrera dura.
    private val firstInstallTimeMillis: Long = readFirstInstallTimeMillis()

    // _hasPurchased refleja solo si hay o hubo una suscripción real de Play
    // Billing -- isPremium (más abajo) combina esto con el trial automático
    // para el gating real de funciones/anuncios. La distinción importa para
    // la UI: PremiumScreen necesita saber si ya es cliente pagador (oculta
    // el botón de suscripción) por separado de si solo está en el trial
    // automático (debe seguir viendo cómo suscribirse).
    private val _hasPurchased = MutableStateFlow(loadPremiumStatus())
    val isPaidPremium: StateFlow<Boolean> = _hasPurchased.asStateFlow()

    private val _isPremium = MutableStateFlow(_hasPurchased.value || isAutoTrialActive())
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Solo para mensajes de UI ("te quedan N días") -- no se vuelve a
    // recalcular durante la sesión (una app de documentos no suele quedar
    // abierta en primer plano varios días seguidos), se recalcula en cada
    // arranque de la app, que es cuando realmente importa que el número
    // esté al día.
    private val _autoTrialDaysRemaining = MutableStateFlow(
        autoTrialDaysRemaining(firstInstallTimeMillis, System.currentTimeMillis(), AUTO_TRIAL_DAYS)
    )
    val autoTrialDaysRemaining: StateFlow<Int?> = _autoTrialDaysRemaining.asStateFlow()

    // HU-54 (prueba gratuita de suscripción): solo para mensajes de UI
    // ("Premium activo, se cobrará el X") -- el gating de funciones sigue
    // siendo únicamente _isPremium, nunca esto (RNF1: un usuario en trial y
    // uno pagando se tratan exactamente igual para desbloquear funciones).
    private val _trialEndsAtMillis = MutableStateFlow(loadTrialEndsAt())
    val trialEndsAtMillis: StateFlow<Long?> = _trialEndsAtMillis.asStateFlow()

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

    fun activatePremium(trialEndsAtMillis: Long? = null) {
        _hasPurchased.value = true
        savePremiumStatus(true)
        _trialEndsAtMillis.value = trialEndsAtMillis
        saveTrialEndsAt(trialEndsAtMillis)
        _isPremium.value = true
        Timber.d(
            "PremiumManager: premium activado" +
                (trialEndsAtMillis?.let { " (en prueba gratis hasta $it)" } ?: "")
        )
    }

    fun deactivatePremium() {
        _hasPurchased.value = false
        savePremiumStatus(false)
        _trialEndsAtMillis.value = null
        saveTrialEndsAt(null)
        // Sigue siendo premium si todavía está dentro del trial automático
        // de instalación, aunque no tenga (o haya cancelado) una
        // suscripción real -- restorePurchases() llama a esto en cada
        // arranque de la app cuando no encuentra nada que restaurar, y eso
        // no debe cortarle el trial automático a un usuario recién
        // instalado que nunca intentó pagar.
        _isPremium.value = isAutoTrialActive()
        Timber.d("PremiumManager: premium desactivado")
    }

    private fun isAutoTrialActive(): Boolean =
        isWithinAutoTrial(firstInstallTimeMillis, System.currentTimeMillis(), AUTO_TRIAL_DAYS)

    private fun readFirstInstallTimeMillis(): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (e: PackageManager.NameNotFoundException) {
        // No debería pasar nunca (es el propio paquete de la app) -- sin
        // este dato no hay forma de saber si corresponde el trial
        // automático, así que se trata como si ya hubiera expirado.
        0L
    }

    private fun loadPremiumStatus(): Boolean {
        return prefs.getBoolean("is_premium", false)
    }

    private fun savePremiumStatus(isPremium: Boolean) {
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }

    private fun loadTrialEndsAt(): Long? {
        val value = prefs.getLong(KEY_TRIAL_ENDS_AT, NO_TRIAL)
        return value.takeIf { it != NO_TRIAL }
    }

    private fun saveTrialEndsAt(value: Long?) {
        prefs.edit().putLong(KEY_TRIAL_ENDS_AT, value ?: NO_TRIAL).apply()
    }

    private companion object {
        const val KEY_TRIAL_ENDS_AT = "trial_ends_at_millis"
        const val NO_TRIAL = -1L
        const val AUTO_TRIAL_DAYS = 3
    }
}

internal const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

internal fun isWithinAutoTrial(
    firstInstallTimeMillis: Long,
    nowMillis: Long,
    trialDurationDays: Int
): Boolean = nowMillis < firstInstallTimeMillis + trialDurationDays * MILLIS_PER_DAY

internal fun autoTrialDaysRemaining(
    firstInstallTimeMillis: Long,
    nowMillis: Long,
    trialDurationDays: Int
): Int? {
    val elapsedDays = ((nowMillis - firstInstallTimeMillis) / MILLIS_PER_DAY).toInt()
    return (trialDurationDays - elapsedDays).takeIf { it > 0 }
}
