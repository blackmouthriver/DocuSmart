package com.docsmart.features.premium.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.billing.BillingManager
import com.docsmart.core.billing.PurchaseResult
import com.docsmart.core.premium.PremiumManager
import com.docsmart.features.premium.data.repository.PremiumRepository
import com.docsmart.features.premium.domain.model.PremiumFeature
import com.docsmart.features.premium.domain.model.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(
    val plans: List<PremiumPlan> = emptyList(),
    val features: List<PremiumFeature> = PremiumFeature.values().toList(),
    val selectedPlan: PremiumPlan? = null,
    val isPremium: Boolean = false,
    val isPurchasing: Boolean = false,
    val purchaseSuccess: Boolean = false,
    val errorMessage: String? = null,
    // HU-54: no nulo y en el futuro solo mientras dura la prueba gratuita del
    // usuario -- únicamente para el mensaje de PremiumActiveCard.
    val trialEndsAtMillis: Long? = null,
    // Trial automático sin tarjeta: isPaidPremium distingue a un cliente
    // pagador real (incluido uno en el trial de suscripción de HU-54) de
    // alguien que solo tiene acceso porque está dentro del trial automático
    // de instalación -- la pantalla necesita esto para no ocultarle el
    // botón de suscripción a este último.
    val isPaidPremium: Boolean = false,
    val autoTrialDaysRemaining: Int? = null
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val premiumManager: PremiumManager,
    private val premiumRepository: PremiumRepository,
    private val billingManager: BillingManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    // Mensajes localizados capturados en el momento de la acción (purchase()/
    // restorePurchases()) — Play Billing responde de forma asíncrona vía
    // billingManager.purchaseResult, y para entonces ya no hay stringResource()
    // disponible directamente (el ViewModel no es @Composable).
    private var purchaseErrorMessage    = ""
    private var pendingMessage          = ""
    private var noPurchasesFoundMessage = ""
    private var restoreSuccessMessage   = ""

    init {
        loadPlans()
        observePremiumStatus()
        observeOffers()
        observeTrialEndsAt()
        observeAutoTrialState()
        observePurchaseResult()
    }

    private fun loadPlans() {
        val plans = premiumRepository.getAvailablePlans()
        _uiState.update { state ->
            state.copy(
                plans = plans,
                selectedPlan = plans.find { it.isPopular } ?: plans.first()
            )
        }
    }

    private fun observePremiumStatus() {
        viewModelScope.launch {
            premiumManager.isPremium.collect { isPremium ->
                _uiState.update { it.copy(isPremium = isPremium) }
            }
        }
    }

    // Sobrescribe el precio fijo de PremiumRepository con el precio real y
    // localizado que devuelve Play Billing (y los días de prueba gratuita si
    // el plan tiene uno configurado en Play Console), en cuanto esté
    // disponible.
    private fun observeOffers() {
        viewModelScope.launch {
            billingManager.planOffers.collect { offers ->
                if (offers.isEmpty()) return@collect
                _uiState.update { state ->
                    state.copy(plans = state.plans.map { plan ->
                        offers[plan.productId]?.let { offer ->
                            plan.copy(
                                price = offer.price.takeIf { it.isNotBlank() } ?: plan.price,
                                trialDays = offer.trialDays
                            )
                        } ?: plan
                    })
                }
            }
        }
    }

    // HU-54, AC1: mientras dure la prueba, PremiumActiveCard debe poder
    // mostrar la fecha real de cobro.
    private fun observeTrialEndsAt() {
        viewModelScope.launch {
            premiumManager.trialEndsAtMillis.collect { trialEndsAtMillis ->
                _uiState.update { it.copy(trialEndsAtMillis = trialEndsAtMillis) }
            }
        }
    }

    // Trial automático sin tarjeta: isPaidPremium/autoTrialDaysRemaining
    // determinan si esta pantalla debe seguir mostrando los planes de
    // suscripción (ver PremiumScreen) y el mensaje de días restantes.
    private fun observeAutoTrialState() {
        viewModelScope.launch {
            premiumManager.isPaidPremium.collect { isPaidPremium ->
                _uiState.update { it.copy(isPaidPremium = isPaidPremium) }
            }
        }
        viewModelScope.launch {
            premiumManager.autoTrialDaysRemaining.collect { daysRemaining ->
                _uiState.update { it.copy(autoTrialDaysRemaining = daysRemaining) }
            }
        }
    }

    private fun observePurchaseResult() {
        viewModelScope.launch {
            billingManager.purchaseResult.collect { result ->
                _uiState.update { state ->
                    when (result) {
                        is PurchaseResult.Success -> state.copy(
                            isPurchasing = false, purchaseSuccess = true, errorMessage = null
                        )
                        is PurchaseResult.Cancelled -> state.copy(isPurchasing = false)
                        is PurchaseResult.Pending -> state.copy(
                            isPurchasing = false, errorMessage = pendingMessage
                        )
                        is PurchaseResult.NoPurchasesToRestore -> state.copy(
                            isPurchasing = false, errorMessage = noPurchasesFoundMessage
                        )
                        is PurchaseResult.Error -> state.copy(
                            isPurchasing = false,
                            errorMessage = purchaseErrorMessage.ifBlank { result.debugMessage }
                        )
                    }
                }
            }
        }
    }

    fun selectPlan(plan: PremiumPlan) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun purchase(activity: Activity, purchaseErrorMessage: String, pendingMessage: String) {
        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
        // sin este guard, un doble-toque rápido en "Comprar" podía lanzar
        // dos flujos de Play Billing superpuestos antes de que la
        // recomposición ocultara el botón -- mismo patrón ya corregido en
        // Convertidor (#31) y el creador de QR (#6/#13).
        if (_uiState.value.isPurchasing) return
        val plan = _uiState.value.selectedPlan ?: return
        this.purchaseErrorMessage = purchaseErrorMessage
        this.pendingMessage = pendingMessage

        DocuSmartAnalytics.logPremiumPurchaseAttempt(plan.id)
        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }
        val launched = billingManager.launchPurchase(activity, plan.productId)
        if (!launched) {
            _uiState.update { it.copy(isPurchasing = false, errorMessage = purchaseErrorMessage) }
        }
        // Si se lanzó, isPurchasing se resuelve cuando llegue purchaseResult.
    }

    fun restorePurchases(noPurchasesFoundMessage: String, restoreSuccessMessage: String) {
        this.noPurchasesFoundMessage = noPurchasesFoundMessage
        this.restoreSuccessMessage = restoreSuccessMessage
        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }
        viewModelScope.launch {
            billingManager.restorePurchases()
            // premiumManager.isPaidPremium.value, no isPremium.value: con el
            // trial automático sin tarjeta, isPremium ya puede ser true sin
            // que se haya restaurado nada real -- usar isPremium acá le
            // mostraría "compra restaurada" a alguien que solo está en el
            // trial. Se lee directo del StateFlow (no _uiState.value) para
            // evitar una carrera con el colector de observeAutoTrialState(),
            // que corre en otra corrutina y podría no haber procesado la
            // actualización todavía.
            val wasRestored = premiumManager.isPaidPremium.value
            _uiState.update { state ->
                state.copy(
                    isPurchasing = false,
                    errorMessage = if (wasRestored) restoreSuccessMessage else noPurchasesFoundMessage,
                    purchaseSuccess = wasRestored
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}