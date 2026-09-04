package com.docsmart.features.premium.data.repository

import com.docsmart.R
import com.docsmart.core.remoteconfig.RemoteConfigManager
import com.docsmart.features.premium.domain.model.PremiumPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepository @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {

    // Remote Config (RF pedido por el usuario 2026-09-04): qué plan se
    // destaca como "Recomendado" y si se muestra el badge de ahorro son
    // ajustables desde la consola de Firebase sin publicar una
    // actualización -- valores por defecto en
    // res/xml/remote_config_defaults.xml igualan el comportamiento previo.
    fun getAvailablePlans(): List<PremiumPlan> {
        val annualHighlighted = remoteConfigManager.isAnnualPlanHighlighted()
        val showSavingsBadge  = remoteConfigManager.showSavingsBadge()

        return listOf(
            PremiumPlan(
                id = "monthly",
                titleRes = R.string.premium_plan_monthly,
                price = "$2.99",
                periodRes = R.string.premium_period_month,
                isPopular = !annualHighlighted,
                productId = "com.docsmart.premium.monthly"
            ),
            PremiumPlan(
                id = "annual",
                titleRes = R.string.premium_plan_annual,
                price = "$19.99",
                periodRes = R.string.premium_period_year,
                savingsLabelRes = if (showSavingsBadge) R.string.premium_savings_44 else null,
                isPopular = annualHighlighted,
                productId = "com.docsmart.premium.annual"
            )
        )
    }
}