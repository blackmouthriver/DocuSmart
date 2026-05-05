package com.docsmart.features.premium.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.premium.PremiumManager
import com.docsmart.features.premium.data.repository.PremiumRepository
import com.docsmart.features.premium.domain.model.PremiumFeature
import com.docsmart.features.premium.domain.model.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    private val premiumRepository: PremiumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        loadPlans()
        observePremiumStatus()
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

    fun selectPlan(plan: PremiumPlan) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    // ── Simular compra (en Fase 10 se conecta Play Billing real) ──
    fun purchase() {
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isPurchasing = true, errorMessage = null)
            }

            // Simula latencia de red
            delay(1500)

            val success = premiumManager.simulatePurchase(plan.id)

            _uiState.update { state ->
                if (success) {
                    state.copy(
                        isPurchasing = false,
                        purchaseSuccess = true,
                        isPremium = true
                    )
                } else {
                    state.copy(
                        isPurchasing = false,
                        errorMessage = "No se pudo completar la compra. Intenta de nuevo."
                    )
                }
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasing = true) }
            delay(1000)
            // En Fase 10 se conecta con Play Billing para restaurar
            _uiState.update { state ->
                state.copy(
                    isPurchasing = false,
                    errorMessage = "No se encontraron compras anteriores"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}