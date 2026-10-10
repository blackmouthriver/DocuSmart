package com.docsmart.features.premium.data.repository

import com.docsmart.R
import com.docsmart.core.remoteconfig.RemoteConfigManager
import com.docsmart.features.premium.domain.model.MONTHLY_PLAN_ID
import com.docsmart.features.premium.domain.model.PremiumPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepository
    @Inject
    constructor(
        private val remoteConfigManager: RemoteConfigManager,
    ) {
        private companion object {
            // "$2.99"/mes vs "$19.99"/año: 44,3 % (ver annualSavingsPercent). Hay una prueba que lo verifica.
            const val FALLBACK_SAVINGS_PERCENT = 44
        }

        // Remote Config (RF pedido por el usuario 2026-09-04): qué plan se
        // destaca como "Recomendado" y si se muestra el badge de ahorro son
        // ajustables desde la consola de Firebase sin publicar una
        // actualización -- valores por defecto en
        // res/xml/remote_config_defaults.xml igualan el comportamiento previo.
        fun getAvailablePlans(): List<PremiumPlan> {
            val annualHighlighted = remoteConfigManager.isAnnualPlanHighlighted()
            val showSavingsBadge = remoteConfigManager.showSavingsBadge()

            return listOf(
                PremiumPlan(
                    id = MONTHLY_PLAN_ID,
                    titleRes = R.string.premium_plan_monthly,
                    price = "$2.99",
                    periodRes = R.string.premium_period_month,
                    isPopular = !annualHighlighted,
                    productId = "com.docsmart.premium.monthly",
                ),
                PremiumPlan(
                    id = "annual",
                    titleRes = R.string.premium_plan_annual,
                    price = "$19.99",
                    periodRes = R.string.premium_period_year,
                    // Valor de respaldo con los precios fijos de arriba; en cuanto Play responde, el
                    // ViewModel lo recalcula con los precios reales (ver PremiumViewModel.applyOffer).
                    savingsPercent = if (showSavingsBadge) FALLBACK_SAVINGS_PERCENT else null,
                    isPopular = annualHighlighted,
                    productId = "com.docsmart.premium.annual",
                ),
            )
        }
    }
