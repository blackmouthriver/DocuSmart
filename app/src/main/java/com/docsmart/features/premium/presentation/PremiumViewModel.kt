package com.docsmart.features.premium.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val errorMessage: String? = null
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
        observePrices()
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
    // localizado que devuelve Play Billing, en cuanto esté disponible.
    private fun observePrices() {
        viewModelScope.launch {
            billingManager.formattedPrices.collect { prices ->
                if (prices.isEmpty()) return@collect
                _uiState.update { state ->
                    state.copy(plans = state.plans.map { plan ->
                        prices[plan.productId]?.takeIf { it.isNotBlank() }
                            ?.let { plan.copy(price = it) } ?: plan
                    })
                }
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
        val plan = _uiState.value.selectedPlan ?: return
        this.purchaseErrorMessage = purchaseErrorMessage
        this.pendingMessage = pendingMessage

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
            // premiumManager.isPremium.value (no _uiState.value.isPremium):
            // se lee directo del StateFlow para evitar una carrera con el
            // colector de observePremiumStatus(), que corre en otra
            // corrutina y podría no haber procesado la actualización todavía.
            val wasRestored = premiumManager.isPremium.value
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