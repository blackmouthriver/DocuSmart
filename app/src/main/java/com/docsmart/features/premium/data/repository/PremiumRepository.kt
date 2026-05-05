package com.docsmart.features.premium.data.repository

import com.docsmart.features.premium.domain.model.PremiumPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepository @Inject constructor() {

    fun getAvailablePlans(): List<PremiumPlan> = listOf(
        PremiumPlan(
            id = "monthly",
            title = "Mensual",
            price = "$2.99",
            period = "por mes",
            isPopular = false,
            productId = "com.docsmart.premium.monthly"
        ),
        PremiumPlan(
            id = "annual",
            title = "Anual",
            price = "$19.99",
            period = "por año",
            savingsLabel = "Ahorra 44%",
            isPopular = true,
            productId = "com.docsmart.premium.annual"
        ),
        PremiumPlan(
            id = "lifetime",
            title = "De por vida",
            price = "$49.99",
            period = "pago único",
            savingsLabel = "Mejor valor",
            isPopular = false,
            productId = "com.docsmart.premium.lifetime"
        )
    )
}