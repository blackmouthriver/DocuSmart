package com.docsmart.features.premium.data.repository

import com.docsmart.R
import com.docsmart.features.premium.domain.model.PremiumPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepository @Inject constructor() {

    fun getAvailablePlans(): List<PremiumPlan> = listOf(
        PremiumPlan(
            id = "monthly",
            titleRes = R.string.premium_plan_monthly,
            price = "$2.99",
            periodRes = R.string.premium_period_month,
            isPopular = false,
            productId = "com.docsmart.premium.monthly"
        ),
        PremiumPlan(
            id = "annual",
            titleRes = R.string.premium_plan_annual,
            price = "$19.99",
            periodRes = R.string.premium_period_year,
            savingsLabelRes = R.string.premium_savings_44,
            isPopular = true,
            productId = "com.docsmart.premium.annual"
        )
    )
}